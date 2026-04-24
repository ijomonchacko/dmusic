package com.dmusic.android.dmusic

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Ultra Simple BASS Audio Manager
 * 
 * Removed all complex monitoring, health checks, and diagnostics.
 * Just pure audio playback with BASS library.
 */
class BassAudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BassAudioManager"
        
        // Load native library
        private var nativeLibraryLoaded = false
        
        init {
            try {
                Log.i(TAG, "🔄 Loading BASS libraries...")
                System.loadLibrary("bass")
                Log.i(TAG, "✅ BASS library loaded")
                
                System.loadLibrary("bassflac")
                Log.i(TAG, "✅ BASSFLAC library loaded")
                
                System.loadLibrary("bass_ssl") 
                Log.i(TAG, "✅ BASS_SSL library loaded")
                
                System.loadLibrary("bass_fx")
                Log.i(TAG, "✅ BASS_FX library loaded")
                
                // Then load our native library
                Log.i(TAG, "🔄 Loading native-lib...")
                System.loadLibrary("native-lib")
                Log.i(TAG, "✅ native-lib loaded")
                
                nativeLibraryLoaded = true
                Log.i(TAG, "✅ All native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                nativeLibraryLoaded = false
                Log.e(TAG, "❌ Failed to load native libraries: ${e.message}")
                e.printStackTrace()
            }
        }
        
        // Essential native methods only
        @JvmStatic external fun createEngine(): Long
        @JvmStatic external fun initializeEngine(engineId: Long, sampleRate: Int, channelCount: Int): Boolean
        @JvmStatic external fun destroyEngine(engineId: Long)
        @JvmStatic external fun playUrl(engineId: Long, url: String): Boolean
        @JvmStatic external fun playFile(engineId: Long, filePath: String): Boolean
        @JvmStatic external fun play(engineId: Long): Boolean
        @JvmStatic external fun pause(engineId: Long): Boolean
        @JvmStatic external fun stop(engineId: Long): Boolean
        @JvmStatic external fun seek(engineId: Long, positionMs: Long): Boolean
        @JvmStatic external fun setVolume(engineId: Long, volume: Double): Boolean
        @JvmStatic external fun getVolume(engineId: Long): Double
        @JvmStatic external fun getCurrentPosition(engineId: Long): Long
        @JvmStatic external fun getStreamLength(engineId: Long): Long
        @JvmStatic external fun getState(engineId: Long): Int
        @JvmStatic external fun getLastError(engineId: Long): String
            @JvmStatic external fun getStats(engineId: Long): HashMap<String, Any>
            @JvmStatic external fun initializeEqualizer(engineId: Long): Boolean
            @JvmStatic external fun setEqualizerEnabled(engineId: Long, enabled: Boolean): Boolean
            @JvmStatic external fun setEqualizerBand(engineId: Long, band: Int, gain: Float): Boolean
            @JvmStatic external fun getEqualizerBand(engineId: Long, band: Int): Float
            @JvmStatic external fun resetEqualizer(engineId: Long): Boolean
            @JvmStatic external fun getEqualizerBandCount(engineId: Long): Int
    }
    
    // Simple states
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
    
    // Callback interface - simplified
    interface AudioCallback {
        fun onStateChanged(state: AudioState)
        fun onPositionChanged(position: Long)
        fun onError(error: String)
    }
    
    private var audioCallback: AudioCallback? = null
    private var positionTimer: Runnable? = null
    private var isEngineInitialized = false
    private var lastReportedState: AudioState = AudioState.IDLE

    private fun notifyState(state: AudioState) {
        if (lastReportedState != state) {
            lastReportedState = state
            audioCallback?.onStateChanged(state)
        }
    }
    
    /**
     * Initialize audio engine
     */
    fun initialize(callback: AudioCallback): Boolean {
        return try {
            Log.i(TAG, "🔄 Starting initialize() - nativeLibraryLoaded: $nativeLibraryLoaded")
            
            if (!nativeLibraryLoaded) {
                Log.e(TAG, "❌ Cannot initialize: Native libraries not loaded")
                return false
            }
            
            Log.i(TAG, "🔄 Calling createEngine()...")
            engineId = createEngine()
            Log.i(TAG, "✅ createEngine() returned: $engineId")
            
            if (engineId != 0L) {
                Log.i(TAG, "🔄 Calling initializeEngine()...")
                val initResult = initializeEngine(engineId, 44100, 2)
                Log.i(TAG, "✅ initializeEngine() returned: $initResult")
                
                if (initResult) {
                    audioCallback = callback
                    isEngineInitialized = true
                    lastReportedState = AudioState.IDLE
                    Log.i(TAG, "✅ Audio engine initialized: $engineId (native: $nativeLibraryLoaded)")
                    true
                } else {
                    destroyEngine(engineId)
                    engineId = 0L
                    isEngineInitialized = false
                    Log.e(TAG, "❌ Failed to initialize audio engine")
                    false
                }
            } else {
                isEngineInitialized = false
                Log.e(TAG, "❌ Failed to create audio engine")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialize error", e)
            false
        }
    }
    
    /**
     * Play URL - ultra simple
     */
    fun playUrl(url: String): Boolean {
        return try {
            if (engineId == 0L) {
                Log.e(TAG, "Engine not initialized")
                return false
            }
            
            Log.i(TAG, "🎵 Calling playUrl with engine: $engineId, url: $url, native: $nativeLibraryLoaded")
            val result = if (nativeLibraryLoaded) {
                Companion.playUrl(engineId, url)
            } else {
                // Fallback: Just log and return true for now
                Log.i(TAG, "🔄 Fallback playUrl: $url")
                true
            }
            Log.i(TAG, "🎵 PlayUrl result: $result")
            
            if (result) {
                notifyState(AudioState.LOADING)
                startPositionTracking()
            } else {
                audioCallback?.onError("Failed to play URL")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Play URL error", e)
            audioCallback?.onError("Play error: ${e.message}")
            false
        }
    }

    /**
     * Play local file path
     */
    fun playFile(filePath: String): Boolean {
        return try {
            if (engineId == 0L) {
                Log.e(TAG, "Engine not initialized")
                return false
            }

            Log.i(TAG, "🎵 Playing local file: $filePath")
            val result = if (nativeLibraryLoaded) {
                Companion.playFile(engineId, filePath)
            } else {
                Log.i(TAG, "🔄 Fallback playFile: $filePath")
                true
            }

            if (result) {
                notifyState(AudioState.LOADING)
                startPositionTracking()
            } else {
                audioCallback?.onError("Failed to play file")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Play file error", e)
            audioCallback?.onError("Play error: ${e.message}")
            false
        }
    }
    
    /**
     * Play/Resume
     */
    fun play(): Boolean {
        return try {
            if (engineId == 0L) return false
            
            val result = if (nativeLibraryLoaded) {
                Companion.play(engineId)
            } else {
                Log.i(TAG, "🔄 Fallback play")
                true
            }
            if (result) {
                notifyState(AudioState.PLAYING)
                startPositionTracking()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Play error", e)
            false
        }
    }
    
    /**
     * Pause
     */
    fun pause(): Boolean {
        return try {
            if (engineId == 0L) return false
            
            val result = if (nativeLibraryLoaded) {
                Companion.pause(engineId)
            } else {
                Log.i(TAG, "🔄 Fallback pause")
                true
            }
            if (result) {
                notifyState(AudioState.PAUSED)
                stopPositionTracking()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Pause error", e)
            false
        }
    }
    
    /**
     * Stop
     */
    fun stop(): Boolean {
        return try {
            if (engineId == 0L) return false
            
            val result = Companion.stop(engineId)
            if (result) {
                notifyState(AudioState.IDLE)
                stopPositionTracking()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Stop error", e)
            false
        }
    }
    
    /**
     * Seek to position
     */
    fun seek(positionMs: Long): Boolean {
        return try {
            if (engineId == 0L) return false
            Companion.seek(engineId, positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Seek error", e)
            false
        }
    }
    
    /**
     * Set volume (0.0 to 1.0)
     */
    fun setVolume(volume: Double): Boolean {
        return try {
            if (engineId == 0L) return false
            Companion.setVolume(engineId, volume.coerceIn(0.0, 1.0))
        } catch (e: Exception) {
            Log.e(TAG, "Volume error", e)
            false
        }
    }
    
    /**
     * Get current position
     */
    fun getCurrentPosition(): Long {
        return try {
            if (engineId == 0L) return 0L
            Companion.getCurrentPosition(engineId)
        } catch (e: Exception) {
            Log.e(TAG, "Position error", e)
            0L
        }
    }
    
    /**
     * Get stream length
     */
    fun getStreamLength(): Long {
        return try {
            if (engineId == 0L) return 0L
            Companion.getStreamLength(engineId)
        } catch (e: Exception) {
            Log.e(TAG, "Length error", e)
            0L
        }
    }
    
    /**
     * Get current state
     */
    fun getState(): AudioState {
        return try {
            if (engineId == 0L) return AudioState.IDLE
            AudioState.values().find { it.value == Companion.getState(engineId) } ?: AudioState.ERROR
        } catch (e: Exception) {
            Log.e(TAG, "State error", e)
            AudioState.ERROR
        }
    }
    
    /**
     * Start position tracking - simple timer
     */
    private fun startPositionTracking() {
        stopPositionTracking()
        positionTimer = object : Runnable {
            override fun run() {
                try {
                    val position = getCurrentPosition()
                    audioCallback?.onPositionChanged(position)
                    
                    // Check if still playing
                    val state = getState()
                    if (state == AudioState.PLAYING) {
                        handler.postDelayed(this, 1000) // 1 second updates
                    } else {
                        notifyState(state)
                        stopPositionTracking()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Position tracking error", e)
                }
            }
        }
        handler.post(positionTimer!!)
    }
    
    /**
     * Stop position tracking
     */
    private fun stopPositionTracking() {
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
     * Cleanup resources
     */
    fun release() {
        try {
            stopPositionTracking()
            if (engineId != 0L) {
                destroyEngine(engineId)
                engineId = 0L
                isEngineInitialized = false
                Log.i(TAG, "✅ Audio engine released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Release error", e)
        }
    }

    /**
     * Tear down and rebuild the native engine – mirrors how YouTube Music restarts ExoPlayer
     * when the OS suspends decoders while the screen is off.
     */
    fun restartEngine(): Boolean {
        if (!nativeLibraryLoaded) {
            Log.e(TAG, "❌ Cannot restart engine: native libraries missing")
            return false
        }

        val callback = audioCallback
        if (callback == null) {
            Log.e(TAG, "❌ Cannot restart engine: audio callback not attached")
            return false
        }

        Log.w(TAG, "🔁 Restarting BASS engine to recover stalled playback")
        release()
        return initialize(callback)
    }
    
    /**
     * App lifecycle - pause
     */
    fun onAppPause() {
        // Simple pause handling
        if (getState() == AudioState.PLAYING) {
            pause()
        }
    }
    
    /**
     * App lifecycle - resume
     */
    fun onAppResume() {
        // Simple resume handling - let user control playback
    }
    
    /**
     * Load file from local path
     */
    fun loadFile(filePath: String): Boolean {
        return try {
            if (engineId == 0L) {
                Log.e(TAG, "Engine not initialized")
                return false
            }
            
            Log.i(TAG, "🎵 Loading file: $filePath")
            // For now, use playUrl method as the native implementation might handle both
            val result = if (nativeLibraryLoaded) {
                Companion.playUrl(engineId, filePath)
            } else {
                Log.i(TAG, "🔄 Fallback loadFile: $filePath")
                true
            }
            
            if (result) {
                audioCallback?.onStateChanged(AudioState.LOADING)
            } else {
                audioCallback?.onError("Failed to load file")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Load file error", e)
            audioCallback?.onError("Load file error: ${e.message}")
            false
        }
    }
    
    /**
     * Get current volume
     */
    fun getVolume(): Double {
        return try {
            if (engineId <= 0) return 0.0
            if (nativeLibraryLoaded) {
                Companion.getVolume(engineId)
            } else {
                1.0 // Fallback value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get volume error", e)
            0.0
        }
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
     * Get last error message
     */
    fun getLastError(): String {
        return try {
            if (engineId <= 0) return "Engine not initialized"
            if (nativeLibraryLoaded) {
                Companion.getLastError(engineId)
            } else {
                "Native libraries not loaded"
            }
        } catch (e: Exception) {
            "Error retrieving error information: ${e.message}"
        }
    }

        /**
         * Initialize the native equalizer chain for the current engine.
         */
        fun initializeEqualizer(): Boolean {
            if (engineId == 0L) return false
            return try {
                Companion.initializeEqualizer(engineId)
            } catch (e: Exception) {
                Log.e(TAG, "Equalizer initialize error", e)
                false
            }
        }

        /**
         * Enable or disable all equalizer bands at once.
         */
        fun setEqualizerEnabled(enabled: Boolean): Boolean {
            if (engineId == 0L) return false
            return try {
                Companion.setEqualizerEnabled(engineId, enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Equalizer setEnabled error", e)
                false
            }
        }

        /**
         * Update a single equalizer band gain in dB.
         */
        fun setEqualizerBand(band: Int, gain: Double): Boolean {
            if (engineId == 0L) return false
            return try {
                Companion.setEqualizerBand(engineId, band, gain.toFloat())
            } catch (e: Exception) {
                Log.e(TAG, "Equalizer setBand error", e)
                false
            }
        }

        /**
         * Fetch the gain for a single equalizer band in dB.
         */
        fun getEqualizerBand(band: Int): Double {
            if (engineId == 0L) return 0.0
            return try {
                Companion.getEqualizerBand(engineId, band).toDouble()
            } catch (e: Exception) {
                Log.e(TAG, "Equalizer getBand error", e)
                0.0
            }
        }

        /**
         * Reset all equalizer bands back to 0 dB.
         */
        fun resetEqualizer(): Boolean {
            if (engineId == 0L) return false
            return try {
                Companion.resetEqualizer(engineId)
            } catch (e: Exception) {
                Log.e(TAG, "Equalizer reset error", e)
                false
            }
        }

        /**
         * Retrieve the number of available equalizer bands.
         */
        fun getEqualizerBandCount(): Int {
            if (engineId == 0L) return 0
            return try {
                Companion.getEqualizerBandCount(engineId)
            } catch (e: Exception) {
                Log.e(TAG, "Equalizer band count error", e)
                0
            }
        }
    
    /**
     * Run tests
     */
    fun runTests(): Boolean {
        return try {
            Log.i(TAG, "Running BASS engine tests...")
            if (engineId == 0L) {
                Log.e(TAG, "Cannot run tests: Engine not initialized")
                return false
            }
            
            // Basic test: check if engine is responsive
            val state = getState()
            Log.i(TAG, "Engine state test: $state")
            
            // Test volume setting
            val volumeTest = setVolume(0.5)
            Log.i(TAG, "Volume test: $volumeTest")
            
            Log.i(TAG, "✅ Basic tests completed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Test execution error", e)
            false
        }
    }
    
    /**
     * Get BASS version
     */
    fun getBassVersion(): String {
        return try {
            if (nativeLibraryLoaded) {
                "BASS 2.4 (Android)"
            } else {
                "BASS libraries not loaded"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get version error", e)
            "Unknown version"
        }
    }
    
    /**
     * Get device information
     */
    fun getDeviceInfo(): String {
        return try {
            if (engineId <= 0) return "Engine not initialized"
            if (nativeLibraryLoaded) {
                val stats = Companion.getStats(engineId)
                val sampleRate = stats["sampleRate"] ?: 44100
                val channels = stats["channels"] ?: 2
                "Device: Android, Sample Rate: ${sampleRate}Hz, Channels: $channels"
            } else {
                "Device: Android, Sample Rate: 44100Hz, Channels: 2 (Native libraries not loaded)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get device info error", e)
            "Device info unavailable"
        }
    }
}
