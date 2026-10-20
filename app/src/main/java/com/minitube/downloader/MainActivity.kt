package com.minitube.downloader

import android.app.Activity
import android.content.ContentValues
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import android.content.ContentUris
import android.net.Uri
import android.widget.*
import android.view.ViewGroup
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var quality: Spinner
    private lateinit var mode: Spinner
    private lateinit var scope: Spinner
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var button: Button
    private lateinit var cancelButton: Button
    @Volatile private var canceled = false
    @Volatile private var activeProcessId: String? = null
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
        root.addView(label("نطاق التنزيل"))
        scope = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("فيديو واحد", "قائمة تشغيل كاملة (Playlist)")) }
        root.addView(scope)
        root.addView(label("نوع الملف"))
        mode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("فيديو MP4", "صوت MP3")) }
        root.addView(mode)
        root.addView(label("أقصى دقة للفيديو"))
        quality = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("720p", "360p", "1080p", "2160p (4K)")) }
        root.addView(quality)
        button = Button(this).apply { text = "تنزيل"; setTextColor(Color.WHITE); setBackgroundColor(blue); setOnClickListener { download() } }
        root.addView(button, LinearLayout.LayoutParams(-1, -2))
        cancelButton = Button(this).apply {
            text = "إلغاء التنزيل"
            isEnabled = false
            setOnClickListener {
                canceled = true
                isEnabled = false
                status.text = "جارٍ إيقاف التنزيل..."
                activeProcessId?.let { id -> thread { YoutubeDL.getInstance().destroyProcessById(id) } }
            }
        }
        root.addView(cancelButton, LinearLayout.LayoutParams(-1, -2))
        val filesButton = Button(this).apply {
            text = "الملفات التي تم تنزيلها"
            setOnClickListener { openDownloadedFiles() }
        }
        root.addView(filesButton, LinearLayout.LayoutParams(-1, -2))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, -2))
        status = label("جارٍ تجهيز أداة التنزيل...")
        root.addView(status)
        root.addView(label("مكان الحفظ: Downloads/MiniTube"))
        root.addView(label("عند اختيار Playlist يتم تنزيل عناصر القائمة المسموح بها. احتفظ بالتطبيق مفتوحًا أثناء التنزيل."))
        root.addView(label("نزّل الملفات التي تملك حق حفظها فقط. بعض المحتويات قد لا تكون متاحة."))
        setContentView(ScrollView(this).apply { addView(root) })
        if (intent?.action == Intent.ACTION_SEND) url.setText(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")
        button.isEnabled = false
        thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                runOnUiThread { status.text = "جارٍ تحديث محرك yt-dlp لتوافق يوتيوب..." }
                var updateMessage = "المحرك جاهز"
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.NIGHTLY)
                    updateMessage = "تم التحقق من تحديث yt-dlp"
                } catch (updateError: Exception) {
                    updateMessage = "تعذر تحديث yt-dlp؛ قد تفشل بعض روابط يوتيوب. تحقق من الإنترنت وأعد تشغيل التطبيق."
                }
                initialized = true
                val message = updateMessage
                runOnUiThread { status.text = message; button.isEnabled = true }
            } catch (e: Exception) {
                runOnUiThread { status.text = "خطأ في التهيئة: ${e.localizedMessage}" }
            }
        }
    }

    private fun download() {
        if (!initialized) { status.text = "لم تكتمل التهيئة"; return }
        val link = url.text.toString().trim()
        val allowed = listOf("https://www.youtube.com/", "https://youtube.com/", "https://youtu.be/", "https://m.youtube.com/", "https://music.youtube.com/")
        if (allowed.none { link.startsWith(it) }) {
            status.text = "أدخل رابط يوتيوب صحيحًا يبدأ بـ https://"; return
        }
        val audio = mode.selectedItemPosition == 1
        val playlist = scope.selectedItemPosition == 1
        val h = when (quality.selectedItemPosition) { 1 -> 360; 2 -> 1080; 3 -> 2160; else -> 720 }
        canceled = false
        val processId = "minitube_" + System.currentTimeMillis()
        activeProcessId = processId
        cancelButton.isEnabled = true
        button.isEnabled = false; progress.isIndeterminate = true; progress.progress = 0; status.text = if (playlist) "جارٍ قراءة بيانات قائمة التشغيل... قد يستغرق الأمر عدة دقائق" else "جارٍ الاتصال بالفيديو..."
        thread {
            val temp = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "working_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val request = YoutubeDLRequest(link)
                if (playlist) {
                    request.addOption("--yes-playlist")
                    request.addOption("--ignore-errors")
                } else {
                    request.addOption("--no-playlist")
                }
                request.addOption("--restrict-filenames")
                request.addOption("--socket-timeout", "20")
                                request.addOption("--retries", "3")
                request.addOption("--fragment-retries", "3")
                request.addOption("-o", File(temp, if (playlist) "%(playlist_index)03d-%(id)s-%(title).100B.%(ext)s" else "%(title).150B.%(ext)s").absolutePath)
                if (audio) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                } else {
                    request.addOption("-f", "bv*[height<=${h}]+ba/b[height<=${h}]/best")
                    request.addOption("--merge-output-format", "mp4")
                    request.addOption("--recode-video", "mp4")
                }
                YoutubeDL.getInstance().execute(request, processId) { percent, eta, line ->
                    runOnUiThread {
                        if (canceled) return@runOnUiThread
                        if (percent >= 0f && percent <= 100f) {
                            progress.isIndeterminate = false
                            progress.progress = percent.toInt().coerceIn(0, 100)
                            status.text = if (playlist)
                                "جارٍ تنزيل عناصر القائمة: ${percent.toInt()}% من العنصر الحالي"
                            else "جارٍ تنزيل الفيديو: ${percent.toInt()}%"
                        } else {
                            progress.isIndeterminate = true
                            status.text = if (playlist)
                                "جارٍ قراءة القائمة أو معالجة الملفات... يرجى الانتظار"
                            else "جارٍ تجهيز أو معالجة الملف... يرجى الانتظار"
                        }
                    }
                }
                if (canceled) throw InterruptedException("ألغيت عملية التنزيل")
                val files = temp.listFiles()?.filter { it.isFile && (it.extension.lowercase() == if(audio) "mp3" else "mp4") }.orEmpty()
                if (files.isEmpty()) error("لم يتم إنشاء ملفات جديدة؛ ربما تم تنزيلها سابقًا أو تعذر الوصول إليها")
                runOnUiThread { progress.isIndeterminate = true; status.text = "جارٍ نقل الملفات إلى مجلد التنزيلات..." }
                var saved = 0
                for (file in files) {
                    if (canceled) throw InterruptedException("ألغيت عملية التنزيل")
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, if(audio) "audio/mpeg" else "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiniTube")
                    }
                    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val uri = contentResolver.insert(collection, values) ?: error("فشل إنشاء الملف في التنزيلات")
                    try {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            file.inputStream().use { input ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    if (canceled) throw InterruptedException("ألغيت عملية التنزيل")
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                        } ?: error("فشل فتح ملف الحفظ")
                        saved++
                    } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
                }
                runOnUiThread { progress.isIndeterminate = false; status.text = "تم حفظ $saved ملف في Downloads/MiniTube"; progress.progress = 100 }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.isIndeterminate = false
                    if (canceled) { status.text = "تم إلغاء التنزيل"; return@runOnUiThread }
                    val detail = e.localizedMessage ?: "خطأ غير معروف"
                    status.text = if (detail.contains("403") || detail.contains("Forbidden", true))
                        "رفض يوتيوب رابط التحميل (403). حدث yt-dlp وأعد المحاولة، وقد لا يتوفر هذا الفيديو: $detail"
                    else "تعذر التنزيل: $detail"
                }
            } finally {
                temp.deleteRecursively()
                activeProcessId = null
                runOnUiThread { button.isEnabled = true; cancelButton.isEnabled = false }
            }
        }
    }
    private fun openDownloadedFiles() {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val names = mutableListOf<String>()
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val selection = MediaStore.Downloads.RELATIVE_PATH + " LIKE ?"
        val args = arrayOf(Environment.DIRECTORY_DOWNLOADS + "/MiniTube/%")
        try {
            contentResolver.query(collection, projection, selection, args, MediaStore.Downloads.DATE_ADDED + " DESC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(nameIndex))
                    uris.add(ContentUris.withAppendedId(collection, cursor.getLong(idIndex)))
                }
            }
            if (names.isEmpty()) {
                Toast.makeText(this, "لا توجد ملفات محفوظة بعد", Toast.LENGTH_LONG).show()
                return
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("ملفاتي - MiniTube")
                .setItems(names.toTypedArray()) { _, which ->
                    val fileUri = uris[which]
                    val mime = if (names[which].endsWith(".mp3", true)) "audio/mpeg" else "video/mp4"
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { startActivity(Intent.createChooser(view, "فتح الملف باستخدام")) }
                    catch (e: Exception) { Toast.makeText(this, "لا يوجد تطبيق لفتح هذا الملف", Toast.LENGTH_LONG).show() }
                }
                .setNegativeButton("إغلاق", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر عرض الملفات: " + e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

}