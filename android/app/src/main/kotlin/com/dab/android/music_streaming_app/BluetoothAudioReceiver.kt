package com.dmusic.android.dmusic

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log

/**
 * Bluetooth Audio Receiver
 * Handles Bluetooth audio connection/disconnection events and media button events
 * Supports Bluetooth car stereos, headphones, and speakers via AVRCP
 */
class BluetoothAudioReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BluetoothAudioReceiver"
        
        @Volatile
        private var wasPlayingBeforeDisconnect = false
        
        @Volatile
        var isBluetoothConnected = false
            private set
        
        @Volatile
        var connectedDeviceName: String? = null
            private set
        
        /**
         * Register this receiver in a context
         */
        fun register(context: Context): BluetoothAudioReceiver {
            val receiver = BluetoothAudioReceiver()
            val filter = IntentFilter().apply {
                // Bluetooth connection state changes
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
                
                // A2DP profile state changes (audio streaming)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
                
                // Audio becoming noisy (headphones unplugged or BT disconnected)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                
                // Media button events
                addAction(Intent.ACTION_MEDIA_BUTTON)
            }
            context.registerReceiver(receiver, filter)
            Log.i(TAG, "✅ Bluetooth Audio Receiver registered")
            return receiver
        }
        
        fun setWasPlaying(wasPlaying: Boolean) {
            wasPlayingBeforeDisconnect = wasPlaying
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                handleBluetoothConnected(intent)
            }
            
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                handleBluetoothDisconnected(context)
            }
            
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                handleA2dpStateChange(context, intent)
            }
            
            BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED -> {
                handleA2dpPlayingStateChange(intent)
            }
            
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                handleAudioBecomingNoisy(context)
            }
            
            Intent.ACTION_MEDIA_BUTTON -> {
                handleMediaButton(context, intent)
            }
        }
    }
    
    private fun handleBluetoothConnected(intent: Intent) {
        try {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            connectedDeviceName = device?.name ?: "Unknown Bluetooth Device"
            isBluetoothConnected = true
            
            Log.i(TAG, "🔵 Bluetooth device connected: $connectedDeviceName")
            
            // Notify Flutter about Bluetooth connection
            PlaybackChannelBridge.sendBluetoothState(true, connectedDeviceName)
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception getting Bluetooth device name", e)
            connectedDeviceName = "Bluetooth Device"
            isBluetoothConnected = true
        }
    }
    
    private fun handleBluetoothDisconnected(context: Context) {
        Log.i(TAG, "🔴 Bluetooth device disconnected")
        
        val previouslyConnected = isBluetoothConnected
        isBluetoothConnected = false
        connectedDeviceName = null
        
        // Pause playback when Bluetooth disconnects
        if (previouslyConnected) {
            pausePlaybackOnDisconnect(context)
        }
        
        // Notify Flutter about Bluetooth disconnection
        PlaybackChannelBridge.sendBluetoothState(false, null)
    }
    
    private fun handleA2dpStateChange(context: Context, intent: Intent) {
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)
        
        Log.i(TAG, "🎧 A2DP state changed: $previousState -> $state")
        
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                isBluetoothConnected = true
                try {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    connectedDeviceName = device?.name ?: "Bluetooth Audio"
                    Log.i(TAG, "🎧 A2DP connected: $connectedDeviceName")
                    
                    // Auto-resume if was playing before disconnect
                    if (wasPlayingBeforeDisconnect) {
                        wasPlayingBeforeDisconnect = false
                        resumePlayback(context)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "❌ Security exception", e)
                    connectedDeviceName = "Bluetooth Audio"
                }
                
                PlaybackChannelBridge.sendBluetoothState(true, connectedDeviceName)
            }
            
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.i(TAG, "🎧 A2DP disconnected")
                
                if (isBluetoothConnected) {
                    pausePlaybackOnDisconnect(context)
                }
                
                isBluetoothConnected = false
                connectedDeviceName = null
                
                PlaybackChannelBridge.sendBluetoothState(false, null)
            }
        }
    }
    
    private fun handleA2dpPlayingStateChange(intent: Intent) {
        val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
        Log.i(TAG, "🎧 A2DP playing state: $state")
    }
    
    private fun handleAudioBecomingNoisy(context: Context) {
        Log.i(TAG, "🔊 Audio becoming noisy - pausing playback")
        
        // This is called when headphones are unplugged or Bluetooth disconnects
        pausePlaybackOnDisconnect(context)
    }
    
    private fun handleMediaButton(context: Context, intent: Intent) {
        // Forward media button events to the playback service
        Log.i(TAG, "🎮 Media button event received via Bluetooth")
        
        // Try to use AutoMediaBrowserService's MediaSession first (for Android Auto)
        val autoSession = AutoMediaBrowserService.instance?.mediaSession
        if (autoSession != null) {
            try {
                androidx.media.session.MediaButtonReceiver.handleIntent(autoSession, intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle via AutoMediaBrowserService: ${e.message}")
            }
        }
        
        // Fallback to MusicPlaybackService's notification manager MediaSession
        val musicService = MusicPlaybackService.instance
        if (musicService != null) {
            try {
                // Forward the media button action to the service
                PlaybackChannelBridge.sendMediaAction("MEDIA_BUTTON")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to handle media button", e)
            }
        }
    }
    
    private fun pausePlaybackOnDisconnect(context: Context) {
        try {
            // Remember if we were playing
            val musicService = MusicPlaybackService.instance
            if (musicService != null) {
                wasPlayingBeforeDisconnect = musicService.isCurrentlyPlaying()
            }
            
            // Send pause command
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = "PAUSE"
            }
            context.startService(intent)
            
            Log.i(TAG, "⏸️ Paused playback on Bluetooth disconnect (was playing: $wasPlayingBeforeDisconnect)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to pause playback", e)
        }
    }
    
    private fun resumePlayback(context: Context) {
        try {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = "RESUME"
            }
            context.startService(intent)
            
            Log.i(TAG, "▶️ Resumed playback on Bluetooth reconnect")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to resume playback", e)
        }
    }
}
