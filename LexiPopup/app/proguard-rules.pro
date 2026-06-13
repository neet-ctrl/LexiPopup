-keep class com.lexipopup.data.local.entities.** { *; }
-keep class com.lexipopup.domain.models.** { *; }
-keep class com.lexipopup.data.remote.dto.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── Gson TypeToken — MUST keep or R8 strips generic signatures and crashes
# with "TypeToken must be created with a type argument" at runtime.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class com.google.gson.internal.$Gson$Types { *; }

# ── Annotation-processor shaded classes (compile-time only, not present at runtime) ──
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**
-dontwarn javax.annotation.processing.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
-dontwarn com.squareup.javapoet.**

# ── llama.android (JNI wrapper around llama.cpp for GGUF inference) ──────────
# The native methods and all JNI-bridging classes must not be renamed or removed.
-keep class io.shubham0204.llama_android.** { *; }
-keep interface io.shubham0204.llama_android.** { *; }
-keepclasseswithmembers class io.shubham0204.llama_android.** {
    native <methods>;
}

# ── Hilt / Dagger generated code references that R8 may not resolve in all configs
-dontwarn dagger.internal.**
-dontwarn dagger.hilt.internal.**
