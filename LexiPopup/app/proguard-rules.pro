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

# ── Annotation-processor shaded classes (compile-time only, not present at runtime) ──
# javax.lang.model is part of the Java compiler API used by AutoValue, Hilt, and KSP
# processors. These classes are never shipped on Android; suppress R8 warnings.
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**
-dontwarn javax.annotation.processing.**

# AutoValue shaded Javapoet / Guava used by com.google.mediapipe:tasks-genai and
# similar libraries that bundle their annotation-processor internals.
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
-dontwarn com.squareup.javapoet.**

# MediaPipe / TensorFlow Lite internal optional integrations
-dontwarn com.google.mediapipe.**
-dontwarn org.tensorflow.**

# Hilt / Dagger generated code references that R8 may not resolve in all configs
-dontwarn dagger.internal.**
-dontwarn dagger.hilt.internal.**
