#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LexiLlama"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_lexipopup_utils_ai_LlamaJni_runInferenceNative(
        JNIEnv  *env,
        jclass  /* clazz */,
        jstring  jModelPath,
        jstring  jPrompt,
        jint     nPredict) {

    // ── Extract Java strings ────────────────────────────────────────────────
    const char *model_path = env->GetStringUTFChars(jModelPath, nullptr);
    const char *prompt_raw = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(prompt_raw);
    env->ReleaseStringUTFChars(jPrompt, prompt_raw);

    LOGD("Loading model: %s", model_path);

    // ── Load model (CPU-only, no GPU layers) ────────────────────────────────
    // llama_model_load_from_file is the current preferred API (b9620+).
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    llama_model *model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(jModelPath, model_path);

    if (!model) {
        LOGE("llama_model_load_from_file failed");
        return env->NewStringUTF("ERROR: Could not load model file — check that the .gguf path is correct");
    }

    // ── Get the vocab (needed for tokenisation in b9620+ API) ───────────────
    // All tokenise/detokenise/eog calls take vocab* not model* since ~b8000.
    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (!vocab) {
        llama_model_free(model);
        return env->NewStringUTF("ERROR: Could not retrieve model vocab");
    }

    // ── Create inference context ────────────────────────────────────────────
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx   = 2048;
    cparams.n_batch = 512;

    llama_context *ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        llama_model_free(model);
        return env->NewStringUTF("ERROR: Could not create inference context");
    }

    // ── Tokenise prompt ─────────────────────────────────────────────────────
    const int max_tok = static_cast<int>(prompt.size()) + 64;
    std::vector<llama_token> tokens(max_tok);

    // b9620 API: llama_tokenize(vocab, text, text_len, tokens, n_max, add_special, parse_special)
    int n_tokens = llama_tokenize(
            vocab,
            prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), max_tok,
            /*add_special=*/true,
            /*parse_special=*/false);

    if (n_tokens < 0) {
        LOGE("llama_tokenize failed: need %d slots", -n_tokens);
        llama_free(ctx);
        llama_model_free(model);
        return env->NewStringUTF("ERROR: Prompt too long for context window");
    }
    tokens.resize(n_tokens);
    LOGD("Prompt has %d tokens", n_tokens);

    // ── Evaluate the prompt batch ───────────────────────────────────────────
    {
        llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode (prompt) failed");
            llama_free(ctx);
            llama_model_free(model);
            return env->NewStringUTF("ERROR: Prompt evaluation failed");
        }
    }

    // ── Auto-regressive generation (greedy decoding) ────────────────────────
    // Greedy sampling from raw logits — avoids sampler API version churn entirely.
    // b9620 API: llama_vocab_n_tokens(vocab) replaces llama_n_vocab(model).
    const int n_vocab = llama_vocab_n_tokens(vocab);
    std::string result;
    result.reserve(1024);

    for (int step = 0; step < nPredict; ++step) {
        const float *logits = llama_get_logits_ith(ctx, -1);

        // Greedy: pick the token with the highest logit
        llama_token best       = 0;
        float       best_logit = logits[0];
        for (int t = 1; t < n_vocab; ++t) {
            if (logits[t] > best_logit) {
                best_logit = logits[t];
                best       = t;
            }
        }

        // b9620 API: llama_vocab_is_eog(vocab, token) replaces llama_token_is_eog(model, token)
        if (llama_vocab_is_eog(vocab, best)) {
            LOGD("EOS token at step %d", step);
            break;
        }

        // b9620 API: llama_token_to_piece(vocab, token, buf, len, lstrip, special)
        // vocab* replaces model* as first argument.
        char piece[256] = {};
        int  piece_len  = llama_token_to_piece(
                vocab, best,
                piece, static_cast<int32_t>(sizeof(piece) - 1),
                /*lstrip=*/0, /*special=*/false);
        if (piece_len > 0) {
            result.append(piece, piece_len);
        }

        // Feed the generated token back into the context
        llama_batch next = llama_batch_get_one(&best, 1);
        if (llama_decode(ctx, next) != 0) {
            LOGE("llama_decode (generation) failed at step %d", step);
            break;
        }
    }

    LOGD("Generated %zu chars", result.size());

    // ── Cleanup ─────────────────────────────────────────────────────────────
    llama_free(ctx);
    llama_model_free(model);

    return env->NewStringUTF(result.c_str());
}

} // extern "C"
