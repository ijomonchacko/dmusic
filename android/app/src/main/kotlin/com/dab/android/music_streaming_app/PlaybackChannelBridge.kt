package com.dmusic.android.dmusic

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.MethodChannel

/**
 * Lightweight bridge that lets background Android components signal playback state
 * changes back to Flutter via the main method channel.
 */
object PlaybackChannelBridge {
    private const val TAG = "PlaybackChannelBridge"

    @Volatile
    private var methodChannel: MethodChannel? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(channel: MethodChannel) {
        methodChannel = channel
        Log.d(TAG, "MethodChannel attached")
    }

    fun detach(channel: MethodChannel) {
        if (methodChannel === channel) {
            methodChannel = null
            Log.d(TAG, "MethodChannel detached")
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun sendState(state: String) {
        runOnMain {
            if (methodChannel != null) {
                methodChannel?.invokeMethod("onPlaybackStateChanged", mapOf("state" to state))
            } else {
                Log.w(TAG, "Dropping state update '$state' because channel is not attached")
            }
        }
    }

    fun sendMediaAction(action: String) {
        runOnMain {
            if (methodChannel != null) {
                methodChannel?.invokeMethod("onMediaAction", mapOf("action" to action))
            } else {
                Log.w(TAG, "Dropping media action '$action' because channel is not attached")
            }
        }
    }

    fun sendError(error: String) {
        runOnMain {
            if (methodChannel != null) {
                methodChannel?.invokeMethod("onError", mapOf("error" to error))
            } else {
                Log.w(TAG, "Dropping playback error '$error' because channel is not attached")
            }
        }
    }
    
    fun sendBluetoothState(connected: Boolean, deviceName: String?) {
        runOnMain {
            if (methodChannel != null) {
                methodChannel?.invokeMethod("onBluetoothStateChanged", mapOf(
                    "connected" to connected,
                    "deviceName" to deviceName
                ))
            } else {
                Log.w(TAG, "Dropping Bluetooth state update because channel is not attached")
            }
        }
    }
    
    fun sendPlayFromId(mediaId: String) {
        runOnMain {
            if (methodChannel != null) {
                methodChannel?.invokeMethod("onPlayFromMediaId", mapOf("mediaId" to mediaId))
            } else {
                Log.w(TAG, "Dropping playFromId '$mediaId' because channel is not attached")
            }
        }
    }
    
    fun sendSearchAndPlay(query: String) {
        runOnMain {
            if (methodChannel != null) {
                methodChannel?.invokeMethod("onSearchAndPlay", mapOf("query" to query))
            } else {
                Log.w(TAG, "Dropping searchAndPlay '$query' because channel is not attached")
            }
        }
    }
}
