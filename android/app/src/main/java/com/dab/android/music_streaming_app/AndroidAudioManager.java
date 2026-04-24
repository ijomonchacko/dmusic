package com.dmusic.android.dmusic;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.util.Log;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AndroidAudioManager {
    private static final String TAG = "WorkingAudioManager";
    private static boolean isInitialized = false;
    private static Context appContext;
    private static String currentUrl = "";
    private static MediaPlayer mediaPlayer;
    private static boolean isPlaying = false;
    private static boolean isPrepared = false;
    
    /**
     * Initialize the audio manager with MediaPlayer for reliable streaming
     */
    public static void initialize(Context context) {
        try {
            appContext = context.getApplicationContext();
            isInitialized = true;
            Log.i(TAG, "✅ Working Audio Manager initialized with MediaPlayer");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize audio manager: " + e.getMessage(), e);
            isInitialized = false;
        }
    }
    
    /**
     * Create a new audio engine (returns dummy ID)
     */
    public static long createEngine() {
        Log.i(TAG, "🎵 Creating audio engine...");
        if (!isInitialized) {
            Log.e(TAG, "❌ Audio manager not initialized");
            return 0;
        }
        return 1L; // Return dummy engine ID
    }
    
    /**
     * Destroy the audio engine
     */
    public static void destroyEngine(long enginePtr) {
        Log.i(TAG, "🧹 Destroying audio engine...");
        stopPlayback();
        cleanup();
    }
    
    /**
     * Play a stream from URL with MediaPlayer
     */
    public static boolean playStream(long enginePtr, String url) {
        Log.i(TAG, "▶️ Playing stream: " + url);
        
        try {
            // Stop any existing playback
            stopPlayback();
            
            // Create new MediaPlayer instance
            mediaPlayer = new MediaPlayer();
            
            // Configure for high-quality network streaming
            mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            );
            
            // Set up completion listener
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.i(TAG, "🏁 Playback completed");
                isPlaying = false;
                isPrepared = false;
            });
            
            // Set up error listener
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "❌ MediaPlayer error: what=" + what + ", extra=" + extra);
                isPlaying = false;
                isPrepared = false;
                return true; // Handle the error
            });
            
            // Set up prepared listener
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.i(TAG, "✅ MediaPlayer prepared, starting playback");
                isPrepared = true;
                mp.start();
                isPlaying = true;
                
                Log.i(TAG, "🎵 Playback started successfully!");
                Log.i(TAG, "🎵 Duration: " + mp.getDuration() + " ms");
            });
            
            // Set up buffering update listener
            mediaPlayer.setOnBufferingUpdateListener((mp, percent) -> {
                Log.d(TAG, "📊 Buffering: " + percent + "%");
            });
            
            // Set the data source and prepare asynchronously
            mediaPlayer.setDataSource(url);
            currentUrl = url;
            mediaPlayer.prepareAsync();
            
            Log.i(TAG, "🔄 Preparing MediaPlayer for: " + url);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Exception playing stream: " + e.getMessage(), e);
            isPlaying = false;
            isPrepared = false;
            return false;
        }
    }
    
    /**
     * Play URL directly
     */
    public static boolean playUrl(String url) {
        return playStream(1L, url);
    }
    
    /**
     * Stop playback
     */
    public static void stopPlayback() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
            isPlaying = false;
            isPrepared = false;
            Log.i(TAG, "⏹️ Playback stopped");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error stopping playback: " + e.getMessage(), e);
        }
    }
    
    /**
     * Pause playback
     */
    public static boolean pausePlayback() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isPlaying = false;
                Log.i(TAG, "⏸️ Playback paused");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error pausing playback: " + e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * Resume playback
     */
    public static boolean resumePlayback() {
        try {
            if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                isPlaying = true;
                Log.i(TAG, "▶️ Playback resumed");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error resuming playback: " + e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * Restart playback
     */
    public static boolean restartPlayback() {
        Log.i(TAG, "🔄 Restarting playback...");
        if (!currentUrl.isEmpty()) {
            return playUrl(currentUrl);
        }
        return false;
    }
    
    /**
     * Check if currently playing
     */
    public static boolean isPlaying() {
        try {
            return mediaPlayer != null && mediaPlayer.isPlaying() && isPlaying;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if engine is ready
     */
    public static boolean isEngineReady() {
        return isInitialized;
    }
    
    /**
     * Get current position
     */
    public static long getCurrentPosition() {
        try {
            if (mediaPlayer != null && isPrepared) {
                return mediaPlayer.getCurrentPosition();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting position: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Get duration
     */
    public static long getDuration() {
        try {
            if (mediaPlayer != null && isPrepared) {
                return mediaPlayer.getDuration();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting duration: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Seek to position
     */
    public static boolean seek(long positionMs) {
        try {
            if (mediaPlayer != null && isPrepared) {
                mediaPlayer.seekTo((int)positionMs);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error seeking: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Set volume (0.0 to 1.0)
     */
    public static void setVolume(float volume) {
        try {
            if (mediaPlayer != null) {
                volume = Math.max(0.0f, Math.min(1.0f, volume)); // Clamp to valid range
                mediaPlayer.setVolume(volume, volume);
                Log.i(TAG, "🔊 Volume set to " + (volume * 100) + "%");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting volume: " + e.getMessage());
        }
    }
    
    /**
     * Get playback status
     */
    public static Map<String, Object> getPlaybackStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isPlaying", isPlaying());
        status.put("position", getCurrentPosition());
        status.put("duration", getDuration());
        status.put("url", currentUrl);
        status.put("isPrepared", isPrepared);
        return status;
    }
    
    /**
     * Get sample rate (default for now)
     */
    public static int getSampleRate() {
        return 48000; // MediaPlayer default
    }
    
    /**
     * Get channel count (default stereo)
     */
    public static int getChannels() {
        return 2; // Stereo
    }
    
    /**
     * Get debug info
     */
    public static String getNativeDebugInfo() {
        return "MediaPlayer-based streaming: " + 
               "Playing=" + isPlaying() + 
               ", Prepared=" + isPrepared + 
               ", URL=" + currentUrl;
    }
    
    /**
     * Set native buffer size (not applicable for MediaPlayer)
     */
    public static void setNativeBufferSize(int bufferSize) {
        Log.d(TAG, "Buffer size setting not applicable for MediaPlayer");
    }
    
    /**
     * Get device capabilities
     */
    public static Map<String, Object> getDeviceCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("engine", "MediaPlayer");
        capabilities.put("maxSampleRate", 48000);
        capabilities.put("channels", 2);
        capabilities.put("streaming", true);
        capabilities.put("https", true);
        return capabilities;
    }
    
    /**
     * Cleanup resources
     */
    private static void cleanup() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            isPlaying = false;
            isPrepared = false;
            currentUrl = "";
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage());
        }
    }

    // Native method placeholders (not used in MediaPlayer implementation)
    // Using pure Java MediaPlayer - no native methods needed
} 