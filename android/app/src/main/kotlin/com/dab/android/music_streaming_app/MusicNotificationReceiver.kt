package com.dmusic.android.dmusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver for music notification actions
 * Handles play/pause, next, previous, stop, and seek actions
 */
class MusicNotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MusicNotificationReceiver"
        
        // Actions
        const val ACTION_PLAY_PAUSE = "com.dab.music.PLAY_PAUSE"
        const val ACTION_NEXT = "com.dab.music.NEXT"
        const val ACTION_PREVIOUS = "com.dab.music.PREVIOUS"
        const val ACTION_STOP = "com.dab.music.STOP"
        const val ACTION_SEEK = "com.dab.music.SEEK"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        Log.i(TAG, "🎵 Received action: $action")
        
        when (action) {
            ACTION_PLAY_PAUSE -> {
                Log.i(TAG, "⏯️ Play/Pause action")
                handlePlayPause(context)
            }
            
            ACTION_NEXT -> {
                Log.i(TAG, "⏭️ Next action")
                handleNext(context)
            }
            
            ACTION_PREVIOUS -> {
                Log.i(TAG, "⏮️ Previous action")
                handlePrevious(context)
            }
            
            ACTION_STOP -> {
                Log.i(TAG, "⏹️ Stop action")
                handleStop(context)
            }
            
            ACTION_SEEK -> {
                val position = intent.getLongExtra("position", 0)
                Log.i(TAG, "⏩ Seek action to position: $position")
                handleSeek(context, position)
            }
            
            else -> {
                Log.w(TAG, "⚠️ Unknown action: $action")
            }
        }
    }
    
    private fun handlePlayPause(context: Context) {
        try {
            // Send to Flutter via method channel or broadcast
            val intent = Intent("com.dab.music.flutter.PLAY_PAUSE")
            context.sendBroadcast(intent)
            
            // Also try to communicate with native audio service
            // This will be handled by the main activity's method channel
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling play/pause", e)
        }
    }
    
    private fun handleNext(context: Context) {
        try {
            val intent = Intent("com.dab.music.flutter.NEXT")
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling next", e)
        }
    }
    
    private fun handlePrevious(context: Context) {
        try {
            val intent = Intent("com.dab.music.flutter.PREVIOUS")
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling previous", e)
        }
    }
    
    private fun handleStop(context: Context) {
        try {
            val intent = Intent("com.dab.music.flutter.STOP")
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling stop", e)
        }
    }
    
    private fun handleSeek(context: Context, position: Long) {
        try {
            val intent = Intent("com.dab.music.flutter.SEEK").apply {
                putExtra("position", position)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling seek", e)
        }
    }
}
