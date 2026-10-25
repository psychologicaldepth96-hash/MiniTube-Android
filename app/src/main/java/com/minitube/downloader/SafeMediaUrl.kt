package com.minitube.downloader

import android.net.Uri

/** Only public HTTPS links for supported media platforms are accepted. */
object SafeMediaUrl {
    private val domains = listOf(
        "youtube.com", "youtu.be", "facebook.com", "fb.watch", "instagram.com",
        "x.com", "twitter.com", "tiktok.com", "snapchat.com", "pinterest.com",
        "reddit.com", "vimeo.com", "dailymotion.com", "threads.net",
        "linkedin.com", "twitch.tv", "kick.com", "streamable.com", "bilibili.com",
        "tumblr.com", "soundcloud.com", "mixcloud.com", "rutube.ru", "vk.com",
        "ok.ru", "odysee.com", "rumble.com", "bandcamp.com"
    )
    fun extractSharedUrl(text: String): String {
        val start = text.indexOf("https://", ignoreCase = true)
        if (start < 0) return text.trim()
        val rest = text.substring(start)
        val end = rest.indexOfFirst { it.isWhitespace() || it == '<' || it == '>' }
        return (if (end < 0) rest else rest.substring(0, end)).trimEnd('.', ',', ';', ')', ']')
    }
    fun isAllowed(value: String): Boolean {
        if (value.length !in 12..2048 || value.any { it == '\n' || it == '\r' || it == '\u0000' }) return false
        return try {
            val uri = Uri.parse(value)
            val host = uri.host?.lowercase() ?: return false
            uri.scheme.equals("https", true) &&
                uri.encodedAuthority?.contains("@") != true && uri.port == -1 &&
                domains.any { host == it || host.endsWith("." + it) }
        } catch (_: Exception) { false }
    }
    fun isYoutube(value: String): Boolean {
        val host = Uri.parse(value).host?.lowercase() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com")
    }
}
