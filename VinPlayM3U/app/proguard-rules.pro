# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# Media3 / ExoPlayer
-dontwarn com.google.common.**
-keep class androidx.media3.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Hilt keeps handled by plugin; nothing extra needed.
