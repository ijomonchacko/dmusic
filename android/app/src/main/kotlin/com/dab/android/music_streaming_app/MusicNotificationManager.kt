package com.dmusic.android.dmusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.File
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/**
 * Enhanced Music Notification Manager with proper album art loading
 * Fixes background playback notification issues
 */
class MusicNotificationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MusicNotificationManager"
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 1001
        
        // Actions
        private const val ACTION_PLAY_PAUSE = "com.dab.music.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.dab.music.NEXT"
        private const val ACTION_PREVIOUS = "com.dab.music.PREVIOUS"
        private const val ACTION_STOP = "com.dab.music.STOP"
        private const val ACTION_SEEK = "com.dab.music.SEEK"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var mediaSession: MediaSessionCompat? = null
    private var currentAlbumArt: Bitmap? = null
    private var isPlaying = false
    private var currentTrack: TrackInfo? = null
    private var albumArtLoadJob: Job? = null
    private var lastAlbumArtUrl: String? = null  // Track last loaded URL to avoid redundant loads
    private var lastNotificationUpdateTime = 0L  // Debounce rapid updates
    private val notificationUpdateDebounceMs = 100L  // Minimum time between notification rebuilds
    
    // CRITICAL: Generation counter to invalidate stale album art callbacks
    // Incremented on every track change to ensure old callbacks are rejected
    @Volatile
    private var albumArtGeneration = 0
    
    /**
     * Get the MediaSession for Bluetooth AVRCP support
     */
    fun getMediaSession(): MediaSessionCompat? = mediaSession
    
    /**
     * CRITICAL: Clear album art cache when starting a new track
     * This prevents showing the previous track's album art
     */
    fun clearAlbumArtCache() {
        Log.i(TAG, "🧹 Clearing album art cache for new track (was: ${lastAlbumArtUrl?.take(30)})")
        // CRITICAL: Increment generation to invalidate all pending album art callbacks
        albumArtGeneration++
        Log.i(TAG, "🔢 Album art generation now: $albumArtGeneration")
        albumArtLoadJob?.cancel()
        albumArtLoadJob = null
        currentAlbumArt = null
        lastAlbumArtUrl = null
        // Also clear the current track to force full refresh
        currentTrack = null
        
        // CRITICAL for Android 16: Clear MediaSession metadata immediately
        // This prevents the old album art from being shown
        try {
            mediaSession?.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Loading...")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "")
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, null)
                    .build()
            )
            Log.i(TAG, "🧹 MediaSession metadata cleared for new track")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing MediaSession metadata", e)
        }
    }
    
    data class TrackInfo(
        val title: String,
        val artist: String,
        val album: String,
        val albumArtUrl: String?,
        val duration: Long,
        val position: Long
    )
    
    init {
        createNotificationChannel()
        initializeMediaSession()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
            Log.i(TAG, "✅ Notification channel created")
        }
    }
    
    private fun initializeMediaSession() {
        try {
            mediaSession = MediaSessionCompat(context, TAG).apply {
                // Set supported actions for better notification controls
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setActions(
                            PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_FAST_FORWARD or
                            PlaybackStateCompat.ACTION_REWIND
                        )
                        .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                        .setBufferedPosition(0)
                        .build()
                )
                
                // Set callback for media actions
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        Log.i(TAG, "▶️ MediaSession: Play")
                        sendAction(ACTION_PLAY_PAUSE)
                    }
                    
                    override fun onPause() {
                        Log.i(TAG, "⏸️ MediaSession: Pause")
                        sendAction(ACTION_PLAY_PAUSE)
                    }
                    
                    override fun onSkipToNext() {
                        Log.i(TAG, "⏭️ MediaSession: Next")
                        sendAction(ACTION_NEXT)
                    }
                    
                    override fun onSkipToPrevious() {
                        Log.i(TAG, "⏮️ MediaSession: Previous")
                        sendAction(ACTION_PREVIOUS)
                    }
                    
                    override fun onStop() {
                        Log.i(TAG, "⏹️ MediaSession: Stop")
                        sendAction(ACTION_STOP)
                    }
                    
                    override fun onSeekTo(pos: Long) {
                        Log.i(TAG, "⏩ MediaSession: Seek to $pos")
                        try {
                            val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                                action = ACTION_SEEK
                                putExtra("position", pos)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to dispatch seek", e)
                        }
                    }
                    
                    override fun onFastForward() {
                        Log.i(TAG, "⏩ MediaSession: Fast Forward")
                        currentTrack?.let { track ->
                            val newPos = (track.position + 10000).coerceAtMost(track.duration)
                            onSeekTo(newPos)
                        }
                    }
                    
                    override fun onRewind() {
                        Log.i(TAG, "⏪ MediaSession: Rewind")
                        currentTrack?.let { track ->
                            val newPos = (track.position - 10000).coerceAtLeast(0)
                            onSeekTo(newPos)
                        }
                    }
                })
                
                isActive = true
            }
            Log.i(TAG, "✅ MediaSession initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize MediaSession", e)
        }
    }
    
    private fun sendAction(action: String) {
        try {
            val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to dispatch action $action", e)
        }
    }
    
    /**
     * Update notification with track info and proper album art loading
     * Fixed: Always updates on play/pause state changes, only debounces position-only updates
     * Fixed: Properly clears and reloads album art on track change
     */
    fun updateNotification(
        title: String,
        artist: String,
        album: String,
        albumArtUrl: String?,
        isPlaying: Boolean,
        duration: Long = 0,
        position: Long = 0
    ) {
        val previousIsPlaying = this.isPlaying
        val previousTitle = this.currentTrack?.title
        val previousArtist = this.currentTrack?.artist
        val previousAlbumArtUrl = this.currentTrack?.albumArtUrl
        
        this.isPlaying = isPlaying
        this.currentTrack = TrackInfo(title, artist, album, albumArtUrl, duration, position)
        
        // Detect if this is a state change that MUST be shown immediately
        val playStateChanged = previousIsPlaying != isPlaying
        // Track change detection: compare both title AND artist to be sure
        val trackChanged = previousTitle != title || previousArtist != artist
        // Also check if album art URL changed even if track "looks" the same
        val albumArtChanged = albumArtUrl != previousAlbumArtUrl && albumArtUrl != null
        val isImportantUpdate = playStateChanged || trackChanged || albumArtChanged
        
        Log.i(TAG, "🔄 Notification update: '$title' by '$artist'")
        Log.i(TAG, "   State: playing=$isPlaying, stateChanged=$playStateChanged, trackChanged=$trackChanged, artChanged=$albumArtChanged")
        Log.i(TAG, "   Art URL: ${albumArtUrl?.take(60) ?: "null"}")
        Log.i(TAG, "   Prev Art: ${previousAlbumArtUrl?.take(60) ?: "null"}")
        
        // Debounce ONLY position-only updates - ALWAYS update for state or track changes
        val now = System.currentTimeMillis()
        val timeSinceLastUpdate = now - lastNotificationUpdateTime
        val shouldDebounce = !isImportantUpdate && timeSinceLastUpdate < notificationUpdateDebounceMs
        lastNotificationUpdateTime = now
        
        // CRITICAL FIX: On track change OR album art change, ALWAYS clear and reload
        if (trackChanged || albumArtChanged) {
            Log.i(TAG, "🎵 Track/art changed! Clearing old album art and loading new one")
            // CRITICAL: Increment generation to invalidate all pending album art callbacks
            albumArtGeneration++
            Log.i(TAG, "🔢 Album art generation now: $albumArtGeneration (track/art changed)")
            // Cancel any pending album art load
            albumArtLoadJob?.cancel()
            albumArtLoadJob = null
            // Clear the cached album art immediately so old art doesn't show
            currentAlbumArt = null
            // Reset the last URL to force reload
            lastAlbumArtUrl = null
        }
        
        // Check if we need to load album art
        val needsAlbumArtLoad = albumArtUrl != null && 
                                albumArtUrl != "null" && 
                                albumArtUrl.isNotEmpty() &&
                                (trackChanged || albumArtChanged || albumArtUrl != lastAlbumArtUrl || currentAlbumArt == null)
        
        if (needsAlbumArtLoad) {
            Log.i(TAG, "🎨 Starting album art load for: $title (generation: $albumArtGeneration)")
            albumArtLoadJob?.cancel()
            lastAlbumArtUrl = albumArtUrl
            
            // CRITICAL: Capture the current generation for callback validation
            val loadGeneration = albumArtGeneration
            val loadingForTitle = title
            val loadingForUrl = albumArtUrl
            
            // Load album art asynchronously with generation check
            albumArtLoadJob = loadAlbumArtAsync(albumArtUrl!!) { bitmap ->
                // CRITICAL: Primary validation using generation counter
                // If generation changed, this callback is stale and must be rejected
                val isStale = loadGeneration != albumArtGeneration
                
                Log.i(TAG, "🎨 Art callback - For: '$loadingForTitle' (gen: $loadGeneration, current: $albumArtGeneration)")
                Log.i(TAG, "🎨 Art callback - URL: ${loadingForUrl?.take(50)}")
                Log.i(TAG, "🎨 Art callback - Stale: $isStale, bitmap: ${bitmap != null}")
                
                if (isStale) {
                    Log.w(TAG, "⚠️ Album art REJECTED - STALE callback (gen $loadGeneration != current $albumArtGeneration)")
                    Log.w(TAG, "   This was for track: '$loadingForTitle', ignoring")
                    return@loadAlbumArtAsync
                }
                
                val currentTrackInfo = currentTrack
                if (currentTrackInfo == null) {
                    Log.w(TAG, "⚠️ Album art REJECTED - no current track")
                    return@loadAlbumArtAsync
                }
                
                Log.i(TAG, "✅ Album art ACCEPTED for: $loadingForTitle (gen: $loadGeneration)")
                currentAlbumArt = bitmap ?: getDefaultAlbumArt()
                
                // CRITICAL FIX: Force rebuild and show notification with new album art
                buildAndShowNotification()
                updateMediaSession()
                
                // CRITICAL FIX: Also notify the service to update foreground notification
                try {
                    val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                        action = "UPDATE_NOTIFICATION"
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.i(TAG, "📤 Sent UPDATE_NOTIFICATION to service")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to trigger notification update", e)
                }
            }
        } else {
            Log.d(TAG, "⏭️ Skipping album art load: url=$albumArtUrl, lastUrl=$lastAlbumArtUrl, hasArt=${currentAlbumArt != null}")
        }
        
        // Build and show notification - ALWAYS for important updates, debounce position-only updates
        if (!shouldDebounce) {
            buildAndShowNotification()
            updateMediaSession()
        } else {
            Log.d(TAG, "⏭️ Debounced notification update (position-only)")
        }
    }
    
    private fun loadAlbumArtAsync(url: String, callback: (Bitmap?) -> Unit): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🎨 Loading album art from: $url")
                val bitmap = loadBitmapFromUrl(url)
                
                // Check if cancelled before invoking callback
                if (!isActive) {
                    Log.d(TAG, "🚫 Album art load cancelled, not invoking callback")
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    if (!isActive) {
                        Log.d(TAG, "🚫 Album art load cancelled before callback")
                        return@withContext
                    }
                    if (bitmap != null) {
                        Log.i(TAG, "✅ Album art loaded successfully")
                        callback(bitmap)
                    } else {
                        Log.w(TAG, "⚠️ Failed to load album art, using default")
                        callback(getDefaultAlbumArt())
                    }
                }
            } catch (e: Exception) {
                // Check if this is a cancellation - don't log as error
                if (e is kotlinx.coroutines.CancellationException || 
                    e.cause is kotlinx.coroutines.CancellationException ||
                    e.javaClass.simpleName.contains("Cancellation")) {
                    Log.d(TAG, "🚫 Album art load was cancelled")
                    // Rethrow to properly propagate cancellation
                    throw e
                }
                Log.e(TAG, "❌ Error loading album art", e)
                if (isActive) {
                    try {
                        withContext(Dispatchers.Main) {
                            callback(getDefaultAlbumArt())
                        }
                    } catch (ce: Exception) {
                        // Ignore cancellation during error handling
                        Log.d(TAG, "🚫 Cancelled during error handling")
                    }
                }
            }
        }
    }
    
    private fun loadBitmapFromUrl(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        
        // CRITICAL for Android 16: Use smaller max size
        val maxSize = if (Build.VERSION.SDK_INT >= 36) 256 else 512
        
        return try {
            // Local file path support
            if (url.startsWith("file://") || File(url).exists()) {
                val path = if (url.startsWith("file://")) {
                    Uri.parse(url).path ?: url.removePrefix("file://")
                } else {
                    url
                }
                val fileBitmap = BitmapFactory.decodeFile(path)
                if (fileBitmap != null) {
                    return fileBitmap
                }
            }

            val urlConnection = URL(url)
            connection = urlConnection.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 10000 // 10 seconds
                readTimeout = 15000 // 15 seconds
                requestMethod = "GET"
                doInput = true
                
                // Set user agent to avoid blocking
                setRequestProperty("User-Agent", "joehacks(v1)")
                setRequestProperty("Accept", "image/*")
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                // Scale bitmap if too large (optimize for notification)
                if (bitmap != null && (bitmap.width > maxSize || bitmap.height > maxSize)) {
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, maxSize, maxSize, true)
                    if (scaledBitmap != bitmap) {
                        bitmap.recycle()
                    }
                    scaledBitmap
                } else {
                    bitmap
                }
            } else {
                Log.w(TAG, "⚠️ HTTP error loading album art: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception loading album art", e)
            null
        } finally {
            inputStream?.close()
            connection?.disconnect()
        }
    }
    
    private fun getDefaultAlbumArt(): Bitmap? {
        return try {
            // Create a default music icon bitmap
            val resourceId = context.resources.getIdentifier("music_note", "drawable", context.packageName)
            if (resourceId != 0) {
                BitmapFactory.decodeResource(context.resources, resourceId)
            } else {
                // Create a simple colored bitmap as fallback
                Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(0xFF1E88E5.toInt()) // Blue background
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating default album art", e)
            null
        }
    }
    
    private fun buildAndShowNotification() {
        try {
            val track = currentTrack ?: return
            
            // Use current album art or generate a default one
            val albumArtBitmap = currentAlbumArt ?: getDefaultAlbumArt()
            
            // CRITICAL for Android 16: Scale bitmap to avoid size issues
            val scaledBitmap = albumArtBitmap?.let { scaleBitmapForNotification(it) }
            
            // CRITICAL for Android 16: Update MediaSession FIRST before notification
            // This ensures the system has the correct metadata when building notification
            updateMediaSessionMetadataOnly(track, scaledBitmap)
            
            // Create notification with media style
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setSubText(track.album)
                .setLargeIcon(scaledBitmap)
                .setSmallIcon(android.R.drawable.ic_media_play) // Use system icon as fallback
                .setStyle(
                    MediaStyle()
                        .setMediaSession(mediaSession?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .addAction(createAction(ACTION_PREVIOUS, "Previous", android.R.drawable.ic_media_previous))
                .addAction(
                    if (isPlaying) {
                        createAction(ACTION_PLAY_PAUSE, "Pause", android.R.drawable.ic_media_pause)
                    } else {
                        createAction(ACTION_PLAY_PAUSE, "Play", android.R.drawable.ic_media_play)
                    }
                )
                .addAction(createAction(ACTION_NEXT, "Next", android.R.drawable.ic_media_next))
                .addAction(createAction(ACTION_STOP, "Stop", android.R.drawable.ic_menu_close_clear_cancel))
                .setContentIntent(createContentIntent())
                .setDeleteIntent(createDeleteIntent())
                .build()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.i(TAG, "✅ Notification updated successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error building notification", e)
        }
    }
    
    /**
     * Build and return a notification for foreground service
     * Used by MusicPlaybackService.startForeground()
     */
    fun buildForegroundNotification(): android.app.Notification {
        val track = currentTrack
        
        // Use current album art or generate a default one
        val albumArtBitmap = currentAlbumArt ?: getDefaultAlbumArt()
        
        // CRITICAL for Android 16: Scale bitmap to avoid size issues
        val scaledBitmap = albumArtBitmap?.let { scaleBitmapForNotification(it) }
        
        // CRITICAL for Android 16: Update MediaSession metadata FIRST
        if (track != null) {
            updateMediaSessionMetadataOnly(track, scaledBitmap)
        }
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(track?.title ?: "DAB Music")
            .setContentText(track?.artist ?: "Loading...")
            .setSubText(track?.album ?: "")
            .setLargeIcon(scaledBitmap)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(createAction(ACTION_PREVIOUS, "Previous", android.R.drawable.ic_media_previous))
            .addAction(
                if (isPlaying) {
                    createAction(ACTION_PLAY_PAUSE, "Pause", android.R.drawable.ic_media_pause)
                } else {
                    createAction(ACTION_PLAY_PAUSE, "Play", android.R.drawable.ic_media_play)
                }
            )
            .addAction(createAction(ACTION_NEXT, "Next", android.R.drawable.ic_media_next))
            .setContentIntent(createContentIntent())
            .build()
    }
    
    private fun createAction(action: String, title: String, iconRes: Int): NotificationCompat.Action {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Action.Builder(iconRes, title, pendingIntent).build()
    }
    
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createDeleteIntent(): PendingIntent {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun updateMediaSession() {
        val track = currentTrack ?: return
        
        try {
            // Use current album art or generate a default one for lock screen / Bluetooth
            val albumArtBitmap = currentAlbumArt ?: getDefaultAlbumArt()
            
            // CRITICAL for Android 16: Scale down bitmap to avoid issues
            val scaledBitmap = albumArtBitmap?.let { scaleBitmapForNotification(it) }
            
            // Update metadata - include both ART and ALBUM_ART for Android 16 compatibility
            mediaSession?.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, scaledBitmap)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, scaledBitmap)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, track.album)
                    .build()
            )
            
            // Update playback state
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_REWIND
                    )
                    .setState(state, track.position, 1.0f)
                    .setBufferedPosition(track.duration) // Enable seeking
                    .build()
            )
            
            Log.d(TAG, "✅ MediaSession updated with art for: ${track.title}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating MediaSession", e)
        }
    }
    
    /**
     * Scale bitmap for notification - Android 16 has stricter size limits
     */
    private fun scaleBitmapForNotification(bitmap: Bitmap): Bitmap {
        val maxSize = if (Build.VERSION.SDK_INT >= 36) 256 else 512 // Smaller for Android 16
        
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) {
            return bitmap
        }
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, maxSize, maxSize, true)
        Log.d(TAG, "📐 Scaled bitmap from ${bitmap.width}x${bitmap.height} to ${maxSize}x${maxSize}")
        return scaledBitmap
    }
    
    /**
     * Update MediaSession metadata only (without playback state)
     * CRITICAL for Android 16: Must be called BEFORE building notification
     */
    private fun updateMediaSessionMetadataOnly(track: TrackInfo, albumArtBitmap: Bitmap?) {
        try {
            mediaSession?.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBitmap)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, albumArtBitmap)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, track.album)
                    .build()
            )
            Log.d(TAG, "📝 MediaSession metadata updated for: ${track.title}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating MediaSession metadata", e)
        }
    }
    
    /**
     * Update playback position for seeking support
     */
    fun updatePosition(position: Long, duration: Long) {
        currentTrack = currentTrack?.copy(position = position, duration = duration)
        
        try {
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_REWIND
                    )
                    .setState(state, position, 1.0f)
                    .setBufferedPosition(duration)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating position", e)
        }
    }
    
    /**
     * Hide notification
     */
    fun hideNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                    .build()
            )
            Log.i(TAG, "✅ Notification hidden")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error hiding notification", e)
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        try {
            hideNotification()
            albumArtLoadJob?.cancel()
            albumArtLoadJob = null
            mediaSession?.release()
            mediaSession = null
            currentAlbumArt?.recycle()
            currentAlbumArt = null
            lastAlbumArtUrl = null  // Reset URL tracking for next session
            Log.i(TAG, "✅ MusicNotificationManager released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing resources", e)
        }
    }
}
