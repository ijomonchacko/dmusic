package com.dmusic.android.dmusic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat

/**
 * Android Auto MediaBrowserService
 * Provides media browsing and playback capabilities for Android Auto
 */
class AutoMediaBrowserService : MediaBrowserServiceCompat() {
    
    companion object {
        private const val TAG = "AutoMediaBrowserService"
        
        // Root IDs for browsing
        private const val ROOT_ID = "root"
        private const val RECENT_ID = "recent"
        private const val FAVORITES_ID = "favorites"
        private const val NOW_PLAYING_ID = "now_playing"
        
        // Maximum items to show in Android Auto
        private const val MAX_ITEMS = 20
        
        @Volatile
        var instance: AutoMediaBrowserService? = null
            private set
    }
    
    // Public accessor for media session (used by BluetoothAudioReceiver)
    var mediaSession: MediaSessionCompat? = null
        private set
    private var currentTrackTitle: String = ""
    private var currentTrackArtist: String = ""
    private var currentTrackAlbum: String = ""
    private var currentAlbumArtUri: String? = null
    private var currentDuration: Long = 0
    private var currentPosition: Long = 0
    private var isPlaying: Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚗 Android Auto MediaBrowserService created")
        instance = this
        
        initializeMediaSession()
    }
    
    private fun initializeMediaSession() {
        try {
            mediaSession = MediaSessionCompat(this, TAG).apply {
                // Set supported playback actions
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
                            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                        )
                        .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                        .build()
                )
                
                // Set callback for media actions from Android Auto
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        Log.i(TAG, "🚗 Android Auto: Play")
                        sendPlaybackAction("RESUME")
                    }
                    
                    override fun onPause() {
                        Log.i(TAG, "🚗 Android Auto: Pause")
                        sendPlaybackAction("PAUSE")
                    }
                    
                    override fun onSkipToNext() {
                        Log.i(TAG, "🚗 Android Auto: Next")
                        sendPlaybackAction("com.dab.music.NEXT")
                    }
                    
                    override fun onSkipToPrevious() {
                        Log.i(TAG, "🚗 Android Auto: Previous")
                        sendPlaybackAction("com.dab.music.PREVIOUS")
                    }
                    
                    override fun onStop() {
                        Log.i(TAG, "🚗 Android Auto: Stop")
                        sendPlaybackAction("STOP")
                    }
                    
                    override fun onSeekTo(pos: Long) {
                        Log.i(TAG, "🚗 Android Auto: Seek to $pos")
                        try {
                            val intent = Intent(this@AutoMediaBrowserService, MusicPlaybackService::class.java).apply {
                                action = "SEEK"
                                putExtra("position", pos)
                            }
                            startService(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to seek", e)
                        }
                    }
                    
                    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                        Log.i(TAG, "🚗 Android Auto: Play from media ID: $mediaId")
                        // Handle playing specific track by ID
                        mediaId?.let {
                            PlaybackChannelBridge.sendPlayFromId(it)
                        }
                    }
                    
                    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                        Log.i(TAG, "🚗 Android Auto: Play from search: $query")
                        // Handle voice search from Android Auto
                        query?.let {
                            PlaybackChannelBridge.sendSearchAndPlay(it)
                        }
                    }
                    
                    override fun onCustomAction(action: String?, extras: Bundle?) {
                        Log.i(TAG, "🚗 Android Auto: Custom action: $action")
                    }
                })
                
                isActive = true
            }
            
            // Set the session token for Android Auto to connect
            sessionToken = mediaSession?.sessionToken
            
            Log.i(TAG, "✅ Android Auto MediaSession initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Android Auto MediaSession", e)
        }
    }
    
    private fun sendPlaybackAction(action: String) {
        try {
            val intent = Intent(this, MusicPlaybackService::class.java).apply {
                this.action = action
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send playback action: $action", e)
        }
    }
    
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Log.i(TAG, "🚗 onGetRoot - client: $clientPackageName")
        
        // Allow Android Auto to connect
        if (isValidClient(clientPackageName)) {
            return BrowserRoot(ROOT_ID, null)
        }
        
        // Also allow connection from other valid media browsers
        return BrowserRoot(ROOT_ID, null)
    }
    
    private fun isValidClient(packageName: String): Boolean {
        // Allow Android Auto and Google Assistant
        val allowedPackages = listOf(
            "com.google.android.projection.gearhead", // Android Auto
            "com.google.android.googlequicksearchbox", // Google Assistant
            "com.google.android.carassistant", // Google Assistant Driving Mode
            packageName // Allow same package
        )
        return allowedPackages.contains(packageName) || packageName == this.packageName
    }
    
    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Log.i(TAG, "🚗 onLoadChildren - parentId: $parentId")
        
        when (parentId) {
            ROOT_ID -> {
                // Return root browsable items
                val items = mutableListOf<MediaBrowserCompat.MediaItem>()
                
                // Now Playing section
                items.add(createBrowsableItem(
                    NOW_PLAYING_ID,
                    "Now Playing",
                    "Currently playing track",
                    null
                ))
                
                // Recent tracks section
                items.add(createBrowsableItem(
                    RECENT_ID,
                    "Recent",
                    "Recently played tracks",
                    null
                ))
                
                // Favorites section
                items.add(createBrowsableItem(
                    FAVORITES_ID,
                    "Favorites",
                    "Your favorite tracks",
                    null
                ))
                
                result.sendResult(items)
            }
            
            NOW_PLAYING_ID -> {
                // Return currently playing track
                val items = mutableListOf<MediaBrowserCompat.MediaItem>()
                
                if (currentTrackTitle.isNotEmpty()) {
                    items.add(createPlayableItem(
                        "current_track",
                        currentTrackTitle,
                        currentTrackArtist,
                        currentAlbumArtUri
                    ))
                }
                
                result.sendResult(items)
            }
            
            RECENT_ID, FAVORITES_ID -> {
                // Request data from Flutter side
                result.detach()
                
                // For now, return empty list - Flutter will populate this
                result.sendResult(mutableListOf())
            }
            
            else -> {
                result.sendResult(mutableListOf())
            }
        }
    }
    
    private fun createBrowsableItem(
        id: String,
        title: String,
        subtitle: String,
        iconUri: String?
    ): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                iconUri?.let { setIconUri(Uri.parse(it)) }
            }
            .build()
        
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }
    
    private fun createPlayableItem(
        id: String,
        title: String,
        artist: String,
        albumArtUri: String?
    ): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(artist)
            .apply {
                albumArtUri?.let { setIconUri(Uri.parse(it)) }
            }
            .build()
        
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }
    
    /**
     * Update the current track metadata for Android Auto display
     */
    fun updateMetadata(
        title: String,
        artist: String,
        album: String,
        albumArtUri: String?,
        duration: Long
    ) {
        currentTrackTitle = title
        currentTrackArtist = artist
        currentTrackAlbum = album
        currentAlbumArtUri = albumArtUri
        currentDuration = duration
        
        Log.i(TAG, "🚗 Updating Android Auto metadata: $title by $artist")
        
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .apply {
                    albumArtUri?.let {
                        putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it)
                        putString(MediaMetadataCompat.METADATA_KEY_ART_URI, it)
                    }
                }
                .build()
        )
    }
    
    /**
     * Update playback state for Android Auto
     */
    fun updatePlaybackState(isPlaying: Boolean, position: Long, duration: Long) {
        this.isPlaying = isPlaying
        this.currentPosition = position
        this.currentDuration = duration
        
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, position, 1.0f)
                .setBufferedPosition(duration)
                .build()
        )
    }
    
    override fun onDestroy() {
        Log.i(TAG, "🚗 Android Auto MediaBrowserService destroyed")
        mediaSession?.release()
        mediaSession = null
        instance = null
        super.onDestroy()
    }
}
