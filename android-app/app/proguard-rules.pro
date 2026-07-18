# SmartCam Pro ProGuard Rules

# Keep app classes
-keep class com.smartcampro.app.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# CameraX
-keep class androidx.camera.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep JSON classes
-keepclassmembers class * {
    @org.json.* <fields>;
}
-keep class org.json.** { *; }

# Material Design
-keep class com.google.android.material.** { *; }
