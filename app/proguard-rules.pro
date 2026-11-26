# Caption Studio — Production R8 / ProGuard Keep Rules

# Preserve stack trace line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx Serialization & Gson (Navigation routes, domain models, style deep links)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserve all Stage 2 & Stage 3 Domain, Data, and Preset Models + Enums under R8 Minification
-keep class com.example.captionstudio.domain.model.** { *; }
-keep class com.example.captionstudio.data.** { *; }
-keep class com.example.captionstudio.presets.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}

# Room Database Entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.captionstudio.data.local.** { *; }

# Vosk Offline ASR & JNA Native Bindings (CRITICAL — prevents UnsatisfiedLinkError in R8)
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**

# Whisper.cpp Android Offline ASR JNI Bindings (CRITICAL — prevents UnsatisfiedLinkError in R8)
-keep class dev.ffmpegkit.whisper.** { *; }
-dontwarn dev.ffmpegkit.whisper.**

# ML Kit Selfie Segmentation & On-Device Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# Media3 ExoPlayer & Transformer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Kotlin Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
