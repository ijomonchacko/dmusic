package com.dmusic.android.dmusic;

import android.util.Log;

/**
 * Java wrapper for the BASS Audio Engine native library
 */
public class BassAudioEngine {
    private static final String TAG = "BassAudioEngine";
    
    // Native library name
    static {
        try {
            System.loadLibrary("native-lib");
            Log.i(TAG, "BASS Audio Engine native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load BASS Audio Engine native library: " + e.getMessage());
            throw new RuntimeException("Failed to load BASS Audio Engine native library", e);
        }
    }
    
    private long enginePtr = 0;
    
    // Native method declarations
    private native long createEngine();
    private native void destroyEngine(long enginePtr);
    private native boolean initialize(long enginePtr, int sampleRate, int channelCount);
    private native void shutdown(long enginePtr);
    private native boolean loadFile(long enginePtr, String filePath);
    private native boolean loadUrl(long enginePtr, String url);
    private native boolean play(long enginePtr);
    private native boolean pause(long enginePtr);
    private native boolean stop(long enginePtr);
    private native boolean setVolume(long enginePtr, float volume);
    private native float getVolume(long enginePtr);
    private native boolean isPlaying(long enginePtr);
    private native boolean isPaused(long enginePtr);
    private native double getPosition(long enginePtr);
    private native double getDuration(long enginePtr);
    private native boolean setPosition(long enginePtr, double seconds);
    private native String getLastError(long enginePtr);
    private native boolean isInitialized(long enginePtr);
    
    /**
     * Constructor - Creates a new BASS audio engine instance
     */
    public BassAudioEngine() {
        Log.i(TAG, "Creating BASS Audio Engine instance");
        enginePtr = createEngine();
        if (enginePtr == 0) {
            throw new RuntimeException("Failed to create BASS Audio Engine");
        }
    }
    
    /**
     * Initialize the audio engine
     * @param sampleRate Sample rate (default: 44100)
     * @param channelCount Channel count (1 for mono, 2 for stereo)
     * @return true if successful
     */
    public boolean initialize(int sampleRate, int channelCount) {
        Log.i(TAG, "Initializing BASS Audio Engine - Sample Rate: " + sampleRate + ", Channels: " + channelCount);
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return false;
        }
        return initialize(enginePtr, sampleRate, channelCount);
    }
    
    /**
     * Initialize with default settings (44100 Hz, stereo)
     * @return true if successful
     */
    public boolean initialize() {
        return initialize(44100, 2);
    }
    
    /**
     * Shutdown the audio engine
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down BASS Audio Engine");
        if (enginePtr != 0) {
            shutdown(enginePtr);
        }
    }
    
    /**
     * Load an audio file
     * @param filePath Path to the audio file
     * @return true if successful
     */
    public boolean loadFile(String filePath) {
        Log.i(TAG, "Loading file: " + filePath);
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return false;
        }
        return loadFile(enginePtr, filePath);
    }
    
    /**
     * Load an audio stream from URL
     * @param url URL to the audio stream
     * @return true if successful
     */
    public boolean loadUrl(String url) {
        Log.i(TAG, "Loading URL: " + url);
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return false;
        }
        return loadUrl(enginePtr, url);
    }
    
    /**
     * Start playback
     * @return true if successful
     */
    public boolean play() {
        Log.i(TAG, "Starting playback");
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return false;
        }
        return play(enginePtr);
    }
    
    /**
     * Pause playback
     * @return true if successful
     */
    public boolean pause() {
        Log.i(TAG, "Pausing playback");
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return false;
        }
        return pause(enginePtr);
    }
    
    /**
     * Stop playback
     * @return true if successful
     */
    public boolean stop() {
        Log.i(TAG, "Stopping playback");
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return false;
        }
        return stop(enginePtr);
    }
    
    /**
     * Set volume level
     * @param volume Volume level (0.0 to 1.0)
     * @return true if successful
     */
    public boolean setVolume(float volume) {
        Log.i(TAG, "Setting volume: " + volume);
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return false;
        }
        return setVolume(enginePtr, volume);
    }
    
    /**
     * Get current volume level
     * @return Volume level (0.0 to 1.0)
     */
    public float getVolume() {
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return 0.0f;
        }
        return getVolume(enginePtr);
    }
    
    /**
     * Check if audio is currently playing
     * @return true if playing
     */
    public boolean isPlaying() {
        if (enginePtr == 0) {
            return false;
        }
        return isPlaying(enginePtr);
    }
    
    /**
     * Check if audio is currently paused
     * @return true if paused
     */
    public boolean isPaused() {
        if (enginePtr == 0) {
            return false;
        }
        return isPaused(enginePtr);
    }
    
    /**
     * Get current playback position
     * @return Position in seconds
     */
    public double getPosition() {
        if (enginePtr == 0) {
            return 0.0;
        }
        return getPosition(enginePtr);
    }
    
    /**
     * Get total duration of the current audio
     * @return Duration in seconds
     */
    public double getDuration() {
        if (enginePtr == 0) {
            return 0.0;
        }
        return getDuration(enginePtr);
    }
    
    /**
     * Set playback position
     * @param seconds Position in seconds
     * @return true if successful
     */
    public boolean setPosition(double seconds) {
        Log.i(TAG, "Setting position: " + seconds + " seconds");
        if (enginePtr == 0) {
            Log.e(TAG, "Engine not created");
            return false;
        }
        return setPosition(enginePtr, seconds);
    }
    
    /**
     * Get the last error message
     * @return Error message string
     */
    public String getLastError() {
        if (enginePtr == 0) {
            return "Engine not created";
        }
        return getLastError(enginePtr);
    }
    
    /**
     * Check if the engine is initialized
     * @return true if initialized
     */
    public boolean isInitialized() {
        if (enginePtr == 0) {
            return false;
        }
        return isInitialized(enginePtr);
    }
    
    /**
     * Clean up resources
     */
    public void dispose() {
        Log.i(TAG, "Disposing BASS Audio Engine");
        if (enginePtr != 0) {
            shutdown(enginePtr);
            destroyEngine(enginePtr);
            enginePtr = 0;
        }
    }
    
    /**
     * Finalize - ensure cleanup
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
    }
}
