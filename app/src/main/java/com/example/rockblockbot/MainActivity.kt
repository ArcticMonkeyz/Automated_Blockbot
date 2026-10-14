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
        label("Rock Block Bot v4.4 — shadow-guided drag (TRIAL)",22f)
        label("1. Enable floating overlay/accessibility, then SHARE A SINGLE APP: Rock Block Saga. " +
            "2. PREVIEW with all tray pieces recognized. Confirm the entire shape, numbered " +
            "source tiles, and GREEN planned board cells. The red crosshair is only the FIRST " +
            "GUESS for the finger, not the game drop position. " +
            "3. NEXT MOVE holds the virtual finger down, looks for the game's TRANSLUCENT " +
            "same-color placement shadow, adjusts the held drag until that shadow matches " +
            "the planned green cells in two separate captures, THEN releases and checks the board. " +
            "The bot does not intentionally release at an unverified guess. If tracking fails, " +
            "it attempts to return the piece to the tray and BLOCKS further moves. " +
            "4. Be patient: a move can take up to about 20 seconds for several corrections " +
            "plus 7 seconds for final verification. Do not touch the game during a test. " +
            "STOP during a held drag requests a return-to-tray; press STOP again after it finishes. " +
            "5. If VERIFIED, PREVIEW again before each NEXT MOVE. If UNVERIFIED, STOP and send " +
            "Last game shadow tracking and Last placement check. " +
            "Keep existing per-piece calibration unless needed for the first guess. " +
            "This is EXPERIMENTAL: game ghosts may be occluded, colors vary, Android may " +
            "interrupt held gestures, and tray return is best-effort only. No unattended play.")
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
