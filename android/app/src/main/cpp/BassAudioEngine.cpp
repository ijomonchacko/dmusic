#include "BassAudioEngine.h"
#include <android/log.h>
#include <string>
#include <memory>
#include <unistd.h>
#include <chrono>
#include <thread>

#define LOG_TAG "BassAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

BassAudioEngine::BassAudioEngine() : m_initialized(false), m_currentStream(0), m_volume(1.0f), 
                                     m_equalizerEnabled(false) {
    LOGI("BassAudioEngine constructor called");
    // Initialize equalizer bands to 0dB
    m_equalizerBands.resize(EQUALIZER_BAND_COUNT, 0.0f);
    m_equalizerFX.resize(EQUALIZER_BAND_COUNT, 0);
}

BassAudioEngine::~BassAudioEngine() {
    LOGI("BassAudioEngine destructor called");
    shutdown();
}

bool BassAudioEngine::initialize(int sampleRate, int channelCount) {
    LOGI("Initializing BASS audio engine - Sample Rate: %d, Channels: %d", sampleRate, channelCount);
    
    if (m_initialized) {
        LOGD("BASS already initialized");
        // Verify engine is still healthy
        if (BASS_GetVersion() != 0) {
            return true;
        } else {
            LOGW("BASS appears unhealthy, reinitializing...");
            shutdown();
        }
    }

    // CRITICAL FIX: Ensure any existing BASS instance is properly cleaned up first
    if (BASS_GetVersion() != 0) {
        LOGI("Cleaning up existing BASS instance before initialization");
        BASS_Stop();
        BASS_Free();
        // Brief delay to ensure cleanup
        std::this_thread::sleep_for(std::chrono::milliseconds(50)); // 50ms delay
    }

    // Enhanced BASS configuration for Android - OPTIMIZED FOR STABILITY
    BASS_SetConfig(BASS_CONFIG_DEV_BUFFER, 80);    // 80ms buffer (enhanced stability)
    BASS_SetConfig(BASS_CONFIG_UPDATEPERIOD, 10);  // 10ms update (balanced responsiveness/power)
    BASS_SetConfig(BASS_CONFIG_UPDATETHREADS, 2);  // Use 2 threads for better performance
    BASS_SetConfig(BASS_CONFIG_FLOATDSP, TRUE);    // Enable floating point DSP
    
    // Enhanced network streaming configuration for robust background playback
    BASS_SetConfig(BASS_CONFIG_NET_TIMEOUT, 2000);     // 2 second timeout (balanced for seeking)
    BASS_SetConfig(BASS_CONFIG_NET_BUFFER, 8000);      // 8000ms buffer (seeking support)
    BASS_SetConfig(BASS_CONFIG_NET_PREBUF, 30);        // 30% prebuffer (balanced)
    BASS_SetConfig(BASS_CONFIG_NET_READTIMEOUT, 2000); // 2 second read timeout (more reliable)
    BASS_SetConfig(BASS_CONFIG_NET_PASSIVE, FALSE);    // Use active FTP mode
    BASS_SetConfig(BASS_CONFIG_NET_PLAYLIST, 1);       // Enable playlist parsing
    
    // Android-specific optimizations
    BASS_SetConfig(BASS_CONFIG_ANDROID_SESSIONID, 0);  // Use default audio session
    BASS_SetConfig(BASS_CONFIG_ANDROID_AAUDIO, 1);     // Prefer AAudio over OpenSL ES
    
    // Quality settings for better audio processing
    BASS_SetConfig(BASS_CONFIG_SRC, 4);                // Highest quality sample rate conversion
    BASS_SetConfig(BASS_CONFIG_SRC_SAMPLE, 4);         // Highest quality sample conversion
    
    // Memory and CPU optimizations
    BASS_SetConfig(BASS_CONFIG_GVOL_SAMPLE, 10000);    // Global sample volume
    BASS_SetConfig(BASS_CONFIG_GVOL_STREAM, 10000);    // Global stream volume
    BASS_SetConfig(BASS_CONFIG_GVOL_MUSIC, 10000);     // Global music volume
    
    // Initialize BASS with enhanced error checking
    DWORD flags = BASS_DEVICE_STEREO;
    if (channelCount == 1) {
        flags = BASS_DEVICE_MONO;
    }
    
    // Add latency flag for better performance
    flags |= BASS_DEVICE_LATENCY;
    
    if (!BASS_Init(-1, sampleRate, flags, 0, NULL)) {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to initialize BASS: %d (%s)", error, getDetailedErrorString(error).c_str());
        
        // Try fallback initialization without latency flag
        flags &= ~BASS_DEVICE_LATENCY;
        if (!BASS_Init(-1, sampleRate, flags, 0, NULL)) {
            error = BASS_ErrorGetCode();
            LOGE("Failed fallback BASS initialization: %d (%s)", error, getDetailedErrorString(error).c_str());
            return false;
        }
        LOGW("BASS initialized with fallback settings (no latency flag)");
    }
    
    // Start BASS with enhanced error handling
    if (!BASS_Start()) {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to start BASS: %d (%s)", error, getDetailedErrorString(error).c_str());
        BASS_Free();
        return false;
    }
    
    // Verify initialization was successful
    DWORD version = BASS_GetVersion();
    if (version == 0) {
        LOGE("BASS initialization verification failed");
        BASS_Free();
        return false;
    }
    
    // Get device info for logging
    BASS_DEVICEINFO deviceInfo;
    if (BASS_GetDeviceInfo(-1, &deviceInfo)) {
        LOGI("BASS using device: %s", deviceInfo.name);
    }
    
    m_initialized = true;
    LOGI("BASS audio engine initialized successfully (Version: 0x%X)", version);
    return true;
}

void BassAudioEngine::shutdown() {
    if (!m_initialized) {
        return;
    }
    
    LOGI("Shutting down BASS audio engine");
    
    // Enhanced shutdown sequence to prevent crashes and memory leaks
    try {
        // Stop current stream if playing
        if (m_currentStream != 0) {
            LOGI("Stopping and cleaning up current stream");
            
            // Stop playback gracefully
            BASS_ChannelStop(m_currentStream);
            
            // Wait for stream to fully stop
            int attempts = 0;
            while (BASS_ChannelIsActive(m_currentStream) != BASS_ACTIVE_STOPPED && attempts < 20) {
                std::this_thread::sleep_for(std::chrono::milliseconds(5)); // 5ms delay
                attempts++;
            }
            
            if (attempts >= 20) {
                LOGW("Stream did not stop gracefully, forcing cleanup");
            }
            
            // Clean up equalizer FX first
            for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
                if (m_equalizerFX[i] != 0) {
                    if (!BASS_ChannelRemoveFX(m_currentStream, m_equalizerFX[i])) {
                        LOGW("Failed to remove equalizer FX %d", i);
                    }
                    m_equalizerFX[i] = 0;
                }
            }
            
            // Free the stream
            if (!BASS_StreamFree(m_currentStream)) {
                int error = BASS_ErrorGetCode();
                LOGW("Failed to free stream: error %d", error);
            }
            
            m_currentStream = 0;
        }
        
        // Stop BASS engine
        if (!BASS_Stop()) {
            int error = BASS_ErrorGetCode();
            LOGW("BASS_Stop failed: error %d", error);
        }
        
        // Free all BASS resources
        if (!BASS_Free()) {
            int error = BASS_ErrorGetCode();
            LOGW("BASS_Free failed: error %d", error);
        }
        
        // Brief delay to ensure complete cleanup
        std::this_thread::sleep_for(std::chrono::milliseconds(50)); // 50ms delay
        
        m_initialized = false;
        LOGI("BASS audio engine shutdown complete");
        
    } catch (...) {
        LOGE("Exception during BASS shutdown, forcing cleanup");
        m_initialized = false;
        m_currentStream = 0;
        // Clear equalizer FX array
        for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
            m_equalizerFX[i] = 0;
        }
    }
}

bool BassAudioEngine::loadFile(const std::string& filePath) {
    if (!m_initialized) {
        LOGE("BASS not initialized");
        return false;
    }
    
    LOGI("Loading file: %s", filePath.c_str());
    
    // Stop current stream if playing
    if (m_currentStream != 0) {
        BASS_ChannelStop(m_currentStream);
        
        // Clear equalizer FX before freeing stream
        for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
            if (m_equalizerFX[i] != 0) {
                BASS_ChannelRemoveFX(m_currentStream, m_equalizerFX[i]);
                m_equalizerFX[i] = 0;
            }
        }
        
        BASS_StreamFree(m_currentStream);
        m_currentStream = 0;
    }
    
    // Create stream from file
    m_currentStream = BASS_StreamCreateFile(FALSE, filePath.c_str(), 0, 0, BASS_SAMPLE_FLOAT);
    
    if (m_currentStream == 0) {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to load file %s: error %d", filePath.c_str(), error);
        return false;
    }
    
    // Set volume
    BASS_ChannelSetAttribute(m_currentStream, BASS_ATTRIB_VOL, m_volume);
    
    // Initialize equalizer now that we have a stream
    initializeEqualizer();
    
    LOGI("File loaded successfully: %s", filePath.c_str());
    return true;
}

bool BassAudioEngine::loadUrl(const std::string& url) {
    if (!m_initialized) {
        LOGE("BASS not initialized");
        return false;
    }
    
    LOGI("Loading URL: %s", url.c_str());
    
    // Enhanced stream cleanup to prevent memory leaks and conflicts
    if (m_currentStream != 0) {
        LOGI("Cleaning up existing stream before loading new URL");
        
        // Stop playback gracefully
        BASS_ChannelStop(m_currentStream);
        
        // Wait for stream to fully stop
        int attempts = 0;
        while (BASS_ChannelIsActive(m_currentStream) == BASS_ACTIVE_PLAYING && attempts < 10) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10)); // 10ms delay
            attempts++;
        }
        
        // Clean up equalizer FX before freeing the stream
        for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
            if (m_equalizerFX[i] != 0) {
                BASS_ChannelRemoveFX(m_currentStream, m_equalizerFX[i]);
                m_equalizerFX[i] = 0;
            }
        }
        
        // Free the stream
        if (!BASS_StreamFree(m_currentStream)) {
            int error = BASS_ErrorGetCode();
            LOGW("Warning: Failed to free previous stream: %d", error);
        }
        
        m_currentStream = 0;
        
        // Brief delay to ensure cleanup
        std::this_thread::sleep_for(std::chrono::milliseconds(25)); // 25ms delay
    }
    
    // Enhanced HTTP configuration with Cloudflare-compatible user agent
    BASS_SetConfigPtr(BASS_CONFIG_NET_AGENT, const_cast<char*>("joehacks(v1)"));
    BASS_SetConfig(BASS_CONFIG_NET_PROXY, 0); // No proxy
    
    // BALANCED: Fast startup with seeking support
    BASS_SetConfig(BASS_CONFIG_NET_TIMEOUT, 2000);   // 2 second timeout (balanced)
    BASS_SetConfig(BASS_CONFIG_NET_BUFFER, 8000);    // 8 second buffer (seeking support)
    BASS_SetConfig(BASS_CONFIG_NET_PREBUF, 30);      // 30% prebuffer (balanced)
    BASS_SetConfig(BASS_CONFIG_BUFFER, 200);         // Balanced playback buffer
    
    // Seekable stream with balanced buffering
    m_currentStream = BASS_StreamCreateURL(url.c_str(), 0, BASS_SAMPLE_FLOAT, nullptr, nullptr);
    
    if (m_currentStream == 0) {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to load URL %s: error %d (%s)", url.c_str(), error, getDetailedErrorString(error).c_str());
        
        // Try fallback with basic flags only
        DWORD streamFlags = BASS_SAMPLE_FLOAT;
        m_currentStream = BASS_StreamCreateURL(url.c_str(), 0, streamFlags, nullptr, nullptr);
        
        if (m_currentStream == 0) {
            error = BASS_ErrorGetCode();
            LOGE("Failed fallback URL load %s: error %d (%s)", url.c_str(), error, getDetailedErrorString(error).c_str());
            return false;
        }
        
        LOGW("URL loaded with fallback flags");
    }
    
    // Set volume
    if (!BASS_ChannelSetAttribute(m_currentStream, BASS_ATTRIB_VOL, m_volume)) {
        int error = BASS_ErrorGetCode();
        LOGW("Warning: Failed to set volume: %d", error);
    }
    
    // Set additional attributes for better streaming
    BASS_ChannelSetAttribute(m_currentStream, BASS_ATTRIB_BUFFER, 0); // Use default buffer
    
    // Initialize equalizer now that we have a stream
    if (!initializeEqualizer()) {
        LOGW("Warning: Equalizer initialization failed, continuing without EQ");
    }
    
    // Get stream info for logging
    BASS_CHANNELINFO info;
    if (BASS_ChannelGetInfo(m_currentStream, &info)) {
        LOGI("Stream info - Freq: %d, Channels: %d, Type: 0x%X", info.freq, info.chans, info.ctype);
    }
    
    LOGI("URL loaded successfully: %s (Handle: %d)", url.c_str(), m_currentStream);
    return true;
}

bool BassAudioEngine::play() {
    if (!m_initialized || m_currentStream == 0) {
        LOGE("Cannot play - BASS not initialized or no stream loaded");
        return false;
    }
    
    LOGI("Starting playback");
    
    if (!BASS_ChannelPlay(m_currentStream, FALSE)) {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to play stream: error %d", error);
        return false;
    }
    
    LOGI("Playback started successfully");
    return true;
}

bool BassAudioEngine::pause() {
    if (!m_initialized || m_currentStream == 0) {
        LOGE("Cannot pause - BASS not initialized or no stream loaded");
        return false;
    }
    
    LOGI("Pausing playback");
    
    if (!BASS_ChannelPause(m_currentStream)) {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to pause stream: error %d", error);
        return false;
    }
    
    LOGI("Playback paused successfully");
    return true;
}

bool BassAudioEngine::stop() {
    if (!m_initialized || m_currentStream == 0) {
        LOGE("Cannot stop - BASS not initialized or no stream loaded");
        return false;
    }
    
    LOGI("Stopping playback");
    
    if (!BASS_ChannelStop(m_currentStream)) {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to stop stream: error %d", error);
        return false;
    }
    
    LOGI("Playback stopped successfully");
    return true;
}

bool BassAudioEngine::setVolume(float volume) {
    // Clamp volume between 0.0 and 1.0
    m_volume = std::max(0.0f, std::min(1.0f, volume));
    
    LOGI("Setting volume to: %.2f", m_volume);
    
    if (m_initialized && m_currentStream != 0) {
        if (!BASS_ChannelSetAttribute(m_currentStream, BASS_ATTRIB_VOL, m_volume)) {
            int error = BASS_ErrorGetCode();
            LOGE("Failed to set volume: error %d", error);
            return false;
        }
    }
    
    return true;
}

float BassAudioEngine::getVolume() const {
    return m_volume;
}

bool BassAudioEngine::isPlaying() const {
    if (!m_initialized || m_currentStream == 0) {
        return false;
    }
    
    DWORD state = BASS_ChannelIsActive(m_currentStream);
    return (state == BASS_ACTIVE_PLAYING);
}

bool BassAudioEngine::isPaused() const {
    if (!m_initialized || m_currentStream == 0) {
        return false;
    }
    
    DWORD state = BASS_ChannelIsActive(m_currentStream);
    return (state == BASS_ACTIVE_PAUSED);
}

double BassAudioEngine::getPosition() const {
    if (!m_initialized || m_currentStream == 0) {
        return 0.0;
    }
    
    QWORD pos = BASS_ChannelGetPosition(m_currentStream, BASS_POS_BYTE);
    if (pos == (QWORD)-1) {
        return 0.0;
    }
    
    return BASS_ChannelBytes2Seconds(m_currentStream, pos);
}

double BassAudioEngine::getDuration() const {
    if (!m_initialized || m_currentStream == 0) {
        return 0.0;
    }
    
    QWORD length = BASS_ChannelGetLength(m_currentStream, BASS_POS_BYTE);
    if (length == (QWORD)-1) {
        return 0.0;
    }
    
    return BASS_ChannelBytes2Seconds(m_currentStream, length);
}

bool BassAudioEngine::setPosition(double seconds) {
    if (!m_initialized || m_currentStream == 0) {
        return false;
    }
    
    QWORD pos = BASS_ChannelSeconds2Bytes(m_currentStream, seconds);
    if (pos == (QWORD)-1) {
        return false;
    }
    
    return BASS_ChannelSetPosition(m_currentStream, pos, BASS_POS_BYTE);
}



std::string BassAudioEngine::getLastError() const {
    int error = BASS_ErrorGetCode();
    return getDetailedErrorString(error);
}

std::string BassAudioEngine::getDetailedErrorString(int error) const {
    switch (error) {
        case BASS_OK: return "No error";
        case BASS_ERROR_MEM: return "Memory error - insufficient memory or memory allocation failed";
        case BASS_ERROR_FILEOPEN: return "Can't open file - file not found or access denied";
        case BASS_ERROR_DRIVER: return "Can't find audio driver - no suitable audio device available";
        case BASS_ERROR_BUFLOST: return "Buffer lost - audio buffer was lost, likely due to system interruption";
        case BASS_ERROR_HANDLE: return "Invalid handle - the stream/channel handle is invalid or has been freed";
        case BASS_ERROR_FORMAT: return "Unsupported format - audio format not supported by BASS";
        case BASS_ERROR_POSITION: return "Invalid position - seek position is beyond stream length";
        case BASS_ERROR_INIT: return "BASS not initialized - call BASS_Init first";
        case BASS_ERROR_START: return "BASS not started - call BASS_Start after initialization";
        case BASS_ERROR_SSL: return "SSL not available - HTTPS streaming requires SSL support";
        case BASS_ERROR_ALREADY: return "Already initialized - BASS is already initialized";
        case BASS_ERROR_NOTAUDIO: return "Not an audio file - file is not a recognized audio format";
        case BASS_ERROR_NOCHAN: return "No free channels - maximum number of channels reached";
        case BASS_ERROR_ILLTYPE: return "Illegal type - invalid channel/stream type specified";
        case BASS_ERROR_ILLPARAM: return "Illegal parameter - invalid parameter value provided";
        case BASS_ERROR_NO3D: return "No 3D support - 3D audio features not available";
        case BASS_ERROR_NOEAX: return "No EAX support - EAX effects not available";
        case BASS_ERROR_DEVICE: return "Illegal device - invalid audio device specified";
        case BASS_ERROR_NOPLAY: return "Not playing - channel is not in playing state";
        case BASS_ERROR_FREQ: return "Illegal sample rate - unsupported sample rate specified";
        case BASS_ERROR_NOTFILE: return "Not a file stream - operation only valid for file streams";
        case BASS_ERROR_NOHW: return "No hardware voices - no hardware mixing voices available";
        case BASS_ERROR_EMPTY: return "Empty file - audio file contains no data";
        case BASS_ERROR_NONET: return "No internet connection - network stream requires internet access";
        case BASS_ERROR_CREATE: return "Couldn't create file - file creation failed";
        case BASS_ERROR_NOFX: return "Effects not available - BASS_FX library not loaded";
        case BASS_ERROR_NOTAVAIL: return "Not available - feature not available on this platform";
        case BASS_ERROR_DECODE: return "Decoding error - audio decoding failed, possibly corrupt data";
        case BASS_ERROR_TIMEOUT: return "Connection timeout - network connection timed out";
        case BASS_ERROR_FILEFORM: return "Unsupported file format - file format not supported";
        case BASS_ERROR_CODEC: return "Codec not available - required codec not installed";
        case BASS_ERROR_ENDED: return "Channel/file ended - playback reached end of stream";
        case BASS_ERROR_BUSY: return "Device busy - audio device is in use by another application";
        case BASS_ERROR_UNSTREAMABLE: return "Unstreamable file - file cannot be streamed";
        case BASS_ERROR_PROTOCOL: return "Unsupported protocol - network protocol not supported";
        case BASS_ERROR_DENIED: return "Access denied - insufficient permissions or server rejected connection";
        default: 
            char errorStr[64];
            snprintf(errorStr, sizeof(errorStr), "Unknown error (code: %d)", error);
            return std::string(errorStr);
    }
}

// ===== EQUALIZER IMPLEMENTATION =====

bool BassAudioEngine::initializeEqualizer() {
    LOGI("Initializing equalizer");
    
    if (!m_initialized) {
        LOGE("Cannot initialize equalizer - engine not ready");
        return false;
    }
    
    // If no stream is loaded, just mark as ready for when stream is loaded
    if (m_currentStream == 0) {
        LOGI("No stream loaded yet, equalizer will be initialized when stream is loaded");
        m_equalizerEnabled = true;
        return true;
    }
    
    // Remove existing equalizer FX if any
    for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
        if (m_equalizerFX[i] != 0) {
            BASS_ChannelRemoveFX(m_currentStream, m_equalizerFX[i]);
            m_equalizerFX[i] = 0;
        }
    }
    
    // Define frequency bands (Hz)
    static const float frequencies[] = {
        32.0f, 64.0f, 125.0f, 250.0f, 500.0f,
        1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
    };
    
    // Create a parametric EQ FX for each band
    for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
        m_equalizerFX[i] = BASS_ChannelSetFX(m_currentStream, BASS_FX_BFX_PEAKEQ, 0);
        if (m_equalizerFX[i] == 0) {
            int error = BASS_ErrorGetCode();
            LOGE("Failed to set equalizer FX for band %d: %d", i, error);
            return false;
        }
        
        // Set up the frequency and initial parameters
        BASS_BFX_PEAKEQ eq;
        eq.lBand = i;          // Band index
        eq.fBandwidth = 1.0f;  // 1 octave bandwidth
        eq.fQ = 0.7f;          // Q factor
        eq.fCenter = frequencies[i];
        eq.fGain = 0.0f;       // Start at 0dB
        eq.lChannel = BASS_BFX_CHANALL;  // Apply to all channels
        
        if (!BASS_FXSetParameters(m_equalizerFX[i], &eq)) {
            int error = BASS_ErrorGetCode();
            LOGE("Failed to set initial parameters for band %d: %d", i, error);
            return false;
        }
        
        LOGD("Initialized equalizer band %d at %.0fHz", i, frequencies[i]);
    }
    
    // Enable equalizer by default
    m_equalizerEnabled = true;
    
    LOGI("Equalizer initialized successfully with %d bands", EQUALIZER_BAND_COUNT);
    return true;
}

bool BassAudioEngine::setEqualizerEnabled(bool enabled) {
    LOGI("Setting equalizer enabled: %s", enabled ? "true" : "false");
    
    if (!m_initialized || m_currentStream == 0) {
        LOGE("Cannot set equalizer enabled - engine not ready or no stream");
        return false;
    }
    
    m_equalizerEnabled = enabled;
    
    if (!enabled) {
        // Disable by setting all bands to 0dB
        for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
            if (!setEqualizerBand(i, 0.0f)) {
                return false;
            }
        }
    } else {
        // Re-apply current band settings
        for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
            if (!setEqualizerBand(i, m_equalizerBands[i])) {
                return false;
            }
        }
    }
    
    return true;
}

bool BassAudioEngine::setEqualizerBand(int band, float gain) {
    if (band < 0 || band >= EQUALIZER_BAND_COUNT) {
        LOGE("Invalid equalizer band: %d", band);
        return false;
    }
    
    if (!m_initialized) {
        LOGE("Cannot set equalizer band - engine not ready");
        return false;
    }
    
    // Store the band value even if no stream is loaded
    gain = std::max(-15.0f, std::min(15.0f, gain));
    m_equalizerBands[band] = gain;
    
    // If no stream is loaded, just store the value for later
    if (m_currentStream == 0 || m_equalizerFX[band] == 0) {
        LOGD("No stream loaded, stored equalizer band %d value: %.1fdB", band, gain);
        return true;
    }
    
    // Define frequency bands (Hz)
    static const float frequencies[] = {
        32.0f, 64.0f, 125.0f, 250.0f, 500.0f,
        1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
    };
    
    // Set up parametric EQ parameters
    BASS_BFX_PEAKEQ eq;
    eq.lBand = band;       // Band index
    eq.fBandwidth = 1.0f;  // 1 octave bandwidth
    eq.fQ = 0.7f;          // Q factor
    eq.fCenter = frequencies[band];
    eq.fGain = m_equalizerEnabled ? gain : 0.0f;
    eq.lChannel = BASS_BFX_CHANALL;  // Apply to all channels
    
    if (!BASS_FXSetParameters(m_equalizerFX[band], &eq)) {
        int error = BASS_ErrorGetCode();
        LOGE("Failed to set equalizer band %d: %d", band, error);
        return false;
    }
    
    LOGD("Set equalizer band %d (%.0fHz) to %.1fdB", band, frequencies[band], gain);
    return true;
}

float BassAudioEngine::getEqualizerBand(int band) {
    if (band < 0 || band >= EQUALIZER_BAND_COUNT) {
        LOGE("Invalid equalizer band: %d", band);
        return 0.0f;
    }
    
    return m_equalizerBands[band];
}

bool BassAudioEngine::resetEqualizer() {
    LOGI("Resetting equalizer to flat");
    
    for (int i = 0; i < EQUALIZER_BAND_COUNT; i++) {
        if (!setEqualizerBand(i, 0.0f)) {
            return false;
        }
    }
    
    return true;
}

int BassAudioEngine::getEqualizerBandCount() const {
    return EQUALIZER_BAND_COUNT;
}

// CRITICAL FIX: Screen-aware audio configuration to prevent distortion
bool BassAudioEngine::setScreenOffMode(bool screenOff) {
    if (!m_initialized) {
        LOGE("Cannot set screen mode - BASS not initialized");
        return false;
    }
    
    if (screenOff) {
        LOGI("🌙 Applying screen-off audio settings (stable buffers)");
        // Switch to more stable buffer settings for background playback
        BASS_SetConfig(BASS_CONFIG_DEV_BUFFER, 100);   // 100ms buffer (more stable)
        BASS_SetConfig(BASS_CONFIG_UPDATEPERIOD, 10);  // 10ms update period (power-efficient)
        BASS_SetConfig(BASS_CONFIG_NET_BUFFER, 8000);  // 8000ms network buffer (seeking support)
        BASS_SetConfig(BASS_CONFIG_NET_PREBUF, 25);    // 25% prebuffer (reduces underruns)
    } else {
        LOGI("☀️ Applying screen-on audio settings (low latency)");
        // Restore low-latency settings for foreground playback
        BASS_SetConfig(BASS_CONFIG_DEV_BUFFER, 50);    // 50ms buffer (balanced)
        BASS_SetConfig(BASS_CONFIG_UPDATEPERIOD, 5);   // 5ms update period (responsive)
        BASS_SetConfig(BASS_CONFIG_NET_BUFFER, 8000);   // 8000ms network buffer (seeking support)
        BASS_SetConfig(BASS_CONFIG_NET_PREBUF, 15);    // 15% prebuffer (fast start)
    }
    
    LOGI("✅ Screen-aware audio configuration applied");
    return true;
}
