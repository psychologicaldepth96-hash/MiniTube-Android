package com.minitube.downloader

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** App entry PIN. This is NOT a dynamically registered Android dialer secret code. */
object AccessCode {
    private const val PREF = "minitube_access"
    private const val DEFAULT = "648823"
    private fun digest(salt: String, code: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + ":" + code).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun validFormat(code: String) = code.length in 4..12 && code.all { it in '0'..'9' }

    fun matches(context: Context, input: String): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val hash = prefs.getString("hash", null) ?: return input == DEFAULT
        val salt = prefs.getString("salt", null) ?: return false
        return MessageDigest.isEqual(hash.toByteArray(), digest(salt, input).toByteArray())
    }

    fun change(context: Context, old: String, new: String): Boolean {
        if (!matches(context, old) || !validFormat(new)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("salt", salt).putString("hash", digest(salt, new)).commit()
    }
}
