package com.dmusic.android.dmusic

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Optimized BASS Audio Manager - Lightweight version
 * 
 * Fixes performance issues in the heavy audio pipeline:
 * - Reduced position monitoring frequency (250ms vs 75ms)
 * - Simplified state management
 * - Better error handling
 * - Optimized native calls
 */
class OptimizedBassAudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OptimizedBassAudioManager"
        
        // Load native library
        private var nativeLibraryLoaded = false
        
        init {
            try {
                Log.i(TAG, "🔄 Loading optimized BASS libraries...")
                System.loadLibrary("bass")
                System.loadLibrary("bassflac")
                System.loadLibrary("bass_ssl") 
                System.loadLibrary("bass_fx")
                System.loadLibrary("native-lib")
                
                nativeLibraryLoaded = true
                Log.i(TAG, "✅ All native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                nativeLibraryLoaded = false
                Log.e(TAG, "❌ Failed to load native libraries: ${e.message}")
            }
        }
        
        // Optimized native methods - only essential ones
        @JvmStatic external fun createOptimizedEngine(): Long
        @JvmStatic external fun initializeOptimizedEngine(engineId: Long, sampleRate: Int, channelCount: Int): Boolean
        @JvmStatic external fun destroyOptimizedEngine(engineId: Long)
        @JvmStatic external fun playOptimizedUrl(engineId: Long, url: String): Boolean
        @JvmStatic external fun playOptimized(engineId: Long): Boolean
        @JvmStatic external fun pauseOptimized(engineId: Long): Boolean
        @JvmStatic external fun stopOptimized(engineId: Long): Boolean
        @JvmStatic external fun seekOptimized(engineId: Long, positionMs: Long): Boolean
        @JvmStatic external fun setOptimizedVolume(engineId: Long, volume: Double): Boolean
        @JvmStatic external fun getOptimizedPosition(engineId: Long): Long
        @JvmStatic external fun getOptimizedLength(engineId: Long): Long
        @JvmStatic external fun getOptimizedState(engineId: Long): Int
    }
    
    // Simplified states
    enum class AudioState(val value: Int) {
        IDLE(0),
        LOADING(1),
        PLAYING(2),
        PAUSED(3),
        ERROR(4)
    }
    
    private var engineId: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Simplified callback interface
    interface OptimizedAudioCallback {
        fun onStateChanged(state: AudioState)
        fun onPositionChanged(position: Long, duration: Long)
        fun onError(error: String)
    }
    
    private var audioCallback: OptimizedAudioCallback? = null
    private var positionTimer: Runnable? = null
    private var isEngineInitialized = false
    
    /**
     * Initialize optimized audio engine
     */
    fun initialize(callback: OptimizedAudioCallback): Boolean {
        return try {
            Log.i(TAG, "🔄 Starting optimized initialize()...")
            
            if (!nativeLibraryLoaded) {
                Log.e(TAG, "❌ Cannot initialize: Native libraries not loaded")
                return false
            }
            
            engineId = createOptimizedEngine()
            Log.i(TAG, "✅ createOptimizedEngine() returned: $engineId")
            
            if (engineId != 0L) {
                val initResult = initializeOptimizedEngine(engineId, 44100, 2)
                Log.i(TAG, "✅ initializeOptimizedEngine() returned: $initResult")
                
                if (initResult) {
                    audioCallback = callback
                    isEngineInitialized = true
                    Log.i(TAG, "✅ Optimized audio engine initialized: $engineId")
                    true
                } else {
                    destroyOptimizedEngine(engineId)
                    engineId = 0L
                    isEngineInitialized = false
                    Log.e(TAG, "❌ Failed to initialize optimized audio engine")
                    false
                }
            } else {
                isEngineInitialized = false
                Log.e(TAG, "❌ Failed to create optimized audio engine")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Optimized initialize error", e)
            false
        }
    }
    
    /**
     * Play URL - optimized version
     */
    fun playUrl(url: String): Boolean {
        return try {
            if (engineId == 0L) {
                Log.e(TAG, "Engine not initialized")
                return false
            }
            
            Log.i(TAG, "🎵 Optimized playUrl: $url")
            val result = playOptimizedUrl(engineId, url)
            Log.i(TAG, "🎵 Optimized PlayUrl result: $result")
            
            if (result) {
                audioCallback?.onStateChanged(AudioState.LOADING)
                startOptimizedPositionTracking()
            } else {
                audioCallback?.onError("Failed to play URL")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Optimized play URL error", e)
            audioCallback?.onError("Play error: ${e.message}")
            false
        }
    }
    
    /**
     * Play/Resume - optimized
     */
    fun play(): Boolean {
        return try {
            if (engineId == 0L) return false
            
            val result = playOptimized(engineId)
            if (result) {
                audioCallback?.onStateChanged(AudioState.PLAYING)
                startOptimizedPositionTracking()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Optimized play error", e)
            false
        }
    }
    
    /**
     * Pause - optimized
     */
    fun pause(): Boolean {
        return try {
            if (engineId == 0L) return false
            
            val result = pauseOptimized(engineId)
            if (result) {
                audioCallback?.onStateChanged(AudioState.PAUSED)
                stopOptimizedPositionTracking()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Optimized pause error", e)
            false
        }
    }
    
    /**
     * Stop - optimized
     */
    fun stop(): Boolean {
        return try {
            if (engineId == 0L) return false
            
            val result = stopOptimized(engineId)
            if (result) {
                audioCallback?.onStateChanged(AudioState.IDLE)
                stopOptimizedPositionTracking()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Optimized stop error", e)
            false
        }
    }
    
    /**
     * Seek - optimized
     */
    fun seek(positionMs: Long): Boolean {
        return try {
            if (engineId == 0L) return false
            seekOptimized(engineId, positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Optimized seek error", e)
            false
        }
    }
    
    /**
     * Set volume - optimized
     */
    fun setVolume(volume: Double): Boolean {
        return try {
            if (engineId == 0L) return false
            setOptimizedVolume(engineId, volume.coerceIn(0.0, 1.0))
        } catch (e: Exception) {
            Log.e(TAG, "Optimized volume error", e)
            false
        }
    }
    
    /**
     * Get current position - optimized
     */
    fun getCurrentPosition(): Long {
        return try {
            if (engineId == 0L) return 0L
            getOptimizedPosition(engineId)
        } catch (e: Exception) {
            Log.e(TAG, "Optimized position error", e)
            0L
        }
    }
    
    /**
     * Get stream length - optimized
     */
    fun getStreamLength(): Long {
        return try {
            if (engineId == 0L) return 0L
            getOptimizedLength(engineId)
        } catch (e: Exception) {
            Log.e(TAG, "Optimized length error", e)
            0L
        }
    }
    
    /**
     * Get current state - optimized
     */
    fun getState(): AudioState {
        return try {
            if (engineId == 0L) return AudioState.IDLE
            AudioState.values().find { it.value == getOptimizedState(engineId) } ?: AudioState.ERROR
        } catch (e: Exception) {
            Log.e(TAG, "Optimized state error", e)
            AudioState.ERROR
        }
    }
    
    /**
     * Start optimized position tracking - 250ms intervals (vs 75ms)
     */
    private fun startOptimizedPositionTracking() {
        stopOptimizedPositionTracking()
        
        positionTimer = object : Runnable {
            override fun run() {
                try {
                    val position = getCurrentPosition()
                    val duration = getStreamLength()
                    audioCallback?.onPositionChanged(position, duration)
                    
                    // Check if still playing
                    val state = getState()
                    if (state == AudioState.PLAYING) {
                        // Optimized: 250ms intervals instead of 75ms (70% CPU reduction)
                        handler.postDelayed(this, 250)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Optimized position tracking error", e)
                }
            }
        }
        handler.post(positionTimer!!)
    }
    
    /**
     * Stop optimized position tracking
     */
    private fun stopOptimizedPositionTracking() {
        positionTimer?.let {
            handler.removeCallbacks(it)
            positionTimer = null
        }
    }
    
    /**
     * Check if engine is initialized
     */
    fun isInitialized(): Boolean {
        return isEngineInitialized && engineId != 0L && nativeLibraryLoaded
    }
    
    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean {
        return try {
            getState() == AudioState.PLAYING
        } catch (e: Exception) {
            Log.e(TAG, "Is playing error", e)
            false
        }
    }
    
    /**
     * Check if currently paused
     */
    fun isPaused(): Boolean {
        return try {
            getState() == AudioState.PAUSED
        } catch (e: Exception) {
            Log.e(TAG, "Is paused error", e)
            false
        }
    }
    
    /**
     * Cleanup resources - optimized
     */
    fun release() {
        try {
            stopOptimizedPositionTracking()
            if (engineId != 0L) {
                destroyOptimizedEngine(engineId)
                engineId = 0L
                isEngineInitialized = false
                Log.i(TAG, "✅ Optimized audio engine released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Optimized release error", e)
        }
    }
    
    /**
     * Get BASS version info
     */
    fun getBassVersion(): String {
        return try {
            if (nativeLibraryLoaded) {
                "BASS 2.4 Optimized (Android)"
            } else {
                "BASS libraries not loaded"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get version error", e)
            "Unknown version"
        }
    }
}
