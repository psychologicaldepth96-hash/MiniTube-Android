package com.minitube.downloader

import android.app.Activity
import android.content.ContentValues
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import android.widget.*
import android.view.ViewGroup
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.ffmpeg.FFmpeg
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var quality: Spinner
    private lateinit var mode: Spinner
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var button: Button
    private val navy = Color.rgb(26, 47, 78)
    private val blue = Color.rgb(39, 188, 231)
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 44, 28, 20)
            setBackgroundColor(Color.rgb(248, 250, 253))
        }
        fun label(text: String, size: Float = 15f): TextView = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(navy); setPadding(0, 8, 0, 8)
        }
        root.addView(label("MiniTube", 30f))
        root.addView(label("تنزيل الفيديو أو الصوت من رابط مسموح لك بحفظه"))
        url = EditText(this).apply { hint = "الصق رابط الفيديو هنا"; setSingleLine(true); textDirection = ViewGroup.TEXT_DIRECTION_LTR }
        root.addView(url, LinearLayout.LayoutParams(-1, -2))
        root.addView(label("نوع الملف"))
        mode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("فيديو MP4", "صوت MP3")) }
        root.addView(mode)
        root.addView(label("أقصى دقة للفيديو"))
        quality = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("720p", "360p", "1080p", "2160p (4K)")) }
        root.addView(quality)
        button = Button(this).apply { text = "تنزيل"; setTextColor(Color.WHITE); setBackgroundColor(blue); setOnClickListener { download() } }
        root.addView(button, LinearLayout.LayoutParams(-1, -2))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, -2))
        status = label("جارٍ تجهيز أداة التنزيل...")
        root.addView(status)
        root.addView(label("مكان الحفظ: Downloads/MiniTube"))
        root.addView(label("نزّل الملفات التي تملك حق حفظها فقط. بعض المحتويات قد لا تكون متاحة."))
        setContentView(ScrollView(this).apply { addView(root) })
        if (intent?.action == Intent.ACTION_SEND) url.setText(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")
        button.isEnabled = false
        thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                initialized = true
                runOnUiThread { status.text = "جاهز للتنزيل"; button.isEnabled = true }
            } catch (e: Exception) {
                runOnUiThread { status.text = "خطأ في التهيئة: ${e.localizedMessage}" }
            }
        }
    }

    private fun download() {
        if (!initialized) { status.text = "لم تكتمل التهيئة"; return }
        val link = url.text.toString().trim()
        val allowed = listOf("https://www.youtube.com/", "https://youtube.com/", "https://youtu.be/", "https://m.youtube.com/")
        if (allowed.none { link.startsWith(it) }) {
            status.text = "أدخل رابط يوتيوب صحيحًا يبدأ بـ https://"; return
        }
        val audio = mode.selectedItemPosition == 1
        val h = when (quality.selectedItemPosition) { 1 -> 360; 2 -> 1080; 3 -> 2160; else -> 720 }
        button.isEnabled = false; progress.progress = 0; status.text = "جاري بدء التنزيل..."
        thread {
            val temp = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "working_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val request = YoutubeDLRequest(link)
                request.addOption("--no-playlist")
                request.addOption("--restrict-filenames")
                request.addOption("-o", File(temp, "%(title).150B.%(ext)s").absolutePath)
                if (audio) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                } else {
                    request.addOption("-f", "bv*[height<=${h}]+ba/b[height<=${h}]/best")
                    request.addOption("--merge-output-format", "mp4")
                    request.addOption("--recode-video", "mp4")
                }
                YoutubeDL.getInstance().execute(request) { percent, _ ->
                    runOnUiThread { progress.progress = percent.toInt().coerceIn(0, 100); status.text = "جاري التنزيل ${percent.toInt()}%" }
                }
                val files = temp.listFiles()?.filter { it.isFile && (it.extension.lowercase() == if(audio) "mp3" else "mp4") }.orEmpty()
                if (files.isEmpty()) error("لم يتم إنشاء الملف المطلوب")
                var saved = 0
                for (file in files) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, if(audio) "audio/mpeg" else "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiniTube")
                    }
                    val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    val uri = contentResolver.insert(collection, values) ?: error("فشل إنشاء الملف في التنزيلات")
                    try {
                        contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("فشل فتح ملف الحفظ")
                        saved++
                    } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
                }
                runOnUiThread { status.text = "تم حفظ $saved ملف في Downloads/MiniTube"; progress.progress = 100 }
            } catch (e: Exception) {
                runOnUiThread { status.text = "تعذر التنزيل: ${e.localizedMessage}" }
            } finally {
                temp.deleteRecursively()
                runOnUiThread { button.isEnabled = true }
            }
        }
    }
}
