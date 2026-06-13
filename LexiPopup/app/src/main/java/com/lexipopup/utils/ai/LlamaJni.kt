package com.lexipopup.utils.ai

/**
 * Kotlin interface to the native llama.cpp JNI bridge (libllama_jni.so).
 *
 * The native library is compiled from llama.cpp source via CMakeLists.txt.
 * It supports any standard GGUF model file (Phi-2, Gemma, etc.) downloaded
 * from HuggingFace — no proprietary format conversion required.
 */
object LlamaJni {

    init {
        System.loadLibrary("llama_jni")
    }

    /**
     * Run greedy-decoded text generation on the given GGUF model.
     *
     * @param modelPath  Absolute path to the .gguf file on device.
     * @param prompt     The full prompt string (instruct format is handled by the caller).
     * @param nPredict   Maximum number of tokens to generate.
     * @return Generated text, or a string starting with "ERROR:" on failure.
     */
    @JvmStatic
    external fun runInferenceNative(modelPath: String, prompt: String, nPredict: Int): String
}
