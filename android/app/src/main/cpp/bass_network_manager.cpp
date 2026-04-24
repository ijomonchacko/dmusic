#include <jni.h>
#include <android/log.h>
#include <string>
#include <unordered_map>
#include <mutex>
#include <chrono>
#include <thread>
#include "bass.h"

#define LOG_TAG "BassNetworkManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Network stream cache to prevent duplicate connections
static std::unordered_map<std::string, HSTREAM> streamCache;
static std::mutex cacheMutex;
static constexpr int MAX_CACHE_SIZE = 10;

// Network callback for monitoring stream status
void CALLBACK NetworkStatusCallback(const void *buffer, DWORD length, void *user) {
    if (buffer && length > 0) {
        LOGD("Network buffer received: %d bytes", length);
    }
}

// Enhanced URL validation
bool isValidStreamUrl(const std::string& url) {
    if (url.empty() || url.length() < 10) {
        return false;
    }
    
    // Check for supported protocols
    if (url.find("http://") == 0 || 
        url.find("https://") == 0 || 
        url.find("ftp://") == 0) {
        return true;
    }
    
    return false;
}

// Configure network settings for optimal streaming
void configureNetworkOptimizations() {
    LOGI("Configuring enhanced network optimizations for BASS streaming");
    
    // Enhanced timeout settings for different network conditions
    BASS_SetConfig(BASS_CONFIG_NET_TIMEOUT, 8000);        // 8 second connection timeout
    BASS_SetConfig(BASS_CONFIG_NET_BUFFER, 2000);         // 2000ms buffer for stability
    BASS_SetConfig(BASS_CONFIG_NET_PREBUF, 25);           // 25% prebuffer for smooth playback
    BASS_SetConfig(BASS_CONFIG_NET_READTIMEOUT, 3000);    // 3 second read timeout
    
    // Additional network optimizations
    BASS_SetConfig(BASS_CONFIG_NET_PASSIVE, FALSE);       // Use active mode
    BASS_SetConfig(BASS_CONFIG_NET_PLAYLIST, 2);          // Enhanced playlist support
    
    // Set Cloudflare-compatible user agent to avoid blocking
    BASS_SetConfigPtr(BASS_CONFIG_NET_AGENT, 
        const_cast<char*>("joehacks(v1)"));
    
    // Configure proxy settings (none by default)
    BASS_SetConfig(BASS_CONFIG_NET_PROXY, 0);
    
    LOGI("Network optimizations configured successfully");
}

// Create enhanced network stream with caching and error recovery
HSTREAM createEnhancedNetworkStream(const std::string& url, DWORD flags) {
    if (!isValidStreamUrl(url)) {
        LOGE("Invalid stream URL: %s", url.c_str());
        return 0;
    }
    
    LOGI("Creating enhanced network stream for: %s", url.c_str());
    
    // Check cache first
    {
        std::lock_guard<std::mutex> lock(cacheMutex);
        auto it = streamCache.find(url);
        if (it != streamCache.end()) {
            HSTREAM cachedStream = it->second;
            // Verify cached stream is still valid
            if (BASS_ChannelIsActive(cachedStream) != BASS_ACTIVE_STOPPED) {
                LOGD("Using cached stream for URL: %s", url.c_str());
                return cachedStream;
            } else {
                // Remove invalid cached stream
                BASS_StreamFree(cachedStream);
                streamCache.erase(it);
            }
        }
    }
    
    // Configure network settings before creating stream
    configureNetworkOptimizations();
    
    // Enhanced flags for better streaming
    DWORD enhancedFlags = flags | BASS_STREAM_BLOCK | BASS_STREAM_STATUS;
    
    // Create stream with retry logic
    HSTREAM stream = 0;
    int attempts = 0;
    const int maxAttempts = 3;
    
    while (attempts < maxAttempts && stream == 0) {
        attempts++;
        LOGI("Stream creation attempt %d/%d for URL: %s", attempts, maxAttempts, url.c_str());
        
        stream = BASS_StreamCreateURL(url.c_str(), 0, enhancedFlags, 
                                     NetworkStatusCallback, nullptr);
        
        if (stream == 0) {
            int error = BASS_ErrorGetCode();
            LOGE("Stream creation failed (attempt %d): error %d", attempts, error);
            
            if (attempts < maxAttempts) {
                // Wait before retry, with exponential backoff
                int delayMs = 1000 * attempts;
                std::this_thread::sleep_for(std::chrono::milliseconds(delayMs));
                
                // Try with reduced flags on retry
                if (attempts == 2) {
                    enhancedFlags = flags | BASS_STREAM_BLOCK;
                    LOGW("Retrying with reduced flags");
                }
            }
        }
    }
    
    if (stream != 0) {
        LOGI("Network stream created successfully: handle %d", stream);
        
        // Cache the stream (with size limit)
        {
            std::lock_guard<std::mutex> lock(cacheMutex);
            if (streamCache.size() >= MAX_CACHE_SIZE) {
                // Remove oldest entry
                auto oldest = streamCache.begin();
                BASS_StreamFree(oldest->second);
                streamCache.erase(oldest);
            }
            streamCache[url] = stream;
        }
        
        // Get stream info for diagnostics
        BASS_CHANNELINFO info;
        if (BASS_ChannelGetInfo(stream, &info)) {
            LOGI("Stream info - Type: 0x%X, Freq: %d, Channels: %d", 
                 info.ctype, info.freq, info.chans);
        }
    } else {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to create network stream after %d attempts: error %d", maxAttempts, error);
    }
    
    return stream;
}

// Cleanup network resources
void cleanupNetworkStreams() {
    LOGI("Cleaning up network streams");
    
    std::lock_guard<std::mutex> lock(cacheMutex);
    for (auto& pair : streamCache) {
        BASS_StreamFree(pair.second);
    }
    streamCache.clear();
    
    LOGI("Network cleanup completed");
}

// JNI exports for network management
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dmusic_android_dmusic_BassNetworkManager_createNetworkStream(
    JNIEnv *env, jobject thiz, jstring url, jint flags) {
    
    const char* urlStr = env->GetStringUTFChars(url, nullptr);
    std::string urlString(urlStr);
    env->ReleaseStringUTFChars(url, urlStr);
    
    HSTREAM stream = createEnhancedNetworkStream(urlString, static_cast<DWORD>(flags));
    return static_cast<jlong>(stream);
}

JNIEXPORT void JNICALL
Java_com_dmusic_android_dmusic_BassNetworkManager_cleanupNetworkResources(
    JNIEnv *env, jobject thiz) {
    cleanupNetworkStreams();
}

JNIEXPORT jboolean JNICALL
Java_com_dmusic_android_dmusic_BassNetworkManager_configureNetworkSettings(
    JNIEnv *env, jobject thiz) {
    try {
        configureNetworkOptimizations();
        return JNI_TRUE;
    } catch (...) {
        return JNI_FALSE;
    }
}

}
