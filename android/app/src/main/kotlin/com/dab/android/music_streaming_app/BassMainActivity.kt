package com.dmusic.android.dmusic

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.util.Log

class BassMainActivity : FlutterActivity() {
    
    companion object {
        private const val TAG = "BassMainActivity"
        private const val CHANNEL = "native_audio_engine"
    }
    
    private lateinit var methodChannel: MethodChannel
    private var audioManager: BassAudioManager? = null
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Initialize method channel
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "initialize" -> {
                    val args = call.arguments as? Map<String, Any>
                    val sampleRate = args?.get("sampleRate") as? Int ?: 44100
                    val channelCount = args?.get("channelCount") as? Int ?: 2
                    Log.i(TAG, "🔄 Initialize called with sampleRate: $sampleRate, channelCount: $channelCount")
                    val success = initializeEngine(sampleRate, channelCount)
                    Log.i(TAG, "🔄 Initialize result: $success")
                    result.success(success)
                }
                "shutdown" -> {
                    val success = shutdownEngine()
                    result.success(success)
                }
                "loadFile" -> {
                    val args = call.arguments as? Map<String, Any>
                    val filePath = args?.get("filePath") as? String
                    if (filePath != null) {
                        val success = loadFile(filePath)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "File path is required", null)
                    }
                }
                "loadUrl" -> {
                    val args = call.arguments as? Map<String, Any>
                    val url = args?.get("url") as? String
                    if (url != null) {
                        val success = loadUrl(url)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "URL is required", null)
                    }
                }
                "play" -> {
                    val success = play()
                    result.success(success)
                }
                "pause" -> {
                    val success = pause()
                    result.success(success)
                }
                "stop" -> {
                    val success = stop()
                    result.success(success)
                }
                "setVolume" -> {
                    val args = call.arguments as? Map<String, Any>
                    val volume = args?.get("volume") as? Double
                    if (volume != null) {
                        val success = setVolume(volume)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "Volume is required", null)
                    }
                }
                "getVolume" -> {
                    val volume = getVolume()
                    result.success(volume)
                }
                "isPlaying" -> {
                    val playing = isPlaying()
                    result.success(playing)
                }
                "isPaused" -> {
                    val paused = isPaused()
                    result.success(paused)
                }
                "getPosition" -> {
                    val position = getPosition()
                    result.success(position)
                }
                "getDuration" -> {
                    val duration = getDuration()
                    result.success(duration)
                }
                "setPosition" -> {
                    val args = call.arguments as? Map<String, Any>
                    val seconds = args?.get("seconds") as? Double
                    if (seconds != null) {
                        val success = setPosition(seconds)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "Position in seconds is required", null)
                    }
                }
                "getLastError" -> {
                    val error = getLastError()
                    result.success(error)
                }
                "runTests" -> {
                    val success = runTests()
                    result.success(success)
                }
                "getBassVersion" -> {
                    val version = getBassVersion()
                    result.success(version)
                }
                "getDeviceInfo" -> {
                    val deviceInfo = getDeviceInfo()
                    result.success(deviceInfo)
                }
                "playStream" -> {
                    val url = call.argument<String>("url")
                    if (url != null) {
                        Log.i(TAG, "🎵 Playing stream: $url")
                        val success = playUrl(url)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "URL is required", null)
                    }
                }
                "isInitialized" -> {
                    val initialized = isEngineInitialized()
                    result.success(initialized)
                }
                "getCurrentPosition" -> {
                    try {
                        val position = audioManager?.getCurrentPosition() ?: 0L
                        result.success(position.toInt())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting current position", e)
                        result.success(0)
                    }
                }
                "seek" -> {
                    val seconds = call.argument<Double>("seconds")
                    if (seconds != null) {
                        val success = audioManager?.seek((seconds * 1000).toLong()) ?: false
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "Seconds is required for seeking", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
        
        // Initialize audio manager
        initializeAudio()
    }
    
    private fun initializeAudio() {
        try {
            audioManager = BassAudioManager(this)
            // Don't auto-initialize here, let Flutter control initialization
            Log.i(TAG, "✅ Audio manager created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio manager creation error", e)
        }
    }
    
    private fun initializeEngine(sampleRate: Int, channelCount: Int): Boolean {
        return try {
            if (audioManager == null) {
                audioManager = BassAudioManager(this)
            }
            
            val success = audioManager!!.initialize(object : BassAudioManager.AudioCallback {
                override fun onStateChanged(state: BassAudioManager.AudioState) {
                    Log.d(TAG, "Audio state changed: $state")
                    
                    // Notify Flutter
                    val stateString = when (state) {
                        BassAudioManager.AudioState.IDLE -> "stopped"
                        BassAudioManager.AudioState.LOADING -> "loading"
                        BassAudioManager.AudioState.PLAYING -> "playing"
                        BassAudioManager.AudioState.PAUSED -> "paused"
                        BassAudioManager.AudioState.ERROR -> "error"
                    }
                    
                    runOnUiThread {
                        methodChannel.invokeMethod("onPlaybackStateChanged", mapOf("state" to stateString))
                    }
                }
                
                override fun onPositionChanged(position: Long) {
                    // Optional: send position updates to Flutter
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "Audio error: $error")
                    runOnUiThread {
                        methodChannel.invokeMethod("onError", mapOf("error" to error))
                    }
                }
            })
            
            if (success) {
                Log.i(TAG, "✅ Audio engine initialized successfully")
            } else {
                Log.e(TAG, "❌ Failed to initialize audio engine")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio engine initialization error", e)
            false
        }
    }
    
    private fun shutdownEngine(): Boolean {
        return try {
            audioManager?.release()
            audioManager = null
            Log.i(TAG, "✅ Audio engine shutdown successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio engine shutdown error", e)
            false
        }
    }
    
    private fun loadFile(filePath: String): Boolean {
        return audioManager?.loadFile(filePath) ?: false
    }
    
    private fun loadUrl(url: String): Boolean {
        return audioManager?.playUrl(url) ?: false
    }
    
    private fun play(): Boolean {
        return audioManager?.play() ?: false
    }
    
    private fun pause(): Boolean {
        return audioManager?.pause() ?: false
    }
    
    private fun stop(): Boolean {
        return audioManager?.stop() ?: false
    }
    
    private fun setVolume(volume: Double): Boolean {
        return audioManager?.setVolume(volume) ?: false
    }
    
    private fun getVolume(): Double {
        return audioManager?.getVolume() ?: 0.0
    }
    
    private fun isPlaying(): Boolean {
        return audioManager?.isPlaying() ?: false
    }
    
    private fun isPaused(): Boolean {
        return audioManager?.isPaused() ?: false
    }
    
    private fun getPosition(): Double {
        val positionMs = audioManager?.getCurrentPosition() ?: 0L
        return positionMs / 1000.0 // Convert milliseconds to seconds
    }
    
    private fun getDuration(): Double {
        val durationMs = audioManager?.getStreamLength() ?: 0L
        return durationMs / 1000.0 // Convert milliseconds to seconds
    }
    
    private fun setPosition(seconds: Double): Boolean {
        val positionMs = (seconds * 1000).toLong()
        return audioManager?.seek(positionMs) ?: false
    }
    
    private fun getLastError(): String {
        return audioManager?.getLastError() ?: "No error information available"
    }
    
    private fun runTests(): Boolean {
        return try {
            audioManager?.runTests() ?: false
            true
        } catch (e: Exception) {
            Log.e(TAG, "Test execution failed", e)
            false
        }
    }
    
    private fun getBassVersion(): String {
        return audioManager?.getBassVersion() ?: "Unknown"
    }
    
    private fun getDeviceInfo(): String {
        return audioManager?.getDeviceInfo() ?: "Unknown device"
    }
    
    private fun playUrl(url: String): Boolean {
        return audioManager?.playUrl(url) ?: false
    }
    
    private fun isEngineInitialized(): Boolean {
        return audioManager?.isInitialized() ?: false
    }
    
    override fun onPause() {
        super.onPause()
        audioManager?.onAppPause()
    }
    
    override fun onResume() {
        super.onResume()
        audioManager?.onAppResume()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        audioManager?.release()
    }
}
