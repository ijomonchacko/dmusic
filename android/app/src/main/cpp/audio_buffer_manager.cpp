#include <jni.h>
#include <android/log.h>
#include "bass.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AudioBufferManager", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AudioBufferManager", __VA_ARGS__)

extern "C" {

// Clear audio buffers to prevent echo and distortion
JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_NativeAudioBridge_clearAudioBuffers(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    if (engine_ptr == 0) {
        LOGE("Invalid engine pointer for buffer clearing");
        return JNI_FALSE;
    }
    
    try {
        // Stop all active channels to clear buffers
        BASS_Stop();
        
        // Clear any buffered data
        BASS_ChannelStop(BASS_STREAMPROC_END);
        
        // Flush audio device buffers
        BASS_Update(0);
        
        // Restart BASS to ensure clean state
        BASS_Start();
        
        LOGI("Audio buffers cleared successfully");
        return JNI_TRUE;
    } catch (...) {
        LOGE("Error clearing audio buffers");
        return JNI_FALSE;
    }
}

// Precache album art for notifications
JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_MainActivity_precacheAlbumArt(JNIEnv *env, jobject thiz, jstring album_art_url) {
    if (album_art_url == nullptr) {
        return JNI_FALSE;
    }
    
    const char* url = env->GetStringUTFChars(album_art_url, nullptr);
    LOGI("Precaching album art: %s", url);
    
    // This would typically involve downloading and caching the image
    // For now, we'll just log the request
    
    env->ReleaseStringUTFChars(album_art_url, url);
    return JNI_TRUE;
}

}
