#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>
#include "BassAudioEngine.h"

#define LOG_TAG "BassAudioBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global instance of the audio engine
static std::unique_ptr<BassAudioEngine> g_audioEngine = nullptr;

// Helper function to get string from jstring
std::string jstring_to_string(JNIEnv* env, jstring jstr) {
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

JNIEXPORT jlong JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_createEngine(JNIEnv *env, jclass clazz) {
    LOGI("Creating BASS audio engine");
    
    try {
        g_audioEngine = std::make_unique<BassAudioEngine>();
        LOGI("✅ Audio engine created successfully (not initialized yet)");
        return reinterpret_cast<jlong>(g_audioEngine.get());
    } catch (const std::exception& e) {
        LOGE("Failed to create audio engine: %s", e.what());
        g_audioEngine.reset();
        return 0;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_initializeEngine(JNIEnv *env, jclass clazz, jlong engineId, jint sampleRate, jint channelCount) {
    LOGI("Initializing BASS audio engine with sampleRate: %d, channelCount: %d", sampleRate, channelCount);
    
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return JNI_FALSE;
    }
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    if (engine != g_audioEngine.get()) {
        LOGE("Engine pointer mismatch");
        return JNI_FALSE;
    }
    
    bool result = engine->initialize(sampleRate, channelCount);
    LOGI("Initialize result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_destroyEngine(JNIEnv *env, jclass clazz, jlong engineId) {
    LOGI("Destroying BASS audio engine");
    
    if (engineId != 0 && g_audioEngine.get() == reinterpret_cast<BassAudioEngine*>(engineId)) {
        LOGI("✅ Clearing global audio engine");
        g_audioEngine.reset();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_playUrl(JNIEnv *env, jclass clazz, jlong engineId, jstring url) {
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return JNI_FALSE;
    }
    
    std::string urlStr = jstring_to_string(env, url);
    LOGI("Playing URL: %s", urlStr.c_str());
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    bool loadResult = engine->loadUrl(urlStr);
    if (loadResult) {
        bool playResult = engine->play();
        LOGI("Play URL result: %s", playResult ? "SUCCESS" : "FAILED");
        return playResult ? JNI_TRUE : JNI_FALSE;
    } else {
        LOGE("Failed to load URL: %s", engine->getLastError().c_str());
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_playFile(JNIEnv *env, jclass clazz, jlong engineId, jstring filePath) {
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return JNI_FALSE;
    }

    std::string pathStr = jstring_to_string(env, filePath);
    LOGI("Playing file: %s", pathStr.c_str());

    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    bool loadResult = engine->loadFile(pathStr);
    if (loadResult) {
        bool playResult = engine->play();
        LOGI("Play file result: %s", playResult ? "SUCCESS" : "FAILED");
        return playResult ? JNI_TRUE : JNI_FALSE;
    } else {
        LOGE("Failed to load file: %s", engine->getLastError().c_str());
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_play(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return JNI_FALSE;
    }
    
    LOGI("Starting playback");
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    bool result = engine->play();
    
    LOGI("Play result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_pause(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return JNI_FALSE;
    }
    
    LOGI("Pausing playback");
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    bool result = engine->pause();
    
    LOGI("Pause result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_stop(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return JNI_FALSE;
    }
    
    LOGI("Stopping playback");
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    bool result = engine->stop();
    
    LOGI("Stop result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_seek(JNIEnv *env, jclass clazz, jlong engineId, jlong positionMs) {
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return JNI_FALSE;
    }
    
    double seconds = positionMs / 1000.0;
    LOGI("Seeking to position: %ld ms (%.2f seconds)", positionMs, seconds);
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    bool result = engine->setPosition(seconds);
    
    LOGI("Seek result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_setVolume(JNIEnv *env, jclass clazz, jlong engineId, jdouble volume) {
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return JNI_FALSE;
    }
    
    LOGI("Setting volume: %.2f", volume);
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    bool result = engine->setVolume(static_cast<float>(volume));
    
    LOGI("Set volume result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdouble JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_getVolume(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        LOGE("Invalid engine pointer");
        return 0.0;
    }
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    float volume = engine->getVolume();
    
    LOGD("Current volume: %.2f", volume);
    return static_cast<jdouble>(volume);
}

JNIEXPORT jlong JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_getCurrentPosition(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        return 0;
    }
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    double position = engine->getPosition();
    jlong positionMs = static_cast<jlong>(position * 1000);
    
    LOGD("Current position: %.2f seconds (%ld ms)", position, positionMs);
    return positionMs;
}

JNIEXPORT jlong JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_getStreamLength(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        return 0;
    }
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    double duration = engine->getDuration();
    jlong durationMs = static_cast<jlong>(duration * 1000);
    
    LOGD("Stream length: %.2f seconds (%ld ms)", duration, durationMs);
    return durationMs;
}

JNIEXPORT jint JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_getState(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        return 4; // ERROR state
    }
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    
    if (!engine->isInitialized()) {
        return 0; // IDLE
    }
    
    if (engine->isPlaying()) {
        return 2; // PLAYING
    } else if (engine->isPaused()) {
        return 3; // PAUSED
    } else {
        return 0; // IDLE
    }
}

JNIEXPORT jstring JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_getLastError(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        return env->NewStringUTF("Invalid engine pointer");
    }
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    std::string error = engine->getLastError();
    
    return env->NewStringUTF(error.c_str());
}

JNIEXPORT jobject JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_getStats(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        return nullptr;
    }
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    
    // Create HashMap
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapConstructor = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID hashMapPut = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    
    jobject hashMap = env->NewObject(hashMapClass, hashMapConstructor);
    
    // Add stats to HashMap
    jclass doubleClass = env->FindClass("java/lang/Double");
    jmethodID doubleConstructor = env->GetMethodID(doubleClass, "<init>", "(D)V");
    
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longConstructor = env->GetMethodID(longClass, "<init>", "(J)V");
    
    jclass intClass = env->FindClass("java/lang/Integer");
    jmethodID intConstructor = env->GetMethodID(intClass, "<init>", "(I)V");
    
    // CPU usage (placeholder - would need actual implementation)
    jobject cpuUsage = env->NewObject(doubleClass, doubleConstructor, 0.0);
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("cpuUsage"), cpuUsage);
    
    // Buffer usage (placeholder - would need actual implementation)
    jobject bufferUsage = env->NewObject(doubleClass, doubleConstructor, 0.0);
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("bufferUsage"), bufferUsage);
    
    // Current position
    jobject currentPosition = env->NewObject(longClass, longConstructor, static_cast<jlong>(engine->getPosition() * 1000));
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("currentPosition"), currentPosition);
    
    // Stream length
    jobject streamLength = env->NewObject(longClass, longConstructor, static_cast<jlong>(engine->getDuration() * 1000));
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("streamLength"), streamLength);
    
    // Volume
    jobject volume = env->NewObject(doubleClass, doubleConstructor, engine->getVolume());
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("volume"), volume);
    
    // Sample rate (placeholder - would need actual implementation)
    jobject sampleRate = env->NewObject(intClass, intConstructor, 44100);
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("sampleRate"), sampleRate);
    
    // Channels (placeholder - would need actual implementation)
    jobject channels = env->NewObject(intClass, intConstructor, 2);
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("channels"), channels);
    
    // State
    int state = 0;
    if (engine->isPlaying()) state = 2;
    else if (engine->isPaused()) state = 3;
    jobject stateObj = env->NewObject(intClass, intConstructor, state);
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("state"), stateObj);
    
    // Last error
    jstring lastError = env->NewStringUTF(engine->getLastError().c_str());
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("lastError"), lastError);
    
    return hashMap;
}

JNIEXPORT void JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_onAppPause(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        return;
    }
    
    LOGI("App paused - pausing audio");
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    engine->pause();
}

JNIEXPORT void JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_onAppResume(JNIEnv *env, jclass clazz, jlong engineId) {
    if (engineId == 0) {
        return;
    }
    
    LOGI("App resumed - resuming audio if needed");
    // Note: We don't automatically resume playback - let the app control this
}

JNIEXPORT void JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_onAudioFocusChange(JNIEnv *env, jclass clazz, jlong engineId, jint focusChange) {
    if (engineId == 0) {
        return;
    }
    
    LOGI("Audio focus changed: %d", focusChange);
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    
    // Handle audio focus changes
    switch (focusChange) {
        case -1: // AUDIOFOCUS_LOSS
            LOGI("Audio focus lost - stopping playback");
            engine->stop();
            break;
        case -2: // AUDIOFOCUS_LOSS_TRANSIENT
            LOGI("Audio focus lost temporarily - pausing playback");
            engine->pause();
            break;
        case -3: // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            LOGI("Audio focus lost (can duck) - reducing volume");
            engine->setVolume(0.3f);
            break;
        case 1: // AUDIOFOCUS_GAIN
            LOGI("Audio focus gained - restoring volume");
            engine->setVolume(1.0f);
            break;
        default:
            LOGI("Unknown audio focus change: %d", focusChange);
            break;
    }
}

// Equalizer functions
JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_initializeEqualizer(JNIEnv *env, jclass clazz, jlong engineId) {
    LOGI("🎛️ JNI: initializeEqualizer called for engine ID: %ld", engineId);
    
    if (g_audioEngine == nullptr) {
        LOGE("🎛️ JNI: Audio engine not initialized");
        return JNI_FALSE;
    }
    
    bool result = g_audioEngine->initializeEqualizer();
    LOGI("🎛️ JNI: Equalizer initialization result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_setEqualizerEnabled(JNIEnv *env, jclass clazz, jlong engineId, jboolean enabled) {
    if (g_audioEngine == nullptr) {
        LOGE("Audio engine not initialized");
        return JNI_FALSE;
    }
    
    bool result = g_audioEngine->setEqualizerEnabled(enabled == JNI_TRUE);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_setEqualizerBand(JNIEnv *env, jclass clazz, jlong engineId, jint band, jfloat gain) {
    LOGI("🎛️ JNI: setEqualizerBand called - band: %d, gain: %.1fdB", band, gain);
    
    if (g_audioEngine == nullptr) {
        LOGE("🎛️ JNI: Audio engine not initialized");
        return JNI_FALSE;
    }
    
    bool result = g_audioEngine->setEqualizerBand(band, gain);
    LOGI("🎛️ JNI: setEqualizerBand result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_getEqualizerBand(JNIEnv *env, jclass clazz, jlong engineId, jint band) {
    if (g_audioEngine == nullptr) {
        LOGE("Audio engine not initialized");
        return 0.0f;
    }
    
    return g_audioEngine->getEqualizerBand(band);
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_resetEqualizer(JNIEnv *env, jclass clazz, jlong engineId) {
    if (g_audioEngine == nullptr) {
        LOGE("Audio engine not initialized");
        return JNI_FALSE;
    }
    
    bool result = g_audioEngine->resetEqualizer();
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_getEqualizerBandCount(JNIEnv *env, jclass clazz, jlong engineId) {
    if (g_audioEngine == nullptr) {
        LOGE("Audio engine not initialized");
        return 0;
    }
    
    return g_audioEngine->getEqualizerBandCount();
}

// CRITICAL FIX: Screen-aware audio configuration
JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassAudioManager_setScreenOffAudioMode(JNIEnv *env, jclass clazz, jlong engineId, jboolean screenOff) {
    LOGD("Setting screen-off audio mode: %s", screenOff ? "ON" : "OFF");
    
    if (g_audioEngine == nullptr) {
        LOGE("Engine not found - cannot set screen mode");
        return JNI_FALSE;
    }
    
    BassAudioEngine* engine = reinterpret_cast<BassAudioEngine*>(engineId);
    if (engine != g_audioEngine.get()) {
        LOGE("Invalid engine ID - cannot set screen mode");
        return JNI_FALSE;
    }
    
    bool success = engine->setScreenOffMode(screenOff);
    return success ? JNI_TRUE : JNI_FALSE;
}

// Global initialization functions for the library
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("BASS Audio Engine JNI library loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGI("BASS Audio Engine JNI library unloaded");
}

} // extern "C"
