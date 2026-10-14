package com.example.rockblockbot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var report: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            report.text = getSharedPreferences("capture",MODE_PRIVATE).getString("report","No game frames yet.")
            handler.postDelayed(this,1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26,24,26,24)
        }
        fun label(value: String, size: Float = 16f) {
            layout.addView(TextView(this).apply { text=value; textSize=size; setPadding(0,12,0,12) })
        }
        fun button(value: String, fn: () -> Unit) {
            layout.addView(Button(this).apply { text=value; setOnClickListener { fn() } })
        }
        label("Rock Block Bot v4.0 — per-piece calibration",22f)
        label("1. Enable overlay/accessibility, then SHARE A SINGLE APP: Rock Block Saga. " +
            "2. PREVIEW with all real tray pieces recognized, and compare the entire shape and green squares. " +
            "3. Open SHOW CAL: global X/Y affect all pieces; TALL Y adjusts all tall pieces; " +
            "PIECE X/Y adjusts ONLY the currently previewed shape+color. Per-piece settings are saved " +
            "across sessions. An empirically measured correction is preloaded for the purple five-high strip, " +
            "while the previously successful yellow L calibration is preserved. " +
            "4. Check the blue pickup ring, red release crosshair and exact coordinates; press NEXT MOVE " +
            "only if the shape and target are correct. Wait at least 8 seconds for VERIFIED/UNVERIFIED " +
            "in the floating overlay. If UNVERIFIED, do not retry blindly. " +
            "5. STOP then inspect Last placement check. Unexpected line clears can now be diagnosed " +
            "as a piece-specific offset suggestion; suggestions are NOT applied automatically. " +
            "Do not use RESUME if all three real pieces are visible. No unattended play.")
        button("Allow floating controls") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
        }
        button("Enable accessibility service") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("Start screen recognition + floating controls") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this,"Allow floating controls first",Toast.LENGTH_LONG).show()
            } else {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(),100)
            }
        }
        button("STOP and remove floating controls") {
            startService(Intent(this,CaptureService::class.java).setAction("STOP"))
        }
        label("Latest game report and test status:",19f)
        report = TextView(this).apply { textSize=15f; setTextIsSelectable(true); setPadding(6,12,6,12) }
        layout.addView(report)
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
    }
    @Deprecated("Android activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==100 && resultCode==RESULT_OK && data!=null) {
            startForegroundService(Intent(this,CaptureService::class.java).apply {
                action="START"; putExtra("code",resultCode); putExtra("data",data)
            })
        }
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
