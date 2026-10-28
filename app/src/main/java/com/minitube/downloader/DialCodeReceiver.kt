package com.minitube.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Experimental OEM-dependent fixed dial code *#*#648823#*#*.
 * Dialers may not emit this broadcast. Never remove the launcher without another tested way in.
 */
class DialCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.data?.scheme != "android_secret_code" ||
            intent.data?.host != "648823") return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "minitube_access"
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "فتح MiniTube", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = Intent(context, UnlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 648823, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("MiniTube")
            .setContentText("اضغط لإدخال رمز الدخول وفتح التطبيق")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try { notificationManager.notify(648823, notification) }
        catch (_: SecurityException) { /* Notification permission may be unavailable. */ }
    }
}
