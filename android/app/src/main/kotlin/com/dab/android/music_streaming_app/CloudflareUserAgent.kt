package com.dmusic.android.dmusic

/**
 * Cloudflare-compatible User Agent Configuration
 * 
 * This object provides a consistent user agent string that bypasses
 * Cloudflare bot detection and other anti-bot measures commonly used
 * by streaming services.
 */
object CloudflareUserAgent {
    
    /**
     * Cloudflare-compatible user agent string
     * Mimics Chrome on macOS to avoid bot detection
     */
    const val USER_AGENT = "joehacks(v1)"
    
    /**
     * Alternative user agents for fallback scenarios
     */
    val FALLBACK_USER_AGENTS = listOf<String>()
    
    /**
     * Get user agent with optional fallback index
     */
    fun getUserAgent(fallbackIndex: Int = -1): String {
        return USER_AGENT
    }
    
    /**
     * Apply user agent to HTTP connection
     */
    fun applyToConnection(connection: java.net.URLConnection, fallbackIndex: Int = -1) {
        connection.setRequestProperty("User-Agent", getUserAgent(fallbackIndex))
    }
}
