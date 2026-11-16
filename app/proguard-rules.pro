# Turbo Rocket Mode ProGuard Rules
# MediaPipe GenAI & Task Library
-keep class com.google.mediapipe.tasks.** { * }
-dontwarn com.google.mediapipe.tasks.**

# Jetpack Compose & UI
-keep class androidx.compose.** { * }
-dontwarn androidx.compose.**

# Kotlinx Serialization & Data Classes
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class kotlinx.serialization.** { * }
-dontwarn kotlinx.serialization.**

# Retrofit & OkHttp
-keep class retrofit2.** { * }
-keep class okhttp3.** { * }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Room Database
-keep class androidx.room.** { * }
-dontwarn androidx.room.**

# JGit
-keep class org.eclipse.jgit.** { * }
-dontwarn org.eclipse.jgit.**

# Preserve SourceFile and LineNumberTable for crash reporting if needed
-keepattributes SourceFile,LineNumberTable
