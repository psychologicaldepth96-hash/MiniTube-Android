package com.minitube.downloader

import android.app.*
import android.content.*
import android.os.*
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import kotlin.concurrent.thread

class DownloadService : Service() {
    companion object {
        const val START = "com.minitube.START"
        const val CANCEL = "com.minitube.CANCEL"
        const val CHANNEL = "minitube_downloads"
        const val NOTIFICATION = 820
        @Volatile var running = false
        @Volatile var stopped = false
        @Volatile var stage = "جاهز"
        @Volatile var percent = -1
        @Volatile var completed = 0
        @Volatile var total = 0
        @Volatile var etaSeconds = -1L
        @Volatile var lastError = ""
        @Volatile var diagnostic = ""
    }
    private var processId: String? = null
    private lateinit var notifications: NotificationManager
    override fun onBind(intent: Intent?) : IBinder? = null
    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "تنزيلات MiniTube", NotificationManager.IMPORTANCE_LOW)
        )
    }
    private fun notifyState(message: String, progress: Int = -1, finished: Boolean = false) {
        stage = message
        percent = progress
        val cancelIntent = Intent(this, DownloadService::class.java).setAction(CANCEL)
        val cancel = PendingIntent.getService(this, 3, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 4, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (finished) "MiniTube" else "MiniTube • التنزيل في الخلفية")
            .setContentText(message)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(!finished)
        if (!finished) builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", cancel)
        if (progress in 0..100 && !finished) builder.setProgress(100, progress, false)
        else if (!finished) builder.setProgress(0, 0, true)
        if (Build.VERSION.SDK_INT >= 29 && !finished) {
            // Android 10+ foreground-service permission/type is declared in the manifest.
            startForeground(NOTIFICATION, builder.build())
        } else if (!finished) startForeground(NOTIFICATION, builder.build())
        else notifications.notify(NOTIFICATION, builder.build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            stopped = true
            processId?.let { id -> thread { YoutubeDL.getInstance().destroyProcessById(id) } }
            notifyState("جارٍ إيقاف التنزيل...")
            return START_NOT_STICKY
        }
        if (intent?.action != START || running) return START_NOT_STICKY
        val requestedUrl = intent.getStringExtra("url")?.trim() ?: ""
        val requestedAudio = intent.getBooleanExtra("audio", false)
        val requestedExt = intent.getStringExtra("extension") ?: ""
        val validExt = requestedExt in (if (requestedAudio) setOf("mp3", "m4a") else setOf("mp4", "mkv"))
        if (!SafeMediaUrl.isAllowed(requestedUrl) || !validExt || (intent.getBooleanExtra("playlist", false) && !SafeMediaUrl.isYoutube(requestedUrl))) {
            notifyState("تم رفض رابط أو إعدادات تنزيل غير صالحة", finished = true)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        stopped = false
        completed = 0
        total = 0
        etaSeconds = -1
        val url = requestedUrl
        val playlist = intent.getBooleanExtra("playlist", false)
        val audio = intent.getBooleanExtra("audio", false)
        val ext = requestedExt
        val height = intent.getIntExtra("height", 720).takeIf { it in listOf(360, 720, 1080, 2160) } ?: 720
        val bitrate = intent.getStringExtra("bitrate")?.takeIf { it in setOf("128K", "192K", "320K") } ?: "128K"
        notifyState("جارٍ تجهيز التنزيل...")
        thread {
            var failures = 0
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val links = if (playlist) {
                    val req = YoutubeDLRequest(url)
                    req.addOption("--flat-playlist")
                    req.addOption("--yes-playlist")
                    req.addOption("--ignore-errors")
                    req.addOption("--print", "%(id)s")
                    val id = "minitube_index_" + System.currentTimeMillis()
                    processId = id
                    val out = YoutubeDL.getInstance().execute(req, id).out
                    out.lines().map { it.trim() }.filter { Regex("^[a-zA-Z0-9_-]{11}$").matches(it) }.distinct()
                        .map { "https://www.youtube.com/watch?v=" + it }
                } else listOf(url)
                if (links.size > 500) error("القائمة طويلة جدًا؛ الحد الأقصى 500 عنصر لكل مهمة")
                total = links.size
                if (links.isEmpty()) error("القائمة فارغة أو غير متاحة")
                for ((index, link) in links.withIndex()) {
                    if (stopped) break
                    val dir = File(base, "task_" + System.currentTimeMillis() + "_" + index).apply { mkdirs() }
                    try {
                        notifyState("العنصر " + (index + 1) + " من " + total + " • محفوظ " + completed)
                        val request = YoutubeDLRequest(link)
                        request.addOption("--no-playlist")
                        // Preserve warnings and extractor details to diagnose unsupported sources.
                        request.addOption("--restrict-filenames")
                        request.addOption("--socket-timeout", "20")
                        request.addOption("--max-filesize", "2G")
                        request.addOption("--retries", "3")
                        request.addOption("--fragment-retries", "3")
                        request.addOption("-o", File(dir, "%(id)s-%(title).100B.%(ext)s").absolutePath)
                        if (audio) {
                            request.addOption("-x")
                            request.addOption("--audio-format", ext)
                            request.addOption("--audio-quality", bitrate)
                        } else {
                            request.addOption("-f", "bv*[height<=" + height + "]+ba/b[height<=" + height + "]/best/bv*+ba/b")
                            request.addOption("--merge-output-format", ext)
                            request.addOption("--recode-video", ext)
                        }
                        val id = "minitube_" + System.currentTimeMillis()
                        processId = id
                        YoutubeDL.getInstance().execute(request, id) { pct, eta, line ->
                            if (line.contains("ERROR:", true) || line.contains("WARNING:", true)) {
                                diagnostic = line.takeLast(350)
                            }
                            if (!stopped) {
                                etaSeconds = eta
                                val p = if (pct in 0f..100f) pct.toInt() else -1
                                // Notification updates are throttled to integer percentage changes.
                                if (p != percent) notifyState("العنصر " + (index + 1) + "/" + total + " • محفوظ " + completed, p)
                            }
                        }
                        if (stopped) break
                        val files = dir.listFiles()?.filter { it.isFile && it.extension.equals(ext, true) }.orEmpty()
                        if (files.isEmpty()) error("لم ينتج ملف صالح")
                        for (file in files) {
                            if (stopped) break
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                                put(MediaStore.MediaColumns.MIME_TYPE, when(ext) {
                                    "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "mkv" -> "video/x-matroska"; else -> "video/mp4"
                                })
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiniTube")
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("تعذر الحفظ")
                            try {
                                contentResolver.openOutputStream(uri)?.use { output ->
                                    file.inputStream().use { input ->
                                        val buffer = ByteArray(65536)
                                        while (!stopped) {
                                            val n = input.read(buffer)
                                            if (n < 0) break
                                            output.write(buffer, 0, n)
                                        }
                                    }
                                } ?: error("فشل فتح الملف")
                                if (stopped) { contentResolver.delete(uri, null, null); break }
                                contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                                completed++
                            } catch(e: Exception) { contentResolver.delete(uri, null, null); throw e }
                        }
                        notifyState("تم حفظ " + completed + " من " + total)
                    } catch (e: Exception) {
                        if (!stopped) {
                            failures++
                            lastError = (diagnostic.ifBlank { e.localizedMessage ?: e.javaClass.simpleName }).take(350)
                            notifyState("فشل العنصر " + (index + 1) + ": " + lastError)
                        }
                    } finally {
                        dir.deleteRecursively()
                    }
                }
                notifyState(if (stopped) "أُلغي التنزيل • محفوظ " + completed else if (failures > 0 && completed == 0) "فشل التنزيل: " + lastError else "اكتمل • محفوظ " + completed + "، فشل " + failures, finished = true)
            } catch(e: Exception) {
                lastError = (e.localizedMessage ?: "خطأ").take(220)
                notifyState(if (stopped) "أُلغي التنزيل" else "فشل: " + lastError, finished = true)
            } finally {
                running = false
                processId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}
