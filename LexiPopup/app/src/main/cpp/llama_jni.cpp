#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LexiLlama"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Persistent model (loaded once, stays in RAM) ─────────────────────────────
// Only the MODEL is kept across calls — loading 1.7 GB from disk is the slow
// part (30-60 s). The context is lightweight and recreated per inference call
// to avoid KV cache state issues without needing llama_kv_cache_clear.

static llama_model *g_model      = nullptr;
static std::string  g_model_path;

static llama_context *makeContext(llama_model *model) {
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx   = 2048;
    cparams.n_batch = 512;
    // llama_init_from_model is the current preferred API (b9620+).
    // llama_new_context_with_model is deprecated with the same hint.
    return llama_init_from_model(model, cparams);
}

extern "C" {

// ── Load model into RAM (call once; no-op if already loaded) ─────────────────
JNIEXPORT jboolean JNICALL
Java_com_lexipopup_utils_ai_LlamaJni_loadModelNative(
        JNIEnv *env, jclass, jstring jPath) {

    const char *path_c = env->GetStringUTFChars(jPath, nullptr);
    std::string path(path_c);
    env->ReleaseStringUTFChars(jPath, path_c);

    if (g_model && g_model_path == path) {
        LOGD("loadModelNative: already loaded — skipping");
        return JNI_TRUE;
    }

    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_model_path.clear();

    LOGD("loadModelNative: loading %s", path.c_str());
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) {
        LOGE("loadModelNative: failed");
        return JNI_FALSE;
    }
    g_model_path = path;
    LOGD("loadModelNative: ready");
    return JNI_TRUE;
}

// ── Unload (only when user deletes the model file) ───────────────────────────
JNIEXPORT void JNICALL
Java_com_lexipopup_utils_ai_LlamaJni_unloadModelNative(JNIEnv *, jclass) {
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_model_path.clear();
    LOGD("unloadModelNative: freed");
}

// ── Inference ─────────────────────────────────────────────────────────────────
// Model is loaded once and stays in RAM. A fresh context is created per call
// (fast, ~1 s) which avoids KV cache state issues cleanly.
JNIEXPORT jstring JNICALL
Java_com_lexipopup_utils_ai_LlamaJni_runInferenceNative(
        JNIEnv  *env,
        jclass  /* clazz */,
        jstring  jModelPath,
        jstring  jPrompt,
        jint     nPredict) {

    // ── Ensure model is loaded ───────────────────────────────────────────────
    const char *model_path_c = env->GetStringUTFChars(jModelPath, nullptr);
    std::string model_path(model_path_c);
    env->ReleaseStringUTFChars(jModelPath, model_path_c);

    if (!g_model || g_model_path != model_path) {
        LOGD("runInferenceNative: loading model");
        if (g_model) { llama_model_free(g_model); g_model = nullptr; }
        g_model_path.clear();

        llama_model_params mparams = llama_model_default_params();
        mparams.n_gpu_layers = 0;
        g_model = llama_model_load_from_file(model_path.c_str(), mparams);
        if (!g_model) {
            return env->NewStringUTF("ERROR: Could not load model file — check the .gguf path");
        }
        g_model_path = model_path;
    }

    // ── Create a fresh context for this inference call ───────────────────────
    // Context creation is fast (~1 s). Recreating it each call avoids any
    // KV cache state from a previous call contaminating the next response.
    llama_context *ctx = makeContext(g_model);
    if (!ctx) {
        return env->NewStringUTF("ERROR: Could not create inference context");
    }

    // ── Extract prompt ───────────────────────────────────────────────────────
    const char *prompt_c = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(prompt_c);
    env->ReleaseStringUTFChars(jPrompt, prompt_c);

    // ── Get vocab (b9620 API: all tokenise calls take vocab* not model*) ─────
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    if (!vocab) {
        llama_free(ctx);
        return env->NewStringUTF("ERROR: Could not retrieve model vocab");
    }

    // ── Tokenise prompt ──────────────────────────────────────────────────────
    const int max_tok = static_cast<int>(prompt.size()) + 64;
    std::vector<llama_token> tokens(max_tok);
    int n_tokens = llama_tokenize(
            vocab,
            prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), max_tok,
            true, false);

    if (n_tokens < 0) {
        llama_free(ctx);
        return env->NewStringUTF("ERROR: Prompt too long for context window");
    }
    tokens.resize(n_tokens);
    LOGD("Prompt: %d tokens", n_tokens);

    // ── Evaluate prompt batch ────────────────────────────────────────────────
    {
        llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
        if (llama_decode(ctx, batch) != 0) {
            llama_free(ctx);
            return env->NewStringUTF("ERROR: Prompt evaluation failed");
        }
    }

    // ── Greedy generation ────────────────────────────────────────────────────
    const int n_vocab = llama_vocab_n_tokens(vocab);
    std::string result;
    result.reserve(1024);

    for (int step = 0; step < nPredict; ++step) {
        const float *logits = llama_get_logits_ith(ctx, -1);

        llama_token best = 0;
        float best_logit = logits[0];
        for (int t = 1; t < n_vocab; ++t) {
            if (logits[t] > best_logit) { best_logit = logits[t]; best = t; }
        }

        if (llama_vocab_is_eog(vocab, best)) { LOGD("EOS at step %d", step); break; }

        char piece[256] = {};
        int  piece_len  = llama_token_to_piece(vocab, best, piece,
                                               sizeof(piece) - 1, 0, false);
        if (piece_len > 0) result.append(piece, piece_len);

        llama_batch next = llama_batch_get_one(&best, 1);
        if (llama_decode(ctx, next) != 0) { LOGE("decode failed at step %d", step); break; }
    }

    LOGD("Generated %zu chars", result.size());
    llama_free(ctx);
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
