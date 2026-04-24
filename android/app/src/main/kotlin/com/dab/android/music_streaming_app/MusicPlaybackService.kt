package com.dmusic.android.dmusic

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlin.math.abs
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

/**
 * Background music playback service
 * Manages audio playback, notifications, and media session
 */
class MusicPlaybackService : Service() {
    
    companion object {
        private const val TAG = "MusicPlaybackService"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var instance: MusicPlaybackService? = null
    }
    
    private val binder = MusicBinder()
    private var bassAudioManager: BassAudioManager? = null
    private var notificationManager: MusicNotificationManager? = null
    private var serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.i(TAG, "🔊 Audio focus change: $focusChange isPlaying=$isPlaying pausedByFocusLoss=$pausedByFocusLoss")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                bassAudioManager?.setVolume(0.3)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocusLoss = true
                pause(true)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocusLoss = true
                pause(true)
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                bassAudioManager?.setVolume(1.0)
                if (pausedByFocusLoss && !isPlaying && currentTitle.isNotEmpty()) {
                    pausedByFocusLoss = false
                    resume()
                }
            }
        }
    }
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var pausedByFocusLoss = false
    
    // Current track state
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentAlbum: String = ""
    private var currentAlbumArtUrl: String? = null
    private var currentStreamUrl: String? = null
    private var currentRawStreamUrl: String? = null
    private var rawStreamFallbackAttempted = false
    private var isPlaying = false
    private var currentDuration = 0L
    private var currentPosition = 0L
    private var pendingManualStop = false
    private var keepAliveForNextTrack = false
    private var suppressNextIdleHandling = false
    private var playbackStartRetries = 0
    private val maxPlaybackStartRetries = 1
    private var externalNetworkLockHolders = 0
    private var externalNetworkLockReleaseJob: Job? = null
    
    // Position tracking
    private var positionTrackingJob: Job? = null
    private var playbackStartJob: Job? = null
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 MusicPlaybackService created")
        instance = this
        
        // Initialize BASS audio manager
        bassAudioManager = BassAudioManager(this).apply {
            initialize(object : BassAudioManager.AudioCallback {
                override fun onStateChanged(state: BassAudioManager.AudioState) {
                    Log.i(TAG, "🎵 Audio state changed: $state")
                    when (state) {
                        BassAudioManager.AudioState.LOADING -> {
                            isPlaying = false
                            PlaybackChannelBridge.sendState("loading")
                        }
                        BassAudioManager.AudioState.PLAYING -> {
                            isPlaying = true
                            playbackStartRetries = 0
                            startPositionTracking()
                            updateNotification()
                            PlaybackChannelBridge.sendState("playing")
                            // Keep Android Auto in sync with native engine state
                            updateAndroidAutoPlaybackState()
                            cancelPlaybackStartWatcher()
                        }
                        BassAudioManager.AudioState.PAUSED -> {
                            Log.i(
                                TAG,
                                "⏸️ Native pause callback (focusLoss=$pausedByFocusLoss keepAlive=$keepAliveForNextTrack pendingManualStop=$pendingManualStop)"
                            )
                            isPlaying = false
                            stopPositionTracking()
                            updateNotification()
                            PlaybackChannelBridge.sendState("paused")
                            // Reflect pause to Android Auto UI
                            updateAndroidAutoPlaybackState()
                            cancelPlaybackStartWatcher()
                            
                            // Auto-resume if paused unexpectedly during track transition
                            if (!pausedByFocusLoss && !pendingManualStop && keepAliveForNextTrack) {
                                Log.w(TAG, "⚠️ Unexpected pause during track transition, auto-resuming")
                                serviceScope.launch {
                                    delay(500)
                                    if (!isPlaying && !pausedByFocusLoss) {
                                        Log.i(TAG, "🔄 Auto-resuming from unexpected pause")
                                        val resumed = bassAudioManager?.play() ?: false
                                        if (resumed) {
                                            beginPlaybackStartWatcher()
                                        }
                                    }
                                }
                            }
                        }
                        BassAudioManager.AudioState.IDLE -> {
                            val manualStop = pendingManualStop
                            pendingManualStop = false
                            val skipIdleHandling = suppressNextIdleHandling
                            if (skipIdleHandling) {
                                suppressNextIdleHandling = false
                            }

                            // IMPROVED COMPLETION DETECTION:
                            // 1. Increased tolerance to 5000ms (5s) to handle slower updates when screen is off
                            // 2. Added explicit check for natural completion (no manual stop, no focus loss)
                            val completedWithDuration = currentDuration > 0 && currentPosition > 0 &&
                                abs(currentDuration - currentPosition) <= 5000
                            val completedWithoutDuration = currentDuration == 0L && currentPosition >= 5000
                            
                            // If we didn't manually stop and didn't lose focus, treat as completion attempt
                            // This ensures we don't miss "completed" events due to strict position checks
                            val naturalfinish = !manualStop && !pausedByFocusLoss && !skipIdleHandling
                            
                            // Combine accurate position check with intent check
                            // If widely missed duration but natural completion, still consider it likely completed
                            val likelyCompleted = naturalfinish && (completedWithDuration || completedWithoutDuration || currentPosition > 0)

                            Log.i(
                                TAG,
                                "🪫 Native idle state manual=$manualStop skip=$skipIdleHandling completed=$likelyCompleted (dur=$currentDuration pos=$currentPosition)"
                            )

                            isPlaying = false
                            stopPositionTracking()
                            
                            // CRITICAL FIX: Keep service alive if we expect a next track (autoplay)
                            // This prevents Android from killing the service in the split second between tracks
                            val tempKeepAlive = likelyCompleted && !keepAliveForNextTrack
                            val suppressCleanup = keepAliveForNextTrack || skipIdleHandling || tempKeepAlive
                            
                            if (!suppressCleanup) {
                                stopForeground(true)
                                abandonAudioFocus()
                                releaseLocks()
                            } else {
                                Log.i(TAG, "♻️ Keeping service alive for next track (temp=$tempKeepAlive)")
                                
                                // Safety: If we're keeping it alive for autoplay, but next track never comes,
                                // we must eventually clean up to avoid battery drain.
                                if (tempKeepAlive) {
                                    serviceScope.launch {
                                        delay(30000) // 30 second grace period for Flutter to send next track
                                        if (!isPlaying && !keepAliveForNextTrack && instance != null) {
                                            Log.i(TAG, "⏰ Autoplay timeout - cleaning up service")
                                            stopForeground(true)
                                            abandonAudioFocus()
                                            releaseLocks()
                                            // Notify Flutter we've fully stopped
                                            PlaybackChannelBridge.sendState("stopped")
                                        }
                                    }
                                }
                            }

                            if (!skipIdleHandling) {
                                if (likelyCompleted) {
                                    Log.i(TAG, "🎶 Playback completed, notifying Flutter queue")
                                    PlaybackChannelBridge.sendState("completed")
                                } else {
                                    PlaybackChannelBridge.sendState("stopped")
                                }
                            } else {
                                Log.i(TAG, "⏯️ Idle notification suppressed (internal restart)")
                            }
                            cancelPlaybackStartWatcher()
                            keepAliveForNextTrack = false
                            // Update Android Auto when playback stops/finishes
                            updateAndroidAutoPlaybackState()
                        }
                        BassAudioManager.AudioState.ERROR -> {
                            if (tryRawStreamFallback("native error state")) {
                                Log.w(TAG, "⚠️ Raw URL fallback scheduled after native error state")
                                return
                            }
                            Log.e(TAG, "❌ Audio engine error")
                            isPlaying = false
                            stopPositionTracking()
                            abandonAudioFocus()
                            releaseLocks()
                            PlaybackChannelBridge.sendState("error")
                            cancelPlaybackStartWatcher()
                            keepAliveForNextTrack = false
                            // Surface error state to Android Auto
                            updateAndroidAutoPlaybackState()
                        }
                        else -> {}
                    }
                }
                
                override fun onPositionChanged(position: Long) {
                    currentPosition = position
                    if (currentDuration <= 0L) {
                        val streamLength = bassAudioManager?.getStreamLength() ?: 0L
                        if (streamLength > 0L) {
                            currentDuration = streamLength
                            updateAndroidAutoMetadata()
                            updateNotification()
                        }
                    }
                    notificationManager?.updatePosition(position, currentDuration)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "❌ Audio error: $error")
                    if (tryRawStreamFallback("audio callback error: $error")) {
                        Log.w(TAG, "⚠️ Raw URL fallback scheduled after callback error")
                        return
                    }
                    PlaybackChannelBridge.sendError(error)
                }
            })
        }

        bassAudioManager?.initializeEqualizer()
        Log.i(TAG, "✅ Equalizer pre-initialized with engine")
        
        // Initialize notification manager
        notificationManager = MusicNotificationManager(this)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initializeLocks()
        
        Log.i(TAG, "✅ MusicPlaybackService initialized")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "🔄 onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            "PLAY_STREAM" -> {
                val url = intent.getStringExtra("url")
                val title = intent.getStringExtra("title") ?: "Unknown Track"
                val artist = intent.getStringExtra("artist") ?: "Unknown Artist"
                val album = intent.getStringExtra("album") ?: "Unknown Album"
                val albumArtUrl = intent.getStringExtra("albumArtUrl")
                val duration = intent.getLongExtra("duration", 0)
                
                Log.i(TAG, "📥 PLAY_STREAM received:")
                Log.i(TAG, "   Title: $title, Artist: $artist")
                Log.i(TAG, "   Album Art URL: ${albumArtUrl?.take(80) ?: "NULL"}")
                
                if (url != null) {
                    playStream(url, title, artist, album, albumArtUrl, duration)
                }
            }
            "PLAY_FILE" -> {
                val filePath = intent.getStringExtra("filePath")
                val title = intent.getStringExtra("title") ?: "Unknown Track"
                val artist = intent.getStringExtra("artist") ?: "Unknown Artist"
                val album = intent.getStringExtra("album") ?: "Unknown Album"
                val albumArtUrl = intent.getStringExtra("albumArtUrl")
                val duration = intent.getLongExtra("duration", 0)

                Log.i(TAG, "📥 PLAY_FILE received: $filePath")
                if (filePath != null) {
                    playFile(filePath, title, artist, album, albumArtUrl, duration)
                }
            }
            
            "PAUSE" -> pause()
            "RESUME" -> resume()
            "STOP" -> stop()
            "SEEK" -> {
                val position = intent.getLongExtra("position", 0)
                seek(position)
            }
            
            // Handle notification actions
            "com.dab.music.PLAY_PAUSE" -> togglePlayPause()
            "com.dab.music.NEXT" -> next()
            "com.dab.music.PREVIOUS" -> previous()
            "com.dab.music.STOP" -> stop()
            "com.dab.music.SEEK" -> {
                val position = intent.getLongExtra("position", 0)
                seek(position)
            }
            
            // Handle album art loaded - update foreground notification
            "UPDATE_NOTIFICATION" -> {
                Log.i(TAG, "📷 Updating foreground notification with new album art")
                updateForegroundNotification()
            }
        }
        
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "🔗 Service bound")
        return binder
    }
    
    override fun onDestroy() {
        Log.i(TAG, "🛑 MusicPlaybackService destroyed")
        
        stopPositionTracking()
        bassAudioManager?.release()
        notificationManager?.release()
        abandonAudioFocus()
        releaseLocks()
        serviceScope.cancel()
        instance = null
        
        super.onDestroy()
    }
    
    /**
     * Update Android Auto metadata and playback state
     */
    private fun updateAndroidAutoMetadata() {
        try {
            AutoMediaBrowserService.instance?.updateMetadata(
                currentTitle,
                currentArtist,
                currentAlbum,
                currentAlbumArtUrl,
                currentDuration
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update Android Auto metadata", e)
        }
    }
    
    /**
     * Update Android Auto playback state
     */
    private fun updateAndroidAutoPlaybackState() {
        try {
            AutoMediaBrowserService.instance?.updatePlaybackState(
                isPlaying,
                currentPosition,
                currentDuration
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update Android Auto playback state", e)
        }
    }

    private fun prepareCloudStreamUrl(rawUrl: String): String {
        return try {
            val parsed = Uri.parse(rawUrl)
            val sessionToken = parsed.getQueryParameter("session")
            val host = parsed.host

            if (sessionToken.isNullOrBlank() || host.isNullOrBlank()) {
                rawUrl
            } else {
                val safeToken = sanitizeHeaderToken(sessionToken)
                configureCloudAuthCookies(parsed, safeToken)
                val normalizedUrl = sanitizeUrlForHeaderPayload(rawUrl)
                val headerPayload = buildString {
                    append(normalizedUrl)
                    append("\r\n")
                    append("Cookie: session=")
                    append(safeToken)
                    append("\r\n")
                    append("Authorization: Bearer ")
                    append(safeToken)
                    append("\r\n")
                    append("User-Agent: joehacks(v1)")
                    append("\r\n")
                    append("Accept: */*")
                    append("\r\n")
                    append("Connection: keep-alive")
                    append("\r\n")
                    append("\r\n")
                }
                Log.i(TAG, "🔐 Cloud stream headers prepared for host: $host")
                headerPayload
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not prepare cloud stream auth, using raw URL", e)
            rawUrl
        }
    }

    private fun sanitizeHeaderToken(token: String): String {
        return token.replace("\r", "").replace("\n", "")
    }

    private fun sanitizeUrlForHeaderPayload(url: String): String {
        return url.replace("\r", "").replace("\n", "")
    }

    private fun configureCloudAuthCookies(parsedUrl: Uri, safeToken: String) {
        try {
            val host = parsedUrl.host ?: return
            val scheme = parsedUrl.scheme ?: "https"
            val baseUri = URI("$scheme://$host")

            val cookieManager = CookieManager().apply {
                setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            }

            cookieManager.cookieStore.add(
                baseUri,
                HttpCookie("session", safeToken).apply {
                    path = "/"
                    domain = host
                    secure = scheme.equals("https", ignoreCase = true)
                },
            )

            CookieHandler.setDefault(cookieManager)
            Log.d(TAG, "🍪 Primed CookieHandler for cloud host: $host")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to prime cloud auth cookies", e)
        }
    }

    private fun isHeaderPayload(value: String): Boolean {
        return value.contains("\r\n")
    }

    private fun shouldTryRawStreamFallback(): Boolean {
        val rawUrl = currentRawStreamUrl
        val preparedUrl = currentStreamUrl
        return !rawStreamFallbackAttempted &&
            !rawUrl.isNullOrBlank() &&
            !preparedUrl.isNullOrBlank() &&
            isHeaderPayload(preparedUrl)
    }

    private fun tryRawStreamFallback(reason: String): Boolean {
        if (!shouldTryRawStreamFallback()) {
            return false
        }

        val rawUrl = currentRawStreamUrl ?: return false
        rawStreamFallbackAttempted = true
        Log.w(TAG, "⚠️ Falling back to raw signed cloud URL ($reason)")

        serviceScope.launch {
            suppressNextIdleHandling = true
            keepAliveForNextTrack = true
            pendingManualStop = false

            PlaybackChannelBridge.sendState("loading")
            bassAudioManager?.stop()
            delay(200)

            currentStreamUrl = rawUrl
            val restarted = bassAudioManager?.playUrl(rawUrl) == true
            if (!restarted) {
                val fallbackError = bassAudioManager?.getLastError() ?: "Raw URL fallback failed"
                finalizePlaybackStartFailure(fallbackError)
                return@launch
            }

            val resumed = bassAudioManager?.play() ?: false
            if (!resumed) {
                val fallbackError = bassAudioManager?.getLastError() ?: "Raw URL fallback resume failed"
                finalizePlaybackStartFailure(fallbackError)
                return@launch
            }

            keepAliveForNextTrack = false
            suppressNextIdleHandling = false
            beginPlaybackStartWatcher()
        }

        return true
    }
    
    /**
     * Play stream with track information
     */
    fun playStream(url: String, title: String, artist: String, album: String, albumArtUrl: String?, duration: Long) {
        Log.i(TAG, "🎵 Playing stream: $title by $artist (art: ${albumArtUrl?.take(50)}...)")
        val playbackUrl = prepareCloudStreamUrl(url)
        
        keepAliveForNextTrack = false
        playbackStartRetries = 0
        rawStreamFallbackAttempted = false
        Log.i(TAG, "🎶 Reset playback state keepAlive=$keepAliveForNextTrack pendingStop=$pendingManualStop")
        
        // CRITICAL: Clear notification album art cache BEFORE updating track info
        // This ensures the old track's art is never shown for the new track
        notificationManager?.clearAlbumArtCache()
        
        // Update current track info
        currentRawStreamUrl = url
        currentStreamUrl = playbackUrl
        currentTitle = title
        currentArtist = artist
        currentAlbum = album
        currentAlbumArtUrl = albumArtUrl
        currentDuration = duration
        currentPosition = 0
        pausedByFocusLoss = false

        // Enter foreground immediately to satisfy Android's FGS startup deadline,
        // even if stream loading fails right after this point.
        startForegroundWithNotification()
        
        if (!requestAudioFocus()) {
            Log.w(TAG, "⚠️ Audio focus not granted, playback may be interrupted")
        }
        acquireLocks()

        PlaybackChannelBridge.sendState("loading")
        
        // Update Android Auto metadata
        updateAndroidAutoMetadata()

        // Play via BASS
        var started = bassAudioManager?.playUrl(playbackUrl) ?: false
        if (!started && playbackUrl != url) {
            Log.w(TAG, "⚠️ Header-based cloud stream failed immediately, retrying with raw signed URL")
            rawStreamFallbackAttempted = true
            currentStreamUrl = url
            started = bassAudioManager?.playUrl(url) ?: false
        }
        if (!started) {
            val lastError = bassAudioManager?.getLastError() ?: "Unknown error"
            Log.e(TAG, "❌ Failed to start playback via BASS: $lastError")
            PlaybackChannelBridge.sendError(lastError)
            PlaybackChannelBridge.sendState("error")
            stopForeground(true)
            stopSelf()
            return
        }

        val autoPlayResult = bassAudioManager?.play() ?: false
        if (!autoPlayResult) {
            val lastError = bassAudioManager?.getLastError() ?: "Unknown error"
            Log.w(TAG, "⚠️ play() did not immediately report success after playUrl: $lastError")
            PlaybackChannelBridge.sendError(lastError)
        }

        beginPlaybackStartWatcher()
    }

    /**
     * Play local file with track information
     */
    fun playFile(filePath: String, title: String, artist: String, album: String, albumArtUrl: String?, duration: Long) {
        Log.i(TAG, "🎵 Playing local file: $title by $artist (path: ${filePath.take(60)})")

        keepAliveForNextTrack = false
        playbackStartRetries = 0
        rawStreamFallbackAttempted = false
        Log.i(TAG, "🎶 Reset playback state keepAlive=$keepAliveForNextTrack pendingStop=$pendingManualStop")

        notificationManager?.clearAlbumArtCache()

        currentStreamUrl = filePath
        currentRawStreamUrl = null
        currentTitle = title
        currentArtist = artist
        currentAlbum = album
        currentAlbumArtUrl = albumArtUrl
        currentDuration = duration
        currentPosition = 0
        pausedByFocusLoss = false

        // Enter foreground immediately to satisfy Android's FGS startup deadline,
        // even if file loading fails right after this point.
        startForegroundWithNotification()

        if (!requestAudioFocus()) {
            Log.w(TAG, "⚠️ Audio focus not granted, playback may be interrupted")
        }
        acquireLocks()

        PlaybackChannelBridge.sendState("loading")
        updateAndroidAutoMetadata()

        val started = bassAudioManager?.playFile(filePath) ?: false
        if (!started) {
            val lastError = bassAudioManager?.getLastError() ?: "Unknown error"
            Log.e(TAG, "❌ Failed to start local playback via BASS: $lastError")
            PlaybackChannelBridge.sendError(lastError)
            PlaybackChannelBridge.sendState("error")
            stopForeground(true)
            stopSelf()
            return
        }

        val autoPlayResult = bassAudioManager?.play() ?: false
        if (!autoPlayResult) {
            val lastError = bassAudioManager?.getLastError() ?: "Unknown error"
            Log.w(TAG, "⚠️ play() did not immediately report success after playFile: $lastError")
            PlaybackChannelBridge.sendError(lastError)
        }

        beginPlaybackStartWatcher()
    }
    
    private fun startForegroundWithNotification() {
        try {
            // CRITICAL FIX: First update the notification with track info
            // This starts async album art loading
            notificationManager?.updateNotification(
                currentTitle,
                currentArtist,
                currentAlbum,
                currentAlbumArtUrl,
                isPlaying,
                currentDuration,
                currentPosition
            )
            
            // CRITICAL FIX: Use the notification from the manager, NOT a new placeholder
            // This ensures we use whatever album art is currently available (even if null)
            // The manager will call notify() again when album art loads
            val notification = notificationManager?.buildForegroundNotification()
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification)
                Log.i(TAG, "✅ Started foreground service with manager notification")
            } else {
                // Fallback: only if notification manager fails
                val fallbackNotification = androidx.core.app.NotificationCompat.Builder(this, "music_playback")
                    .setContentTitle(currentTitle)
                    .setContentText(currentArtist)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setOngoing(true)
                    .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                startForeground(NOTIFICATION_ID, fallbackNotification)
                Log.w(TAG, "⚠️ Started foreground with fallback notification")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting foreground service", e)
        }
    }
    
    private fun updateNotification() {
        notificationManager?.updateNotification(
            currentTitle,
            currentArtist,
            currentAlbum,
            currentAlbumArtUrl,
            isPlaying,
            currentDuration,
            currentPosition
        )
    }
    
    /**
     * Update foreground notification with latest album art
     * Called when async album art loading completes
     */
    private fun updateForegroundNotification() {
        try {
            val notification = notificationManager?.buildForegroundNotification()
            if (notification != null) {
                // Update the foreground notification to show the new album art
                startForeground(NOTIFICATION_ID, notification)
                Log.i(TAG, "✅ Foreground notification updated with album art")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating foreground notification", e)
        }
    }
    
    private fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            resume()
        }
    }
    
    private fun pause(fromFocusLoss: Boolean = false): Boolean {
        Log.i(TAG, "⏸️ Pausing playback (isPlaying=$isPlaying fromFocus=$fromFocusLoss)")
        
        // Check NATIVE state too - internal flag might be out of sync
        val nativelyPlaying = bassAudioManager?.isPlaying() ?: false
        
        if (!isPlaying && !nativelyPlaying && !fromFocusLoss) {
            Log.w(TAG, "⚠️ Pause called but not currently playing (native=$nativelyPlaying)")
            return false
        }
        
        val paused = bassAudioManager?.pause() ?: false
        if (paused && !fromFocusLoss) {
            pausedByFocusLoss = false
            abandonAudioFocus()
            releaseLocks()
        }
        if (!paused) {
            Log.w(TAG, "⚠️ pause() call did not succeed")
        }
        if (paused) {
            isPlaying = false
            // Send state update to Flutter immediately
            PlaybackChannelBridge.sendState("paused")
            // Update notification to show play button immediately
            updateNotification()
            // Update Android Auto playback state
            updateAndroidAutoPlaybackState()
        }
        cancelPlaybackStartWatcher()
        return paused
    }
    
    private fun resume(): Boolean {
        Log.i(TAG, "▶️ Resuming playback")
        if (!requestAudioFocus()) {
            Log.w(TAG, "⚠️ Audio focus not granted on resume")
        }
        acquireLocks()
        val resumed = bassAudioManager?.play() ?: false
        if (!resumed) {
            Log.w(TAG, "⚠️ resume() call did not succeed")
            abandonAudioFocus()
            releaseLocks()
        }
        if (resumed) {
            isPlaying = true  // Sync state immediately
            PlaybackChannelBridge.sendState("loading")
            updateNotification()  // Update notification to show pause button
            // Update Android Auto playback state
            updateAndroidAutoPlaybackState()
            beginPlaybackStartWatcher()
        }
        return resumed
    }
    
    private fun stop(stopService: Boolean = true): Boolean {
        if (stopService) {
            Log.i(TAG, "⏹️ Stopping playback")
            keepAliveForNextTrack = false
        } else {
            Log.i(TAG, "⏹️ Halting playback (service stays alive)")
            keepAliveForNextTrack = true
        }

        pendingManualStop = true
        Log.i(TAG, "⏹️ Pending manual stop armed (stopService=$stopService)")
        val stopped = bassAudioManager?.stop() ?: false

        if (stopService) {
            abandonAudioFocus()
            releaseLocks()
            stopForeground(true)
            stopSelf()
        }

        if (!stopped) {
            Log.w(TAG, "⚠️ stop() call did not succeed")
        }
        isPlaying = false
        cancelPlaybackStartWatcher()
        return stopped
    }
    
    private fun seek(position: Long): Boolean {
        Log.i(TAG, "⏩ Seeking to position: $position")
        val success = bassAudioManager?.seek(position) ?: false
        if (success) {
            currentPosition = position
            updateNotification()
        } else {
            Log.w(TAG, "⚠️ seek() call did not succeed")
        }
        return success
    }
    
    private fun next() {
        Log.i(TAG, "⏭️ Next track")
        PlaybackChannelBridge.sendMediaAction("next")
    }
    
    private fun previous() {
        Log.i(TAG, "⏮️ Previous track")
        PlaybackChannelBridge.sendMediaAction("previous")
    }
    
    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .setAcceptsDelayedFocusGain(true)
                        .build()
                }
                audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio focus request failed", e)
            false
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio focus abandon failed", e)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun initializeLocks() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "dab_music_wifi_lock")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Wi-Fi lock", e)
        }

        try {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dab_music_wakelock")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize wake lock", e)
        }
    }

    private fun acquireLocks() {
        try {
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to acquire Wi-Fi lock", e)
        }

        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to acquire wake lock", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to release Wi-Fi lock", e)
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to release wake lock", e)
        }
    }

    private fun startPositionTracking() {
        // Position tracking is now handled by BassAudioManager callbacks (onPositionChanged)
        // We don't need a separate loop here to avoid double-updating and race conditions.
        // The BassAudioManager updates every 1000ms which is sufficient.
        Log.d(TAG, "⚡ Using native BassAudioManager callbacks for position tracking")
    }
    
    private fun stopPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = null
    }

    private fun beginPlaybackStartWatcher() {
        cancelPlaybackStartWatcher()

        var watcherJob: Job? = null
        watcherJob = serviceScope.launch {
            try {
                Log.i(TAG, "⏱️ Playback start watcher armed for $currentTitle")
                var attempts = 0
                val maxAttempts = 20 // ~3 seconds at 150ms intervals (reduced for faster detection)
                while (attempts < maxAttempts) {
                    val playing = bassAudioManager?.isPlaying() == true
                    val hasProgress = (bassAudioManager?.getCurrentPosition() ?: 0L) > 0L

                    if (playing || hasProgress) {
                        Log.i(TAG, "✅ Playback watcher detected progress playing=$playing position=${bassAudioManager?.getCurrentPosition()}")
                        if (!isPlaying) {
                            isPlaying = true
                            startPositionTracking()
                            updateNotification()
                            PlaybackChannelBridge.sendState("playing")
                        }
                        playbackStartJob = null
                        return@launch
                    }
                    attempts++
                    delay(150)
                }

                // Only treat native PLAYING as success here. LOADING can get stuck forever
                // when HTTP readAt keeps failing (e.g., cloud auth/proxy mismatch).
                val engineState = bassAudioManager?.getState()
                if (engineState == BassAudioManager.AudioState.PLAYING) {
                    Log.w(TAG, "⚠️ Playback watcher timeout but engine active (state=$engineState), assuming success")
                    if (!isPlaying) {
                        isPlaying = true
                        startPositionTracking()
                        updateNotification()
                        PlaybackChannelBridge.sendState("playing")
                    }
                    playbackStartJob = null
                    return@launch
                }

                if (!isPlaying) {
                    val lastError = bassAudioManager?.getLastError() ?: "Playback start timed out"
                    Log.e(TAG, "❌ Playback did not start within expected time: $lastError (attempts=$attempts)")
                    playbackStartJob = null
                    handlePlaybackStartTimeout(lastError)
                    return@launch
                }
            } finally {
                if (isActive && playbackStartJob == watcherJob) {
                    playbackStartJob = null
                }
            }
        }
        playbackStartJob = watcherJob
    }

    private fun cancelPlaybackStartWatcher() {
        playbackStartJob?.cancel()
        playbackStartJob = null
    }

    private fun handlePlaybackStartTimeout(lastError: String) {
        val retryUrl = when {
            currentStreamUrl.isNullOrEmpty() -> null
            playbackStartRetries == 0 &&
                currentRawStreamUrl != null &&
                isHeaderPayload(currentStreamUrl!!) -> {
                Log.w(TAG, "⚠️ Playback timeout on header payload, retrying with raw signed URL")
                rawStreamFallbackAttempted = true
                currentRawStreamUrl
            }
            else -> currentStreamUrl
        }
        if (retryUrl == null) {
            finalizePlaybackStartFailure("Missing stream URL for retry")
            return
        }

        if (playbackStartRetries >= maxPlaybackStartRetries) {
            finalizePlaybackStartFailure(lastError)
            return
        }

        playbackStartRetries++
        val forceEngineReset = playbackStartRetries == maxPlaybackStartRetries
        val resetTag = if (forceEngineReset) " + engine reset" else ""
        Log.w(
            TAG,
            "⚠️ Playback start timed out, retrying (${playbackStartRetries}/$maxPlaybackStartRetries$resetTag)"
        )
        restartPlaybackAfterTimeout(retryUrl, forceEngineReset)
    }

    private fun restartPlaybackAfterTimeout(url: String, forceEngineReset: Boolean) {
        serviceScope.launch {
            suppressNextIdleHandling = true
            keepAliveForNextTrack = true
            pendingManualStop = false
            bassAudioManager?.stop()
            delay(200)

            if (forceEngineReset) {
                Log.w(TAG, "🧼 Rebuilding BASS engine before retry (YT Music style failover)")
                val engineReady = bassAudioManager?.restartEngine() == true
                if (!engineReady) {
                    finalizePlaybackStartFailure("Engine restart failed")
                    return@launch
                }
                bassAudioManager?.initializeEqualizer()
            }

            PlaybackChannelBridge.sendState("loading")
            val restarted = bassAudioManager?.playUrl(url) == true
            if (!restarted) {
                val reason = bassAudioManager?.getLastError() ?: "Failed to restart stream"
                finalizePlaybackStartFailure(reason)
                return@launch
            }

            val resumed = bassAudioManager?.play() ?: false
            if (!resumed) {
                val reason = bassAudioManager?.getLastError() ?: "Failed to resume stream"
                finalizePlaybackStartFailure(reason)
                return@launch
            }

            keepAliveForNextTrack = false
            suppressNextIdleHandling = false
            beginPlaybackStartWatcher()
        }
    }

    private fun finalizePlaybackStartFailure(reason: String) {
        playbackStartRetries = 0
        suppressNextIdleHandling = false
        keepAliveForNextTrack = false
        Log.e(TAG, "❌ Playback start failure: $reason")
        PlaybackChannelBridge.sendError(reason)
        PlaybackChannelBridge.sendState("error")
        stop()
    }
    
    /**
     * Get current playback state
     */
    fun isPlaying(): Boolean = isPlaying
    fun getCurrentPosition(): Long = currentPosition
    fun getDuration(): Long = currentDuration
    fun getCurrentTrackInfo(): Triple<String, String, String> = Triple(currentTitle, currentArtist, currentAlbum)

    // Flutter bridge helpers
    fun pauseFromFlutter(): Boolean {
        Log.i(TAG, "🔄 pauseFromFlutter called")
        return pause()
    }

    fun resumeFromFlutter(): Boolean = resume()

    fun stopFromFlutter(): Boolean = stop(true)

    fun stopPlaybackKeepServiceFromFlutter(): Boolean = stop(false)

    fun seekFromFlutter(positionMs: Long): Boolean = seek(positionMs)

    fun getCurrentPositionMs(): Long = currentPosition

    fun getCurrentDurationMs(): Long = currentDuration

    fun isCurrentlyPlaying(): Boolean = isPlaying

    fun holdNetworkLockForStream(durationMs: Long) {
        serviceScope.launch {
            externalNetworkLockHolders++
            Log.i(TAG, "🔒 holdNetworkLockForStream holders=$externalNetworkLockHolders duration=${durationMs}ms")
            acquireLocks()
            externalNetworkLockReleaseJob?.cancel()
            externalNetworkLockReleaseJob = launch {
                delay(durationMs)
                releaseExternalNetworkLock()
            }
        }
    }

    private fun releaseExternalNetworkLock() {
        if (externalNetworkLockHolders > 0) {
            externalNetworkLockHolders--
        }

        Log.i(TAG, "🔓 releaseExternalNetworkLock holders=$externalNetworkLockHolders isPlaying=$isPlaying keepAlive=$keepAliveForNextTrack")

        if (externalNetworkLockHolders == 0 && !isPlaying && !keepAliveForNextTrack) {
            releaseLocks()
        }
    }

    fun initializeEqualizerFromFlutter(): Boolean {
        val result = bassAudioManager?.initializeEqualizer() ?: false
        Log.i(TAG, "🎛️ initializeEqualizerFromFlutter -> $result")
        return result
    }

    fun setEqualizerEnabledFromFlutter(enabled: Boolean): Boolean {
        val result = bassAudioManager?.setEqualizerEnabled(enabled) ?: false
        Log.i(TAG, "🎛️ setEqualizerEnabledFromFlutter($enabled) -> $result")
        return result
    }

    fun setEqualizerBandFromFlutter(band: Int, gain: Double): Boolean {
        val result = bassAudioManager?.setEqualizerBand(band, gain) ?: false
        Log.i(TAG, "🎛️ setEqualizerBandFromFlutter(band=$band, gain=$gain) -> $result")
        return result
    }

    fun getEqualizerBandFromFlutter(band: Int): Double {
        val value = bassAudioManager?.getEqualizerBand(band) ?: 0.0
        Log.i(TAG, "🎛️ getEqualizerBandFromFlutter($band) -> $value")
        return value
    }

    fun resetEqualizerFromFlutter(): Boolean {
        val result = bassAudioManager?.resetEqualizer() ?: false
        Log.i(TAG, "🎛️ resetEqualizerFromFlutter -> $result")
        return result
    }

    fun getEqualizerBandCountFromFlutter(): Int {
        val count = bassAudioManager?.getEqualizerBandCount() ?: 0
        Log.i(TAG, "🎵️ getEqualizerBandCountFromFlutter -> $count")
        return count
    }

    fun syncPlaybackStateFromFlutter() {
        val nativeIsPlaying = bassAudioManager?.isPlaying() == true
        val hasPosition = (bassAudioManager?.getCurrentPosition() ?: 0L) > 0L
        val engineState = bassAudioManager?.getState()
        
        Log.i(TAG, "🔄 syncPlaybackState: service=$isPlaying native=$nativeIsPlaying pos=$hasPosition state=$engineState")
        
        when {
            isPlaying -> PlaybackChannelBridge.sendState("playing")
            nativeIsPlaying || hasPosition -> {
                Log.i(TAG, "🔄 Native is active but service disagrees, forcing playing state")
                isPlaying = true
                startPositionTracking()
                PlaybackChannelBridge.sendState("playing")
            }
            engineState != null && engineState.value >= 2 -> {
                Log.i(TAG, "🔄 Engine reports playing state, forcing sync")
                isPlaying = true
                startPositionTracking()
                PlaybackChannelBridge.sendState("playing")
            }
            else -> {
                Log.i(TAG, "🔄 No playback detected, reporting stopped")
                PlaybackChannelBridge.sendState("stopped")
            }
        }
    }

    fun recoverPlaybackFromFlutter() {
        Log.i(TAG, "⚠️ recoverPlayback requested for $currentTitle")
        if (currentStreamUrl.isNullOrEmpty()) {
            Log.e(TAG, "❌ Cannot recover: no stream URL available")
            return
        }
        
        serviceScope.launch {
            try {
                Log.i(TAG, "🔄 Attempting playback recovery via engine restart")
                val stopped = bassAudioManager?.stop() ?: false
                delay(300)
                
                val restarted = bassAudioManager?.playUrl(currentStreamUrl!!) ?: false
                if (restarted) {
                    val resumed = bassAudioManager?.play() ?: false
                    if (resumed) {
                        Log.i(TAG, "✅ Playback recovery successful")
                        beginPlaybackStartWatcher()
                    } else {
                        Log.e(TAG, "❌ Playback recovery failed at resume step")
                        PlaybackChannelBridge.sendState("error")
                    }
                } else {
                    Log.e(TAG, "❌ Playback recovery failed at restart step")
                    PlaybackChannelBridge.sendState("error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Playback recovery exception", e)
                PlaybackChannelBridge.sendState("error")
            }
        }
    }
}
