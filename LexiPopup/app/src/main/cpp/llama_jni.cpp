#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LexiLlama"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Persistent model state ────────────────────────────────────────────────────
// Model and context are loaded ONCE and kept in memory between inference calls.
// This eliminates the ~30-60s reload cost on every message.
// unloadModelNative() frees them (called when the user deletes the model).

static llama_model   *g_model      = nullptr;
static llama_context *g_ctx        = nullptr;
static std::string    g_model_path;

extern "C" {

// ── Load (called once when model becomes ready) ───────────────────────────────
JNIEXPORT jboolean JNICALL
Java_com_lexipopup_utils_ai_LlamaJni_loadModelNative(
        JNIEnv *env, jclass, jstring jPath) {

    const char *path_c = env->GetStringUTFChars(jPath, nullptr);
    std::string path(path_c);
    env->ReleaseStringUTFChars(jPath, path_c);

    // Already loaded — nothing to do
    if (g_model && g_model_path == path) {
        LOGD("loadModelNative: already loaded — skipping");
        return JNI_TRUE;
    }

    // Unload any previously loaded model first
    if (g_ctx)   { llama_free(g_ctx);        g_ctx   = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_model_path.clear();

    LOGD("loadModelNative: loading %s", path.c_str());

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) {
        LOGE("loadModelNative: llama_model_load_from_file failed");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx   = 2048;
    cparams.n_batch = 512;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("loadModelNative: llama_new_context_with_model failed");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_model_path = path;
    LOGD("loadModelNative: ready");
    return JNI_TRUE;
}

// ── Unload (called when user deletes the model file) ─────────────────────────
JNIEXPORT void JNICALL
Java_com_lexipopup_utils_ai_LlamaJni_unloadModelNative(JNIEnv *, jclass) {
    if (g_ctx)   { llama_free(g_ctx);        g_ctx   = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_model_path.clear();
    LOGD("unloadModelNative: freed");
}

// ── Inference (model must already be loaded via loadModelNative) ──────────────
JNIEXPORT jstring JNICALL
Java_com_lexipopup_utils_ai_LlamaJni_runInferenceNative(
        JNIEnv  *env,
        jclass  /* clazz */,
        jstring  jModelPath,
        jstring  jPrompt,
        jint     nPredict) {

    // ── Ensure model is loaded (load on first call, skip on subsequent) ──────
    const char *model_path_c = env->GetStringUTFChars(jModelPath, nullptr);
    std::string model_path(model_path_c);
    env->ReleaseStringUTFChars(jModelPath, model_path_c);

    if (!g_model || g_model_path != model_path) {
        // Model not yet loaded or path changed — load now
        LOGD("runInferenceNative: loading model on first call");
        if (g_ctx)   { llama_free(g_ctx);        g_ctx   = nullptr; }
        if (g_model) { llama_model_free(g_model); g_model = nullptr; }
        g_model_path.clear();

        llama_model_params mparams = llama_model_default_params();
        mparams.n_gpu_layers = 0;

        g_model = llama_model_load_from_file(model_path.c_str(), mparams);
        if (!g_model) {
            LOGE("runInferenceNative: failed to load model");
            return env->NewStringUTF("ERROR: Could not load model file — check that the .gguf path is correct");
        }

        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx   = 2048;
        cparams.n_batch = 512;

        g_ctx = llama_new_context_with_model(g_model, cparams);
        if (!g_ctx) {
            llama_model_free(g_model);
            g_model = nullptr;
            return env->NewStringUTF("ERROR: Could not create inference context");
        }
        g_model_path = model_path;
    }

    // ── Clear KV cache so each call starts with a clean slate ────────────────
    // This is instant (just zeros out metadata) — no model reload needed.
    llama_kv_cache_clear(g_ctx);

    // ── Extract prompt string ─────────────────────────────────────────────────
    const char *prompt_c = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(prompt_c);
    env->ReleaseStringUTFChars(jPrompt, prompt_c);

    // ── Get vocab pointer (b9620 API: vocab* required for tokenise/detokenise) ─
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    if (!vocab) {
        return env->NewStringUTF("ERROR: Could not retrieve model vocab");
    }

    // ── Tokenise prompt ──────────────────────────────────────────────────────
    const int max_tok = static_cast<int>(prompt.size()) + 64;
    std::vector<llama_token> tokens(max_tok);

    int n_tokens = llama_tokenize(
            vocab,
            prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), max_tok,
            /*add_special=*/true,
            /*parse_special=*/false);

    if (n_tokens < 0) {
        LOGE("runInferenceNative: tokenize failed (need %d tokens)", -n_tokens);
        return env->NewStringUTF("ERROR: Prompt too long for context window");
    }
    tokens.resize(n_tokens);
    LOGD("Prompt has %d tokens", n_tokens);

    // ── Evaluate prompt batch ────────────────────────────────────────────────
    {
        llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("runInferenceNative: prompt decode failed");
            return env->NewStringUTF("ERROR: Prompt evaluation failed");
        }
    }

    // ── Greedy auto-regressive generation ───────────────────────────────────
    const int n_vocab = llama_vocab_n_tokens(vocab);
    std::string result;
    result.reserve(1024);

    for (int step = 0; step < nPredict; ++step) {
        const float *logits = llama_get_logits_ith(g_ctx, -1);

        llama_token best       = 0;
        float       best_logit = logits[0];
        for (int t = 1; t < n_vocab; ++t) {
            if (logits[t] > best_logit) { best_logit = logits[t]; best = t; }
        }

        if (llama_vocab_is_eog(vocab, best)) {
            LOGD("EOS at step %d", step);
            break;
        }

        char piece[256] = {};
        int  piece_len  = llama_token_to_piece(
                vocab, best,
                piece, static_cast<int32_t>(sizeof(piece) - 1),
                /*lstrip=*/0, /*special=*/false);
        if (piece_len > 0) result.append(piece, piece_len);

        llama_batch next = llama_batch_get_one(&best, 1);
        if (llama_decode(g_ctx, next) != 0) {
            LOGE("runInferenceNative: generation decode failed at step %d", step);
            break;
        }
    }

    LOGD("Generated %zu chars", result.size());
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
