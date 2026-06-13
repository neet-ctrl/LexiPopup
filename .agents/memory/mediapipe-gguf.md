---
name: MediaPipe GGUF incompatibility
description: MediaPipe tasks-genai cannot load standard HuggingFace GGUF files; solution is llama.cpp built via CMake/NDK.
---

## Rule
Never use `com.google.mediapipe:tasks-genai` for GGUF model inference. It only works with Google's proprietary `.task` format downloaded from Kaggle with authentication. All HuggingFace GGUF files (any architecture: Phi-2, Gemma, Llama, etc.) fail at runtime with `Error building tflite model` in `config_utils.cc:540`.

## Why
The MediaPipe LLM engine is a TFLite-based runtime, not a GGUF-native runtime. Its "GGUF support" only works with files converted through Google's own `ai_edge_torch` pipeline, not the standard GGUF format produced by llama.cpp and published on HuggingFace.

## Solution implemented
Replaced MediaPipe with llama.cpp built directly via CMake + Android NDK:

- `app/CMakeLists.txt` — FetchContent downloads llama.cpp master from GitHub; builds `llama` static lib and `llama_jni` shared lib
- `app/src/main/cpp/llama_jni.cpp` — JNI bridge: loads GGUF, tokenizes, runs greedy inference loop using raw logits (avoids sampler API version churn)
- `app/src/main/java/com/lexipopup/utils/ai/LlamaJni.kt` — Kotlin `object` with `System.loadLibrary("llama_jni")` and `external fun runInferenceNative(...)`
- `OnDeviceAiProvider.generateText()` — calls `LlamaJni.runInferenceNative(modelPath, prompt, maxTokens)`

## How to apply
Any time on-device AI is needed for GGUF models on Android: use llama.cpp via CMake. The build takes 10-20 minutes the first time (C++ compilation) but Gradle caches subsequent builds.

**Potential compile errors to watch for (easy fixes):**
- `llama_load_model_from_file` renamed to `llama_model_load_from_file` in very new versions — update the call in `llama_jni.cpp`
- `llama_free_model` renamed to `llama_model_free` — same fix
- `llama_n_vocab(model)` signature may need `llama_model_get_vocab(model)` then `llama_vocab_n_tokens(vocab)` in latest versions
- `llama_token_to_piece(model, ...)` 6-arg signature may differ — check llama.h

## ProGuard
No ProGuard rules needed for the native .so. The JNI bridge is loaded via `System.loadLibrary` and the native symbols are preserved because they are exported C functions (not Java reflection).
