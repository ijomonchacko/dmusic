package com.dmusic.android.dmusic

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.util.Log
import java.io.File
import java.security.MessageDigest

class MainActivity: FlutterActivity() {
    
    companion object {
        init {
            try {
                System.loadLibrary("bass")
                System.loadLibrary("bassflac") 
                System.loadLibrary("bass_ssl")
                System.loadLibrary("bass_fx")
                System.loadLibrary("native-lib")
                Log.i("MainActivity", "✅ All native libraries loaded")
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Failed to load native libraries: ${e.message}")
            }
        }
    }
    
    private val AUDIO_CHANNEL = "native_audio_engine"
    private val BASS_CHANNEL = "bass_audio_engine"
    private val SERVICE_CHANNEL = "music_service"
    private val METADATA_CHANNEL = "local_metadata"

    private var audioMethodChannel: MethodChannel? = null
    
    private var bassAudioManager: BassAudioManager? = null
    private var notificationManager: MusicNotificationManager? = null
    private lateinit var audioManager: AudioManager
    private var serviceIntent: Intent? = null
    private var bluetoothReceiver: BluetoothAudioReceiver? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize audio manager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Register Bluetooth audio receiver for car stereo and headphone support
        try {
            bluetoothReceiver = BluetoothAudioReceiver.register(this)
            Log.i("MainActivity", "✅ Bluetooth Audio Receiver registered")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Failed to register Bluetooth receiver: ${e.message}")
        }
        
        Log.i("MainActivity", "🚀 MainActivity created")
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        Log.i("MainActivity", "🔄 Configuring Flutter engine with audio channels")
        
        // Initialize BASS Audio Manager
        bassAudioManager = BassAudioManager(this)
        Log.i("MainActivity", "✅ BASS Audio Manager created")
        
        // Initialize notification manager
        notificationManager = MusicNotificationManager(this)
        Log.i("MainActivity", "✅ Music Notification Manager created")
        
        setupAudioChannels(flutterEngine)
    }
    
    private fun setupAudioChannels(flutterEngine: FlutterEngine) {
        try {
            // Setup Native Audio Engine channel (for Flutter integration)
            Log.i("MainActivity", "🔧 Setting up native_audio_engine channel")
            audioMethodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AUDIO_CHANNEL).also { channel ->
                PlaybackChannelBridge.attach(channel)
                channel.setMethodCallHandler { call, result ->
                    handleAudioChannelCall(call, result)
                }
            }
            
            // Setup BASS Audio Engine channel (for direct BASS integration)
            MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BASS_CHANNEL).setMethodCallHandler { call, result ->
                handleBassChannelCall(call, result)
            }
            
            // Setup Music Service channel (for background service integration)
            MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SERVICE_CHANNEL).setMethodCallHandler { call, result ->
                handleServiceChannelCall(call, result)
            }

            // Setup Local Metadata channel (for embedded tags/art)
            MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METADATA_CHANNEL).setMethodCallHandler { call, result ->
                handleMetadataChannelCall(call, result)
            }
            
            Log.i("MainActivity", "✅ Audio channels configured successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Failed to setup audio channels: ${e.message}")
        }
    }
    
    private fun handleAudioChannelCall(call: io.flutter.plugin.common.MethodCall, result: io.flutter.plugin.common.MethodChannel.Result) {
        Log.i("MainActivity", "🎵 Native audio call received: ${call.method}")
        
        when (call.method) {
            "initialize" -> {
                Log.i("MainActivity", "🔄 Initializing native audio engine")
                try {
                    val success = nativeInitialize()
                    Log.i("MainActivity", "✅ Native audio engine initialized: $success")
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native initialize error: ${e.message}")
                    result.success(false)
                }
            }
            
            "playStream" -> {
                val url = call.argument<String>("url")
                if (url != null) {
                    Log.i("MainActivity", "🎵 Playing stream: $url")
                    try {
                        val loadSuccess = nativeLoadUrl(url)
                        if (loadSuccess) {
                            val playSuccess = nativePlay()
                            Log.i("MainActivity", "🎵 Play result: $playSuccess")
                            result.success(playSuccess)
                        } else {
                            Log.e("MainActivity", "❌ Failed to load URL")
                            result.success(false)
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "❌ Native playStream error: ${e.message}")
                        result.success(false)
                    }
                } else {
                    Log.e("MainActivity", "❌ Invalid URL for playStream")
                    result.success(false)
                }
            }
            
            "stop" -> {
                Log.i("MainActivity", "⏹️ Stopping playback")
                try {
                    val success = nativeStop()
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native stop error: ${e.message}")
                    result.success(false)
                }
            }
            
            "pause" -> {
                Log.i("MainActivity", "⏸️ Pausing playback")
                try {
                    val success = nativePause()
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native pause error: ${e.message}")
                    result.success(false)
                }
            }
            
            "resume" -> {
                Log.i("MainActivity", "▶️ Resuming playback")
                try {
                    val success = nativePlay()
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native resume error: ${e.message}")
                    result.success(false)
                }
            }
            
            "getPosition" -> {
                try {
                    val position = nativeGetPosition() * 1000 // Convert to ms
                    Log.d("MainActivity", "📍 Position: ${position}ms")
                    result.success(position.toInt())
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native getPosition error: ${e.message}")
                    result.success(0)
                }
            }
            
            "getDuration" -> {
                try {
                    val duration = nativeGetDuration() * 1000 // Convert to ms
                    Log.d("MainActivity", "⏱️ Duration: ${duration}ms")
                    result.success(duration.toInt())
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native getDuration error: ${e.message}")
                    result.success(0)
                }
            }
            
            "seek" -> {
                val positionMs = call.argument<Int>("positionMs")?.toLong() ?: 0L
                Log.i("MainActivity", "⏩ Seeking to: ${positionMs}ms")
                try {
                    val positionSeconds = positionMs / 1000.0 // Convert to seconds
                    val success = nativeSetPosition(positionSeconds)
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native seek error: ${e.message}")
                    result.success(false)
                }
            }
            
            "setVolume" -> {
                val volume = call.argument<Double>("volume") ?: 1.0
                Log.i("MainActivity", "🔊 Setting volume: $volume")
                try {
                    val success = nativeSetVolume(volume)
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native setVolume error: ${e.message}")
                    result.success(false)
                }
            }
            
            "getVolume" -> {
                try {
                    val volume = nativeGetVolume()
                    result.success(volume)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native getVolume error: ${e.message}")
                    result.success(1.0)
                }
            }
            
            "isPlaying" -> {
                try {
                    val playing = nativeIsPlaying()
                    result.success(playing)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native isPlaying error: ${e.message}")
                    result.success(false)
                }
            }
            
            "isInitialized" -> {
                try {
                    val initialized = nativeIsInitialized()
                    Log.d("MainActivity", "🔍 Native isInitialized: $initialized")
                    result.success(initialized)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native isInitialized error: ${e.message}")
                    result.success(false)
                }
            }
            
            "loadFile" -> {
                val filePath = call.argument<String>("filePath")
                if (filePath != null) {
                    Log.i("MainActivity", "📁 Loading file: $filePath")
                    try {
                        val success = nativeLoadFile(filePath)
                        Log.i("MainActivity", "📁 Load file result: $success")
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "❌ Native loadFile error: ${e.message}")
                        result.success(false)
                    }
                } else {
                    Log.e("MainActivity", "❌ Invalid file path for loadFile")
                    result.success(false)
                }
            }
            
            "shutdown" -> {
                Log.i("MainActivity", "🛑 Shutting down native engine")
                try {
                    nativeShutdown()
                    result.success(true)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native shutdown error: ${e.message}")
                    result.success(false)
                }
            }
            
            "getLastError" -> {
                try {
                    val error = nativeGetLastError()
                    result.success(error)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native getLastError error: ${e.message}")
                    result.success("Failed to get error: ${e.message}")
                }
            }

            "initializeEqualizer" -> {
                val service = MusicPlaybackService.instance
                val success = service?.initializeEqualizerFromFlutter() ?: false
                Log.i("MainActivity", "🎛️ initializeEqualizer -> $success (serviceAttached=${service != null})")
                result.success(success)
            }

            "setEqualizerEnabled" -> {
                val enabled = (call.arguments as? Boolean) ?: false
                val service = MusicPlaybackService.instance
                val success = service?.setEqualizerEnabledFromFlutter(enabled) ?: false
                Log.i("MainActivity", "🎛️ setEqualizerEnabled($enabled) -> $success")
                result.success(success)
            }

            "setEqualizerBand" -> {
                val band = call.argument<Int>("band")
                val gain = call.argument<Number>("gain")?.toDouble()
                if (band == null || gain == null) {
                    Log.w("MainActivity", "🎛️ setEqualizerBand called with invalid arguments: band=$band gain=$gain")
                    result.success(false)
                    return
                }
                val service = MusicPlaybackService.instance
                val success = service?.setEqualizerBandFromFlutter(band, gain) ?: false
                Log.i("MainActivity", "🎛️ setEqualizerBand(band=$band, gain=$gain) -> $success")
                result.success(success)
            }

            "getEqualizerBand" -> {
                val band = call.arguments as? Int ?: call.argument<Int>("band")
                val service = MusicPlaybackService.instance
                val value = if (band != null) {
                    service?.getEqualizerBandFromFlutter(band) ?: 0.0
                } else {
                    0.0
                }
                Log.i("MainActivity", "🎛️ getEqualizerBand(band=$band) -> $value")
                result.success(value)
            }

            "resetEqualizer" -> {
                val service = MusicPlaybackService.instance
                val success = service?.resetEqualizerFromFlutter() ?: false
                Log.i("MainActivity", "🎛️ resetEqualizer -> $success")
                result.success(success)
            }

            "getEqualizerBandCount" -> {
                val service = MusicPlaybackService.instance
                val count = service?.getEqualizerBandCountFromFlutter() ?: 0
                Log.i("MainActivity", "🎛️ getEqualizerBandCount -> $count")
                result.success(count)
            }
            
            else -> {
                Log.w("MainActivity", "⚠️ Unknown method: ${call.method}")
                result.notImplemented()
            }
        }
    }
    
    private fun handleBassChannelCall(call: io.flutter.plugin.common.MethodCall, result: io.flutter.plugin.common.MethodChannel.Result) {
        Log.i("MainActivity", "🎛️ BASS audio call: ${call.method}")
        
        when (call.method) {
            "initialize" -> {
                val sampleRate = call.argument<Int>("sampleRate") ?: 44100
                val channelCount = call.argument<Int>("channelCount") ?: 2
                Log.i("MainActivity", "🔄 BASS initialize: $sampleRate Hz, $channelCount channels")
                try {
                    val success = nativeInitialize()
                    Log.i("MainActivity", "🔄 Native initialize result: $success")
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native initialize error: ${e.message}")
                    result.success(false)
                }
            }
            
            "loadUrl" -> {
                val url = call.argument<String>("url")
                if (url != null) {
                    Log.i("MainActivity", "🌐 BASS load URL: $url")
                    try {
                        val success = nativeLoadUrl(url)
                        Log.i("MainActivity", "🌐 Load URL result: $success")
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "❌ Native loadUrl error: ${e.message}")
                        result.success(false)
                    }
                } else {
                    result.success(false)
                }
            }
            
            "play" -> {
                Log.i("MainActivity", "▶️ BASS play")
                try {
                    val success = nativePlay()
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native play error: ${e.message}")
                    result.success(false)
                }
            }
            
            "pause" -> {
                Log.i("MainActivity", "⏸️ BASS pause")
                try {
                    val success = nativePause()
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native pause error: ${e.message}")
                    result.success(false)
                }
            }
            
            "stop" -> {
                Log.i("MainActivity", "⏹️ BASS stop")
                try {
                    val success = nativeStop()
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native stop error: ${e.message}")
                    result.success(false)
                }
            }
            
            "getPosition" -> {
                try {
                    val position = nativeGetPosition() * 1000 // Convert to ms
                    Log.d("MainActivity", "📍 Position: ${position}ms")
                    result.success(position.toInt())
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native getPosition error: ${e.message}")
                    result.success(0)
                }
            }
            
            "getDuration" -> {
                try {
                    val duration = nativeGetDuration() * 1000 // Convert to ms
                    Log.d("MainActivity", "⏱️ Duration: ${duration}ms")
                    result.success(duration.toInt())
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native getDuration error: ${e.message}")
                    result.success(0)
                }
            }
            
            "seek" -> {
                val positionMs = call.argument<Int>("positionMs")?.toLong() ?: 0L
                Log.i("MainActivity", "⏩ Seeking to: ${positionMs}ms")
                try {
                    val positionSeconds = positionMs / 1000.0 // Convert to seconds
                    val success = nativeSetPosition(positionSeconds)
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native seek error: ${e.message}")
                    result.success(false)
                }
            }
            
            "setVolume" -> {
                val volume = call.argument<Double>("volume") ?: 1.0
                Log.i("MainActivity", "🔊 Setting volume: $volume")
                try {
                    val success = nativeSetVolume(volume)
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native setVolume error: ${e.message}")
                    result.success(false)
                }
            }
            
            "getVolume" -> {
                try {
                    val volume = nativeGetVolume()
                    result.success(volume)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native getVolume error: ${e.message}")
                    result.success(1.0)
                }
            }
            
            "setPosition" -> {
                val seconds = call.argument<Double>("seconds") ?: 0.0
                Log.i("MainActivity", "⏩ Seeking to position: $seconds seconds")
                try {
                    val success = nativeSetPosition(seconds)
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native setPosition error: ${e.message}")
                    result.success(false)
                }
            }
            
            "isInitialized" -> {
                try {
                    val initialized = nativeIsInitialized()
                    result.success(initialized)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native isInitialized error: ${e.message}")
                    result.success(false)
                }
            }
            
            "isPaused" -> {
                try {
                    val paused = nativeIsPaused()
                    result.success(paused)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native isPaused error: ${e.message}")
                    result.success(false)
                }
            }
            
            "isPlaying" -> {
                try {
                    val playing = nativeIsPlaying()
                    result.success(playing)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native isPlaying error: ${e.message}")
                    result.success(false)
                }
            }
            
            "loadFile" -> {
                val filePath = call.argument<String>("filePath")
                if (filePath != null) {
                    Log.i("MainActivity", "📁 BASS load file: $filePath")
                    try {
                        val success = nativeLoadFile(filePath)
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "❌ Native loadFile error: ${e.message}")
                        result.success(false)
                    }
                } else {
                    result.success(false)
                }
            }
            
            "getLastError" -> {
                try {
                    val error = nativeGetLastError()
                    result.success(error)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native getLastError error: ${e.message}")
                    result.success("Failed to get error: ${e.message}")
                }
            }
            
            "shutdown" -> {
                Log.i("MainActivity", "🛑 BASS shutdown")
                try {
                    nativeShutdown()
                    result.success(true)
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Native shutdown error: ${e.message}")
                    result.success(false)
                }
            }
            
            else -> {
                Log.w("MainActivity", "⚠️ Unknown BASS method: ${call.method}")
                result.notImplemented()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i("MainActivity", "🔄 MainActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        Log.i("MainActivity", "⏸️ MainActivity paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity", "🛑 MainActivity destroyed")
        
        // Stop service if running
        serviceIntent?.let { intent ->
            stopService(intent)
            serviceIntent = null
        }
        
        // Unregister Bluetooth receiver
        bluetoothReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.i("MainActivity", "✅ Bluetooth receiver unregistered")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to unregister Bluetooth receiver: ${e.message}")
            }
        }
        bluetoothReceiver = null
        
        audioMethodChannel?.let { PlaybackChannelBridge.detach(it) }
        audioMethodChannel = null
        bassAudioManager?.release()
        bassAudioManager = null
        notificationManager?.release()
        notificationManager = null
    }
    
    private fun handleServiceChannelCall(call: io.flutter.plugin.common.MethodCall, result: io.flutter.plugin.common.MethodChannel.Result) {
        Log.i("MainActivity", "🎛️ Music service call: ${call.method}")
        
        when (call.method) {
            "startService" -> {
                val url = call.argument<String>("url")
                val filePath = call.argument<String>("filePath")
                val title = call.argument<String>("title") ?: "Unknown Track"
                val artist = call.argument<String>("artist") ?: "Unknown Artist"
                val album = call.argument<String>("album") ?: "Unknown Album"
                val albumArtUrl = call.argument<String>("albumArtUrl")
                val duration = call.argument<Number>("duration")?.toLong() ?: 0L

                if (filePath != null) {
                    startMusicService(url = null, filePath = filePath, title = title, artist = artist, album = album, albumArtUrl = albumArtUrl, duration = duration)
                    result.success(true)
                } else if (url != null) {
                    startMusicService(url = url, filePath = null, title = title, artist = artist, album = album, albumArtUrl = albumArtUrl, duration = duration)
                    result.success(true)
                } else {
                    Log.e("MainActivity", "❌ Missing URL or filePath for startService")
                    result.success(false)
                }
            }
            "pause" -> {
                val paused = MusicPlaybackService.instance?.pauseFromFlutter() ?: false
                result.success(paused)
            }
            "resume" -> {
                val resumed = MusicPlaybackService.instance?.resumeFromFlutter() ?: false
                result.success(resumed)
            }
            "stopPlayback" -> {
                val stopped = MusicPlaybackService.instance?.stopFromFlutter() ?: false
                result.success(stopped)
            }
            "stopPlaybackKeepService" -> {
                val stopped = MusicPlaybackService.instance?.stopPlaybackKeepServiceFromFlutter() ?: false
                result.success(stopped)
            }
            "seekTo" -> {
                val position = call.argument<Number>("positionMs")?.toLong() ?: 0L
                val success = MusicPlaybackService.instance?.seekFromFlutter(position) ?: false
                result.success(success)
            }
            "getPosition" -> {
                val position = MusicPlaybackService.instance?.getCurrentPositionMs() ?: 0L
                result.success(position)
            }
            "getDuration" -> {
                val duration = MusicPlaybackService.instance?.getCurrentDurationMs() ?: 0L
                result.success(duration)
            }
            "isPlaying" -> {
                val playing = MusicPlaybackService.instance?.isCurrentlyPlaying() ?: false
                result.success(playing)
            }
            "stopService" -> {
                stopMusicService()
                result.success(true)
            }

            "holdNetworkLockForStream" -> {
                val duration = call.argument<Number>("durationMs")?.toLong() ?: 8000L
                val service = MusicPlaybackService.instance
                if (service != null) {
                    service.holdNetworkLockForStream(duration)
                    result.success(true)
                } else {
                    result.success(false)
                }
            }
            
            "syncPlaybackState" -> {
                val service = MusicPlaybackService.instance
                if (service != null) {
                    service.syncPlaybackStateFromFlutter()
                    result.success(true)
                } else {
                    result.success(false)
                }
            }
            
            "recoverPlayback" -> {
                val service = MusicPlaybackService.instance
                if (service != null) {
                    service.recoverPlaybackFromFlutter()
                    result.success(true)
                } else {
                    result.success(false)
                }
            }
            
            "updateNotification" -> {
                val title = call.argument<String>("title") ?: ""
                val artist = call.argument<String>("artist") ?: ""
                val album = call.argument<String>("album") ?: ""
                val albumArtUrl = call.argument<String>("albumArtUrl")
                val isPlaying = call.argument<Boolean>("isPlaying") ?: false
                val duration = call.argument<Number>("duration")?.toLong() ?: 0L
                val position = call.argument<Number>("position")?.toLong() ?: 0L
                
                notificationManager?.updateNotification(
                    title, artist, album, albumArtUrl, isPlaying, duration, position
                )
                result.success(true)
            }
            
            else -> {
                Log.w("MainActivity", "⚠️ Unknown service method: ${call.method}")
                result.notImplemented()
            }
        }
    }

    private fun handleMetadataChannelCall(call: io.flutter.plugin.common.MethodCall, result: io.flutter.plugin.common.MethodChannel.Result) {
        when (call.method) {
            "getMetadata" -> {
                val filePath = call.argument<String>("filePath")
                val includeArtwork = call.argument<Boolean>("includeArtwork") ?: true
                if (filePath.isNullOrEmpty()) {
                    result.success(null)
                    return
                }
                try {
                    result.success(readLocalMetadata(filePath, includeArtwork))
                } catch (e: Exception) {
                    Log.e("MainActivity", "Local metadata read failed: ${e.message}")
                    result.success(null)
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun readLocalMetadata(filePath: String, includeArtwork: Boolean): Map<String, Any?> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val bitDepth = extractOptionalIntMetadata(retriever, "METADATA_KEY_BITS_PER_SAMPLE")
            val sampleRate = extractOptionalIntMetadata(retriever, "METADATA_KEY_SAMPLERATE")
            val isHiRes = (bitDepth ?: 0) >= 24 || (sampleRate ?: 0) >= 96000
            val picture = if (includeArtwork) retriever.embeddedPicture else null
            val albumArtPath = if (picture != null && picture.isNotEmpty()) {
                saveAlbumArt(filePath, picture)
            } else {
                null
            }

            return mapOf(
                "title" to title,
                "artist" to artist,
                "album" to album,
                "genre" to genre,
                "durationMs" to durationMs,
                "bitDepth" to bitDepth,
                "sampleRate" to sampleRate,
                "isHiRes" to isHiRes,
                "albumArtPath" to albumArtPath
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun extractOptionalIntMetadata(
        retriever: MediaMetadataRetriever,
        fieldName: String,
    ): Int? {
        return try {
            val keyField = MediaMetadataRetriever::class.java.getField(fieldName)
            val key = keyField.getInt(null)
            retriever.extractMetadata(key)?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun saveAlbumArt(filePath: String, bytes: ByteArray): String? {
        return try {
            val hash = md5(filePath)
            val outputFile = File(applicationContext.cacheDir, "cover_${hash}.jpg")
            if (!outputFile.exists() || outputFile.length() == 0L) {
                outputFile.writeBytes(bytes)
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e("MainActivity", "Album art cache write failed: ${e.message}")
            null
        }
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    private fun startMusicService(url: String?, filePath: String?, title: String, artist: String, album: String, albumArtUrl: String?, duration: Long) {
        try {
            Log.i("MainActivity", "🚀 Starting music service for: $title")
            
            serviceIntent = Intent(this, MusicPlaybackService::class.java).apply {
                action = if (filePath != null) "PLAY_FILE" else "PLAY_STREAM"
                if (filePath != null) {
                    putExtra("filePath", filePath)
                } else if (url != null) {
                    putExtra("url", url)
                }
                putExtra("title", title)
                putExtra("artist", artist)
                putExtra("album", album)
                putExtra("albumArtUrl", albumArtUrl)
                putExtra("duration", duration)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent!!)
            } else {
                startService(serviceIntent)
            }
            Log.i("MainActivity", "✅ Music service started")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error starting music service", e)
        }
    }
    
    private fun stopMusicService() {
        try {
            serviceIntent?.let { intent ->
                stopService(intent)
                serviceIntent = null
                Log.i("MainActivity", "✅ Music service stopped")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error stopping music service", e)
        }
    }
    
    // Native method declarations
    private external fun nativeInitialize(): Boolean
    private external fun nativeLoadUrl(url: String): Boolean
    private external fun nativeLoadFile(filePath: String): Boolean
    private external fun nativePlay(): Boolean
    private external fun nativePause(): Boolean
    private external fun nativeStop(): Boolean
    private external fun nativeSetVolume(volume: Double): Boolean
    private external fun nativeGetVolume(): Double
    private external fun nativeGetPosition(): Double
    private external fun nativeGetDuration(): Double
    private external fun nativeSetPosition(seconds: Double): Boolean
    private external fun nativeIsPlaying(): Boolean
    private external fun nativeIsPaused(): Boolean
    private external fun nativeIsInitialized(): Boolean
    private external fun nativeGetLastError(): String
    private external fun nativeShutdown(): Unit
    private external fun nativeRelease(): Boolean
}