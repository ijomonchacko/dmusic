#include <jni.h>
#include <android/log.h>
#include <iostream>
#include <string>
#include "BassAudioEngine.h"

#define LOG_TAG "BassAudioEngineTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Test function to verify BASS Audio Engine functionality
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dmusic_android_dmusic_BassAudioEngineTest_testEngine(JNIEnv *env, jobject thiz) {
    LOGI("=== Starting BASS Audio Engine Test ===");
    
    // Create engine instance
    BassAudioEngine engine;
    
    // Test initialization
    LOGI("Testing initialization...");
    if (engine.initialize(44100, 2)) {
        LOGI("✓ Engine initialized successfully");
    } else {
        LOGE("✗ Engine initialization failed: %s", engine.getLastError().c_str());
        return;
    }
    
    // Test engine status
    LOGI("Testing engine status...");
    if (engine.isInitialized()) {
        LOGI("✓ Engine is initialized");
    } else {
        LOGE("✗ Engine initialization status check failed");
    }
    
    // Test volume controls
    LOGI("Testing volume controls...");
    if (engine.setVolume(0.8f)) {
        LOGI("✓ Volume set to 0.8");
        float volume = engine.getVolume();
        LOGI("✓ Current volume: %.2f", volume);
    } else {
        LOGE("✗ Volume setting failed: %s", engine.getLastError().c_str());
    }
    
    // Test playback state
    LOGI("Testing playback state...");
    LOGI("Is playing: %s", engine.isPlaying() ? "true" : "false");
    LOGI("Is paused: %s", engine.isPaused() ? "true" : "false");
    
    // Test position and duration (should be 0 without loaded file)
    LOGI("Testing position/duration...");
    LOGI("Position: %.2f seconds", engine.getPosition());
    LOGI("Duration: %.2f seconds", engine.getDuration());
    
    // Test shutdown
    LOGI("Testing shutdown...");
    engine.shutdown();
    LOGI("✓ Engine shutdown complete");
    
    LOGI("=== BASS Audio Engine Test Complete ===");
}

/**
 * Test function to verify BASS libraries are properly linked
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_dmusic_android_dmusic_BassAudioEngineTest_getBassVersion(JNIEnv *env, jobject thiz) {
    LOGI("Getting BASS version information");
    
    // Get BASS version
    DWORD version = BASS_GetVersion();
    char versionStr[64];
    sprintf(versionStr, "BASS Version: %d.%d.%d.%d", 
            HIBYTE(HIWORD(version)), LOBYTE(HIWORD(version)),
            HIBYTE(LOWORD(version)), LOBYTE(LOWORD(version)));
    
    LOGI("BASS Version: %s", versionStr);
    return env->NewStringUTF(versionStr);
}

/**
 * Test function to check available audio devices
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_dmusic_android_dmusic_BassAudioEngineTest_getDeviceInfo(JNIEnv *env, jobject thiz) {
    LOGI("Getting device information");
    
    std::string deviceInfo = "Available Audio Devices:\n";
    
    // Get device count
    BASS_DEVICEINFO info;
    int deviceCount = 0;
    
    for (int i = 0; BASS_GetDeviceInfo(i, &info); i++) {
        deviceCount++;
        deviceInfo += "Device " + std::to_string(i) + ": " + std::string(info.name) + "\n";
        deviceInfo += "  Driver: " + std::string(info.driver) + "\n";
        deviceInfo += "  Enabled: " + std::string((info.flags & BASS_DEVICE_ENABLED) ? "Yes" : "No") + "\n";
        deviceInfo += "  Default: " + std::string((info.flags & BASS_DEVICE_DEFAULT) ? "Yes" : "No") + "\n\n";
    }
    
    deviceInfo += "Total devices found: " + std::to_string(deviceCount);
    
    LOGI("Device Info: %s", deviceInfo.c_str());
    return env->NewStringUTF(deviceInfo.c_str());
}
