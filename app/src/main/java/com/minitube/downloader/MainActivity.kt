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
import android.provider.DocumentsContract
import android.widget.*
import android.view.ViewGroup
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var quality: Spinner
    private lateinit var mode: Spinner
    private lateinit var audioSettings: LinearLayout
    private lateinit var videoSettings: LinearLayout
    private lateinit var audioFormat: Spinner
    private lateinit var audioBitrate: Spinner
    private lateinit var videoFormat: Spinner
    private lateinit var progressText: TextView
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
        window.statusBarColor = Color.rgb(12, 27, 49)
        window.navigationBarColor = Color.rgb(247, 249, 252)
        val bg = Color.rgb(247, 249, 252)
        val muted = Color.rgb(105, 120, 143)
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        fun shape(color: Int, radius: Int = 18, stroke: Int = Color.TRANSPARENT): GradientDrawable =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radius).toFloat()
                if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
            }
        fun label(text: String, size: Float = 14f): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(navy)
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }
        fun section(text: String): TextView = label(text, 16f).apply {
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(7))
        }
        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.WHITE, 20, Color.rgb(230, 236, 244))
            setPadding(dp(18), dp(14), dp(18), dp(17))
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.bottomMargin = dp(14)
            layoutParams = lp
        }
        fun addCard(root: LinearLayout, item: LinearLayout) {
            root.addView(item, item.layoutParams ?: LinearLayout.LayoutParams(-1, -2))
        }
        fun choice(items: List<String>): Spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            background = shape(Color.rgb(245, 248, 252), 12)
            setPadding(dp(12), dp(6), dp(8), dp(6))
            minimumHeight = dp(48)
        }
        fun action(text: String, primary: Boolean, onTap: () -> Unit): Button =
            Button(this).apply {
                this.text = text
                textSize = 14f
                isAllCaps = false
                setTextColor(if (primary) Color.WHITE else navy)
                background = shape(if (primary) navy else Color.rgb(232, 240, 247), 14)
                setOnClickListener { onTap() }
            }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(15, 35, 63), Color.rgb(33, 74, 113))).apply {
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(21), dp(22), dp(21), dp(22))
        }
        hero.addView(label("MiniTube", 30f).apply {
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        hero.addView(label("تنزيل المحتوى المسموح به من منصات التواصل", 13f).apply {
            setTextColor(Color.rgb(202, 224, 239))
        })
        addCard(root, hero)
        val linkCard = card()
        linkCard.addView(section("رابط المحتوى"))
        url = EditText(this).apply {
            hint = "الصق رابط فيديو من منصة اجتماعية"
            textSize = 14f
            setSingleLine(true)
            textDirection = View.TEXT_DIRECTION_LTR
            background = shape(Color.rgb(245, 248, 252), 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        linkCard.addView(url, LinearLayout.LayoutParams(-1, dp(52)))
        val paste = action("لصق الرابط", false) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) url.setText(text)
            else Toast.makeText(this, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
        }
        linkCard.addView(paste)
        linkCard.addView(label("YouTube • Facebook • Instagram • X • TikTok • Snapchat وغيرها", 11f))
        linkCard.addView(section("نطاق التنزيل"))
        scope = choice(listOf("فيديو واحد", "قائمة تشغيل كاملة (Playlist)"))
        linkCard.addView(scope)
        addCard(root, linkCard)
        val formatCard = card()
        formatCard.addView(section("اختر نوع الملف"))
        mode = choice(listOf("فيديو", "صوت فقط"))
        formatCard.addView(mode)
        videoSettings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        videoSettings.addView(label("دقة الفيديو"))
        quality = choice(listOf("360p", "720p", "1080p", "2160p (4K)"))
        quality.setSelection(1)
        videoSettings.addView(quality)
        videoSettings.addView(label("الصيغة"))
        videoFormat = choice(listOf("MP4", "MKV"))
        videoSettings.addView(videoFormat)
        formatCard.addView(videoSettings)
        audioSettings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        audioSettings.addView(label("صيغة الصوت"))
        audioFormat = choice(listOf("MP3", "M4A"))
        audioSettings.addView(audioFormat)
        audioSettings.addView(label("الجودة المستهدفة"))
        audioBitrate = choice(listOf("128 kbps", "192 kbps", "320 kbps"))
        audioSettings.addView(audioBitrate)
        formatCard.addView(audioSettings)
        mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                videoSettings.visibility = if (position == 0) View.VISIBLE else View.GONE
                audioSettings.visibility = if (position == 1) View.VISIBLE else View.GONE
            }
        }
        addCard(root, formatCard)
        val controls = card()
        controls.addView(section("التحكم في التنزيل"))
        button = action("↓ بدء التنزيل", true) { download() }
        controls.addView(button, LinearLayout.LayoutParams(-1, dp(54)))
        cancelButton = action("إلغاء التنزيل", false) {
            canceled = true
            cancelButton.isEnabled = false
            status.text = "جارٍ إيقاف التنزيل..."
            startService(Intent(this@MainActivity, DownloadService::class.java).setAction(DownloadService.CANCEL))
        }
        cancelButton.isEnabled = false
        controls.addView(cancelButton, LinearLayout.LayoutParams(-1, dp(48)))
        addCard(root, controls)
        val progressCard = card()
        progressCard.addView(section("حالة التنزيل"))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        progressCard.addView(progress, LinearLayout.LayoutParams(-1, dp(12)))
        progressText = label("لم يبدأ التنزيل", 16f).apply { setTypeface(null, android.graphics.Typeface.BOLD) }
        progressCard.addView(progressText)
        status = label("جارٍ تجهيز المحرك...", 12f).apply { setTextColor(muted) }
        progressCard.addView(status)
        addCard(root, progressCard)
        val filesCard = card()
        filesCard.addView(section("ملفاتي"))
        filesCard.addView(label("كل الملفات تُحفظ في Downloads/MiniTube", 12f).apply { setTextColor(muted) })
        filesCard.addView(action("فتح مجلد التنزيلات ↗", false) { openDownloadFolder() },
            LinearLayout.LayoutParams(-1, dp(50)))
        addCard(root, filesCard)
        root.addView(label("للمحتوى الذي تملك حق تنزيله فقط. يُفضّل إبقاء التطبيق مفتوحًا أثناء التحميل.", 11f).apply {
            setTextColor(muted)
        })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        if (intent?.action == Intent.ACTION_SEND) url.setText(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")
        button.isEnabled = false
        thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                runOnUiThread { status.text = "جارٍ تحديث محرك yt-dlp لتوافق يوتيوب..." }
                var updateMessage = "المحرك جاهز"
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
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
        if (!SafeMediaUrl.isAllowed(link)) {
            status.text = "رابط غير صالح أو غير مدعوم. استخدم HTTPS من منصة موثوقة."
            return
        }
        val audio = mode.selectedItemPosition == 1
        val playlist = scope.selectedItemPosition == 1
        if (playlist && !SafeMediaUrl.isYoutube(link)) {
            status.text = "القوائم الكاملة متاحة حاليًا ليوتيوب فقط؛ اختر فيديو واحد لهذا الرابط"
            return
        }
        val h = when (quality.selectedItemPosition) { 0 -> 360; 1 -> 720; 2 -> 1080; else -> 2160 }
        val audioExt = if (audioFormat.selectedItemPosition == 0) "mp3" else "m4a"
        val videoExt = if (videoFormat.selectedItemPosition == 0) "mp4" else "mkv"
        val bitrate = listOf("128K", "192K", "320K")[audioBitrate.selectedItemPosition]
        val finalExtension = if (audio) audioExt else videoExt
        if (DownloadService.running) {
            status.text = "توجد عملية تنزيل قيد التشغيل بالفعل"
            return
        }
        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.START
            putExtra("url", link)
            putExtra("playlist", playlist)
            putExtra("audio", audio)
            putExtra("extension", finalExtension)
            putExtra("height", h)
            putExtra("bitrate", bitrate)
        }
        try {
            startForegroundService(intent)
            status.text = "بدأ التنزيل بالخلفية؛ يمكنك إغلاق الشاشة"
            progressText.text = "سيظهر التقدم في إشعارات الهاتف"
            button.isEnabled = false
            cancelButton.isEnabled = true
        } catch (e: Exception) {
            status.text = "تعذر تشغيل التنزيل بالخلفية: " + (e.localizedMessage ?: "خطأ")
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (DownloadService.running) {
                button.isEnabled = false
                cancelButton.isEnabled = true
                val pct = DownloadService.percent
                progress.isIndeterminate = pct < 0
                if (pct >= 0) progress.progress = pct
                val eta = DownloadService.etaSeconds
                progressText.text = "محفوظ " + DownloadService.completed + "/" + DownloadService.total +
                    (if (pct >= 0) " • " + pct + "%" else " • جارٍ التجهيز") +
                    (if (eta >= 0) " • متبقي " + eta + " ثانية" else "")
                status.text = DownloadService.stage
            } else if (initialized) {
                button.isEnabled = true
                cancelButton.isEnabled = false
                if (DownloadService.lastError.isNotBlank()) {
                    progress.isIndeterminate = false
                    status.text = "سبب فشل آخر تنزيل: " + DownloadService.lastError
                } else if (DownloadService.total > 0) {
                    progress.isIndeterminate = false
                    status.text = DownloadService.stage
                }
            }
            handler.postDelayed(this, 1000)
        }
    }
    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }
    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun openDownloadFolder() {
        // Android's file picker can open near the folder; the user chooses the folder in the system UI.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val initial = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Download/MiniTube")
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
        }
        try { startActivity(intent) }
        catch (e: Exception) {
            Toast.makeText(this, "افتح مدير الملفات ثم Downloads ثم MiniTube", Toast.LENGTH_LONG).show()
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