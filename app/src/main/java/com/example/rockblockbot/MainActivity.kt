package com.example.rockblockbot

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Diagnostic v2: observe game pixels, don't execute unattended gameplay. */
class MainActivity : Activity() {
    private lateinit var report: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            report.text = getSharedPreferences("capture", MODE_PRIVATE)
                .getString("report", "No game frames analyzed yet.")
            handler.postDelayed(this, 1500)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        fun info(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text; textSize = size; setPadding(0, 12, 0, 12)
            layout.addView(this)
        }
        info("Rock Block Bot — recognition test", 23f)
        info("Step 1: Start capture and choose ENTIRE SCREEN in Android's permission dialog. Step 2: Switch to Rock Block Saga and show an active puzzle. Step 3: Return here to review detection. No blocks will be moved in this version.")
        layout.addView(Button(this).apply {
            text = "Start screen recognition"
            setOnClickListener {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(), 100)
            }
        })
        layout.addView(Button(this).apply {
            text = "Stop screen recognition"
            setOnClickListener { startService(Intent(this@MainActivity, CaptureService::class.java).setAction("STOP")) }
        })
        layout.addView(Button(this).apply {
            text = "Accessibility settings (future dragging)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        info("Latest recognition result:", 19f)
        report = TextView(this).apply { textSize = 15f; setTextIsSelectable(true); setPadding(8,12,8,12) }
        layout.addView(report)
        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java).apply {
                action = "START"
                putExtra("code", resultCode)
                putExtra("data", data)
            }
            startForegroundService(i)
        }
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
