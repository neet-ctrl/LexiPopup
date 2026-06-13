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

# MediaPipe LLM Inference — MUST keep all classes and members.
# LlmInferenceOptions and its proto-generated subclasses access fields like
# "modelPath_" by name via reflection at runtime.  R8 renames those fields
# during obfuscation, causing:
#   RuntimeException: Field modelPath_ for <obfuscated> not found
# -dontwarn alone suppresses warnings but does NOT prevent renaming.
# The three rules below together: keep class names, keep every member name,
# and keep the names of any nested/generated classes inside the package.
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }

# MediaPipe classes not bundled in tasks-genai AAR — referenced but not shipped:
#
#  com.google.mediapipe.proto.**          — native-only proto classes (GraphProfiler etc.)
#  com.google.mediapipe.framework.image.** — vision/MPImage classes added in 0.10.22;
#                                            only needed if app calls addImage(); we don't.
#
# All are safe to ignore — our code never calls the methods that reference them.
-dontwarn com.google.mediapipe.proto.**
-dontwarn com.google.mediapipe.framework.image.**

# Protobuf annotation classes used by tasks-genai 0.10.22 generated proto code.
# These annotation types (ProtoField, ProtoPresenceBits, etc.) are compile-time
# markers only; they are NOT present in the lite protobuf runtime shipped on Android.
# R8's -keep class com.google.mediapipe.** forces it to resolve their references,
# which fails without this dontwarn.
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField

# TensorFlow Lite (pulled in transitively by tasks-genai)
-keep class org.tensorflow.** { *; }
-keepclassmembers class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# Hilt / Dagger generated code references that R8 may not resolve in all configs
-dontwarn dagger.internal.**
-dontwarn dagger.hilt.internal.**
