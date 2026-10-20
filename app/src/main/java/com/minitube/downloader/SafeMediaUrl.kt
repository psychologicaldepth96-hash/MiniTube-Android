package com.minitube.downloader

import android.net.Uri

/** Only public HTTPS links for supported media platforms are accepted. */
object SafeMediaUrl {
    private val domains = listOf(
        "youtube.com", "youtu.be", "facebook.com", "fb.watch", "instagram.com",
        "x.com", "twitter.com", "tiktok.com", "snapchat.com", "pinterest.com",
        "reddit.com", "vimeo.com", "dailymotion.com", "threads.net"
    )
    fun isAllowed(value: String): Boolean {
        if (value.length !in 12..2048 || value.any { it == '\n' || it == '\r' || it == '\u0000' }) return false
        return try {
            val uri = Uri.parse(value)
            val host = uri.host?.lowercase() ?: return false
            uri.scheme.equals("https", true) &&
                uri.userInfo == null && uri.port == -1 &&
                domains.any { host == it || host.endsWith("." + it) }
        } catch (_: Exception) { false }
    }
    fun isYoutube(value: String): Boolean {
        val host = Uri.parse(value).host?.lowercase() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com")
    }
}
