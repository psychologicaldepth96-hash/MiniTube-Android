package com.minitube.downloader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Explicit user authentication before opening from a dial-code notification or app link. */
class UnlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val field = EditText(this).apply {
            hint = "رمز الدخول"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            isSingleLine = true
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 8)
            addView(TextView(this@UnlockActivity).apply {
                text = "أدخل رمز MiniTube لفتح التطبيق"
                textSize = 18f
            })
            addView(field)
        }
        AlertDialog.Builder(this)
            .setTitle("فتح MiniTube")
            .setView(layout)
            .setPositiveButton("فتح") { _, _ ->
                if (AccessCode.matches(this, field.text.toString())) {
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    Toast.makeText(this, "رمز غير صحيح", Toast.LENGTH_LONG).show()
                }
                finish()
            }
            .setNegativeButton("إلغاء") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
