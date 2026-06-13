cmake_minimum_required(VERSION 3.22)
project(lexipopup_llama CXX)

# ── Disable every feature we don't need so llama.cpp compiles fast ─────────
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# GPU backends — none available on typical Android devices
set(GGML_METAL    OFF CACHE BOOL "" FORCE)
set(GGML_CUDA     OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN   OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL   OFF CACHE BOOL "" FORCE)
set(GGML_BLAS     OFF CACHE BOOL "" FORCE)
set(GGML_SYCL     OFF CACHE BOOL "" FORCE)

# Don't auto-detect host CPU features — cross-compiling for ARM Android
set(GGML_NATIVE   OFF CACHE BOOL "" FORCE)

# ── Fetch llama.cpp source at build time ────────────────────────────────────
# Using GIT_REPOSITORY (not URL) — CMake 3.22 has a bug where FetchContent
# with a ZIP URL fails in Android NDK cross-compilation mode with
# "At least one entry of URL is a path (invalid in a list)".
# GIT_REPOSITORY avoids that code path entirely and works reliably.
# Pinned to b9620 (2026-06-13, ggml-org org) for a stable, verified API.
include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b9620
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama_cpp)

# ── Our JNI bridge shared library ───────────────────────────────────────────
add_library(llama_jni SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}
)

target_compile_features(llama_jni PRIVATE cxx_std_17)

target_link_libraries(llama_jni
    llama
    android
    log
)
