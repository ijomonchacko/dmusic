#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>
#include "BassAudioEngine.h"

#define LOG_TAG "NativeAudioBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global instance of the audio engine
static std::unique_ptr<BassAudioEngine> g_nativeAudioEngine = nullptr;

// Helper function to get string from jstring
std::string jstring_to_string_native(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) {
        return "";
    }
    
    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    if (cstr == nullptr) {
        return "";
    }
    
    std::string str(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return str;
}

extern "C" {

// Native method implementations for bass_audio_engine channel
JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeInitialize(JNIEnv *env, jobject thiz) {
    LOGI("Creating and initializing native BASS audio engine");
    
    try {
        g_nativeAudioEngine = std::make_unique<BassAudioEngine>();
        if (g_nativeAudioEngine->initialize(44100, 2)) {
            LOGI("✅ Native audio engine created and initialized");
            return JNI_TRUE;
        } else {
            LOGE("❌ Failed to initialize native engine");
            g_nativeAudioEngine.reset();
            return JNI_FALSE;
        }
    } catch (const std::exception& e) {
        LOGE("❌ Failed to create native audio engine: %s", e.what());
        g_nativeAudioEngine.reset();
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeLoadUrl(JNIEnv *env, jobject thiz, jstring url) {
    if (!g_nativeAudioEngine) {
        LOGE("❌ Native engine not initialized");
        return JNI_FALSE;
    }
    
    std::string urlStr = jstring_to_string_native(env, url);
    LOGI("🌐 Loading URL: %s", urlStr.c_str());
    
    bool result = g_nativeAudioEngine->loadUrl(urlStr);
    LOGI("🌐 Load URL result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativePlay(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        LOGE("❌ Native engine not initialized");
        return JNI_FALSE;
    }
    
    LOGI("▶️ Starting playback");
    bool result = g_nativeAudioEngine->play();
    LOGI("▶️ Play result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativePause(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        LOGE("❌ Native engine not initialized");
        return JNI_FALSE;
    }
    
    LOGI("⏸️ Pausing playback");
    bool result = g_nativeAudioEngine->pause();
    LOGI("⏸️ Pause result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeStop(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        LOGE("❌ Native engine not initialized");
        return JNI_FALSE;
    }
    
    LOGI("⏹️ Stopping playback");
    bool result = g_nativeAudioEngine->stop();
    LOGI("⏹️ Stop result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeSetVolume(JNIEnv *env, jobject thiz, jdouble volume) {
    if (!g_nativeAudioEngine) {
        LOGE("❌ Native engine not initialized");
        return JNI_FALSE;
    }
    
    LOGI("🔊 Setting volume: %.2f", volume);
    bool result = g_nativeAudioEngine->setVolume(static_cast<float>(volume));
    LOGI("🔊 Set volume result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdouble JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeGetVolume(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        LOGE("❌ Native engine not initialized");
        return 0.0;
    }
    
    float volume = g_nativeAudioEngine->getVolume();
    LOGD("🔊 Current volume: %.2f", volume);
    return static_cast<jdouble>(volume);
}

JNIEXPORT jdouble JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeGetPosition(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        return 0.0;
    }
    
    double position = g_nativeAudioEngine->getPosition();
    LOGD("📍 Current position: %.2f seconds", position);
    return position;
}

JNIEXPORT jdouble JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeGetDuration(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        return 0.0;
    }
    
    double duration = g_nativeAudioEngine->getDuration();
    LOGD("📏 Duration: %.2f seconds", duration);
    return duration;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeSetPosition(JNIEnv *env, jobject thiz, jdouble seconds) {
    if (!g_nativeAudioEngine) {
        LOGE("❌ Native engine not initialized");
        return JNI_FALSE;
    }
    
    LOGI("⏩ Seeking to position: %.2f seconds", seconds);
    bool result = g_nativeAudioEngine->setPosition(seconds);
    LOGI("⏩ Seek result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeIsPlaying(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        return JNI_FALSE;
    }
    
    bool isPlaying = g_nativeAudioEngine->isPlaying();
    LOGD("▶️ Is playing: %s", isPlaying ? "YES" : "NO");
    return isPlaying ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeIsPaused(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        return JNI_FALSE;
    }
    
    bool isPaused = g_nativeAudioEngine->isPaused();
    LOGD("⏸️ Is paused: %s", isPaused ? "YES" : "NO");
    return isPaused ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeIsInitialized(JNIEnv *env, jobject thiz) {
    bool initialized = g_nativeAudioEngine && g_nativeAudioEngine->isInitialized();
    LOGD("🔍 Is initialized: %s", initialized ? "YES" : "NO");
    return initialized ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeLoadFile(JNIEnv *env, jobject thiz, jstring filePath) {
    if (!g_nativeAudioEngine) {
        LOGE("❌ Native engine not initialized");
        return JNI_FALSE;
    }
    
    std::string filePathStr = jstring_to_string_native(env, filePath);
    LOGI("📁 Loading file: %s", filePathStr.c_str());
    
    bool result = g_nativeAudioEngine->loadFile(filePathStr);
    LOGI("📁 Load file result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeGetLastError(JNIEnv *env, jobject thiz) {
    if (!g_nativeAudioEngine) {
        return env->NewStringUTF("Native engine not initialized");
    }
    
    std::string error = g_nativeAudioEngine->getLastError();
    return env->NewStringUTF(error.c_str());
}

JNIEXPORT void JNICALL
Java_com_dmusic_android_dmusic_MainActivity_nativeShutdown(JNIEnv *env, jobject thiz) {
    LOGI("🧹 Shutting down native audio engine");
    
    if (g_nativeAudioEngine) {
        g_nativeAudioEngine->shutdown();
        g_nativeAudioEngine.reset();
        LOGI("✅ Native audio engine shut down");
    }
}

} // extern "C"
