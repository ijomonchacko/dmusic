#pragma once

#include <string>
#include <memory>
#include <vector>
#include <bass.h>
#include <bassflac.h>
#include <bass_ssl.h>
#include <bass_fx.h>

/**
 * BASS Audio Engine for Android
 * 
 * This class provides a simplified interface to the BASS audio library
 * for music streaming and playback functionality.
 */
class BassAudioEngine {
public:
    BassAudioEngine();
    ~BassAudioEngine();
    
    // Core initialization and cleanup
    bool initialize(int sampleRate = 44100, int channelCount = 2);
    void shutdown();
    
    // File and stream loading
    bool loadFile(const std::string& filePath);
    bool loadUrl(const std::string& url);
    
    // Playback control
    bool play();
    bool pause();
    bool stop();
    
    // Volume control
    bool setVolume(float volume);  // 0.0 to 1.0
    float getVolume() const;
    
    // Playback state
    bool isPlaying() const;
    bool isPaused() const;
    
    // Position and duration
    double getPosition() const;    // in seconds
    double getDuration() const;    // in seconds
    bool setPosition(double seconds);
    
    // Error handling
    std::string getLastError() const;
    std::string getDetailedErrorString(int error) const;
    
    // Equalizer methods
    bool initializeEqualizer();
    bool setEqualizerEnabled(bool enabled);
    bool setEqualizerBand(int band, float gain);
    float getEqualizerBand(int band);
    bool resetEqualizer();
    int getEqualizerBandCount() const;
    
    // CRITICAL FIX: Screen-aware audio configuration
    bool setScreenOffMode(bool screenOff);
    
    
    // Status
    bool isInitialized() const { return m_initialized; }
    
private:
    // Prevent copying
    BassAudioEngine(const BassAudioEngine&) = delete;
    BassAudioEngine& operator=(const BassAudioEngine&) = delete;
    
    bool m_initialized;
    HSTREAM m_currentStream;
    float m_volume;
    
    // Equalizer members
    std::vector<HFX> m_equalizerFX;
    bool m_equalizerEnabled;
    std::vector<float> m_equalizerBands;
    static const int EQUALIZER_BAND_COUNT = 10;
};
