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
        label("Rock Block Bot v3.8 — tall-piece calibration",22f)
        label("1. Enable overlays and accessibility. 2. Start capture and choose SHARE A SINGLE APP: Rock Block Saga. 3. With three real tray pieces, press PREVIEW (not RESUME). Check the entire detected shape and all green placement cells. Blue ring = finger pickup, red crosshair = finger release. The preview lists both screen-pixel coordinates. 4. Tap NEXT MOVE only after checking the preview; wait at least seven seconds for verification. Press PREVIEW again before any other move. STOP after the result is settled and inspect Last placement check and Last touch path. 5. For calibration, SHOW CAL opens global X/Y and TALL Y buttons. Global Y now affects every piece. TALL Y controls only tall pieces and starts at -240px relative to global Y (for saved Y=484, effective tall Y starts at 244px). TALL Y + moves the finger release downward by 20px; TALL Y - moves it upward. These settings are experimental and do not guarantee exact placement. Never move a piece with an incorrect shape or incomplete preview, and never retry an unverified move blindly. No unattended play.")
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
