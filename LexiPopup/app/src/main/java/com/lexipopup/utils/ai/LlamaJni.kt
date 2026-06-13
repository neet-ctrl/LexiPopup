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
     * Load the GGUF model into native memory and keep it there.
     * Call once when the model file becomes available. Subsequent calls with
     * the same path are no-ops (the model is already in memory).
     *
     * @return true on success, false if the file could not be loaded.
     */
    @JvmStatic
    external fun loadModelNative(modelPath: String): Boolean

    /**
     * Free the native model and context from memory.
     * Call when the user deletes the model file.
     */
    @JvmStatic
    external fun unloadModelNative()

    /**
     * Run greedy-decoded text generation. The model is loaded from [modelPath]
     * on the first call and kept in memory — subsequent calls skip the load step
     * and only reset the KV cache, making them significantly faster.
     *
     * @param modelPath  Absolute path to the .gguf file on device.
     * @param prompt     Full prompt string (instruct format applied by caller).
     * @param nPredict   Maximum tokens to generate.
     * @return Generated text, or a string starting with "ERROR:" on failure.
     */
    @JvmStatic
    external fun runInferenceNative(modelPath: String, prompt: String, nPredict: Int): String
}
