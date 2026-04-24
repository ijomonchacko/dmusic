# Keep rules for flutter_secure_storage and Flutter plugins
-keep class com.it_nomads.fluttersecurestorage.** { *; }
-keep class io.flutter.plugins.** { *; }

# Keep path_provider and cached_network_image classes
-keep class io.flutter.plugins.pathprovider.** { *; }
-keep class com.ryanheise.** { *; }
-keep class flutter.plugins.** { *; }
-dontwarn com.github.bumptech.glide.**
-keep class com.github.bumptech.glide.** { *; }

# Keep Flutter classes
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# Keep OkHttp classes for streaming
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcher {}

# Keep MediaSession classes for notifications
-keep class androidx.media.** { *; }

# General rules
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Flutter Play Core keep rules
-keep class com.google.android.play.** { *; }
-keep class com.google.android.play.core.** { *; }
-keep class com.google.android.play.core.splitcompat.** { *; }
-keep class com.google.android.play.core.splitinstall.** { *; }
-keep class com.google.android.play.core.tasks.** { *; }

# ===== BASS AUDIO ENGINE PROTECTION =====

# Keep ALL native methods - CRITICAL for production
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep BassMainActivity and ALL its methods (native audio bridge)
-keep class com.dmusic.android.dmusic.BassMainActivity {
    *;
}

# Keep BassAudioManager and its methods
-keep class com.dmusic.android.dmusic.BassAudioManager {
    *;
}

# Keep all JNI related classes and methods
-keep class * {
    public static native <methods>;
    private static native <methods>;
    protected static native <methods>;
}

# BASS-only specific native methods - DO NOT OBFUSCATE
-keep class com.dmusic.android.dmusic.MainActivity {
    # Engine lifecycle
    public native boolean createHiResEngine();
    public native void destroyHiResEngine();
    public native boolean isEngineReady();
    public native boolean isHiResEngineReady();
    
    # Playback control
    public native boolean playHiResUrl(java.lang.String);
    public native boolean startHiResPlayback();
    public native void stopHiResPlayback();
    public native boolean pauseHiResPlayback();
    public native boolean resumeHiResPlayback();
    public native boolean isHiResPlaying();
    
    # Audio properties
    public native void setHiResVolume(float);
    public native float getHiResVolume();
    public native int getHiResSampleRate();
    public native int getHiResChannelCount();
    public native java.lang.String getHiResAudioApiName();
    public native java.lang.String getHiResDeviceInfo();
    public native java.lang.String getHiResEngineStatus();
    
    # Speaker protection
    public native void enableSpeakerProtection(boolean);
    public native boolean isSpeakerProtectionActive();
    public native void setMaxSafeVolume(float);
    public native java.lang.String getHiResSafetyStatus();
    public native void logHiResState();
}

# Audio and media related
-keep class android.media.** { *; }
-keep class android.content.res.AssetFileDescriptor { *; }

# BASS library - CRITICAL (native libraries handled by CMake)

# BASS library related (if any Java wrappers exist)
-keep class com.un4seen.bass.** { *; }
-dontwarn com.un4seen.bass.**

# Keep native library loading
-keep class java.lang.System {
    public static void loadLibrary(java.lang.String);
    public static void load(java.lang.String);
}

# Don't obfuscate native libraries and their symbols
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ===== OPTIMIZATION SETTINGS FOR AUDIO =====

# Conservative optimization for audio apps
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!method/inlining/*
-optimizationpasses 3
-allowaccessmodification
-dontpreverify

# Audio codec related warnings
-dontwarn android.media.**
-dontwarn androidx.media.**

# Native library warnings
-dontwarn dalvik.system.VMRuntime
-dontwarn java.lang.invoke.**

# ===== PRODUCTION LOGGING =====

# Remove debug logging but keep error/warning logs for production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep important crash reporting info
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# ===== METHOD CHANNEL PROTECTION =====

# Keep Flutter method channel related classes
-keep class io.flutter.plugin.common.** { *; }
-keep class io.flutter.embedding.engine.** { *; }

# Protect against method channel name obfuscation
-keepclassmembers class * {
    @io.flutter.plugin.common.MethodChannel.** *;
}

# ===== PRODUCTION STABILITY =====

# Keep enum classes intact (used in audio state management)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations (for service communication)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable classes
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# FFmpeg Kit Flutter protection
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-keep class com.arthenica.mobileffmpeg.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-dontwarn com.arthenica.smartexception.**
-dontwarn com.arthenica.mobileffmpeg.**

# ===== ADDITIONAL RELEASE BUILD PROTECTION =====

# Prevent R8 from removing any classes that might be used by native code
-keep class com.dmusic.android.dmusic.** { *; }

# Keep classes with static initializers (for native library loading)
-keepclasseswithmembers class * {
    static {
        <methods>;
    }
}

# Keep all method channel handlers
-keep class * extends io.flutter.embedding.android.FlutterActivity { *; }

# Keep all service classes
-keep class * extends android.app.Service { *; }

# Prevent optimization of audio-related classes
-keep class android.media.** { *; }
-keep class androidx.media.** { *; }

# Keep all exception classes for proper error handling
-keep class * extends java.lang.Exception { *; }
-keep class * extends java.lang.Error { *; }

# Keep reflection-based code
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Conservative optimization settings for production
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 2
-allowaccessmodification
-dontobfuscate
