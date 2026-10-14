package com.example.rockblockbot

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private val exportReportRequest = 212

    /** Text-only export: no modifications to the working game's image or gesture loop. */
    private fun exportReport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "RockBlockBot-rollback-report.zip")
        }
        startActivityForResult(intent, exportReportRequest)
    }

    private fun saveReport(uri: Uri) {
        try {
            val text = getSharedPreferences("capture", MODE_PRIVATE)
                .getString("report", "No game report yet.") ?: "No game report yet."
            val output = contentResolver.openOutputStream(uri)
                ?: error("Cannot open chosen file")
            output.use { stream ->
                ZipOutputStream(stream).use { zip ->
                    zip.putNextEntry(ZipEntry("report.txt"))
                    zip.write(("Version: 4.4.13 (guarded 4.4.4 placement engine)\n" +
                        "Calibration namespace: guard_v4413; old learned grips ignored; failed probes invalidate saved grips.\n\n" +
                        text).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            Toast.makeText(this, "Report ZIP saved to phone", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Report export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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
        label("Rock Block Bot v4.4.13 — guarded v4.4.4 placement engine (TRIAL)",22f)
        label("1. Enable overlay/accessibility and SHARE A SINGLE APP: Rock Block Saga. " +
            "2. PREVIEW only when all real pieces are recognized. The numbered cyan " +
            "markers must match each visible tray tile and the green cells are the intended placement. " +
            "3. The compact top dock never needs to cover the board. The STOP label no " +
            "longer wraps; tap INFO for coordinates or CAL to cycle Global/Tall/Piece offsets. " +
            "Info and adjustments close automatically during a drag. " +
            "4. Press CAL DRAG for the selected piece. It uses ONE uninterrupted touch " +
            "gesture: pick up, travel to the board, measure the game's colored translucent " +
            "shadow during a longer 1.8-second board dwell, then return toward the tray " +
            "before releasing. This is a CALIBRATION " +
            "attempt, NOT a requested placement. The piece might still be placed unexpectedly; " +
            "wait for the result and inspect the board and tray. " +
            "5. If the report says CALIBRATED and the piece returned, press PREVIEW again. " +
            "NEXT MOVE is enabled for that shape/color and uses ONE normal gesture at the " +
            "measured endpoint. This is a trial, NOT live visual feedback during the release. " +
            "6. After NEXT MOVE, wait for VERIFIED or UNVERIFIED and inspect the board. " +
            "Any uncertain probe or failed placement blocks blind continuation. " +
            "Do not press STOP or touch the game while a gesture is moving. " +
            "7. Restored v4.4.4 recognition/gesture engine; guarded learned-grip reuse. Experimental saved " +
            "drag calibrations are ignored; CAL DRAG again for each shape+color. " +
            "Manual global/tall/per-piece adjustments remain. " +
            "No unattended play. Android may interrupt even a single gesture; return-to-tray " +
            "is best-effort. Share Last game shadow tracking and Last placement check if it fails.")
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
        button("RESET MANUAL TUNING (optional)") {
            AlertDialog.Builder(this)
                .setTitle("Reset manual calibration?")
                .setMessage("First STOP screen recognition. This clears saved global/tall/" +
                    "per-piece tuning and all v4.4.13 learned grips. It cannot undo a game move. " +
                    "Only use this if old manual settings may be corrupt; recalibration is required.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset") { _, _ ->
                    val a = getSharedPreferences("calibration", MODE_PRIVATE).edit().clear().commit()
                    val b = getSharedPreferences("piece_calibration", MODE_PRIVATE).edit().clear().commit()
                    val c = getSharedPreferences("shadow_probe_guard_v4413", MODE_PRIVATE).edit().clear().commit()
                    Toast.makeText(this,
                        if (a && b && c) "Tuning reset. Restart recognition and CAL DRAG again."
                        else "Reset incomplete. STOP, then try again.",
                        Toast.LENGTH_LONG).show()
                }.show()
        }
        button("EXPORT LAST REPORT ZIP (text only)") { exportReport() }
        label("Latest game report and test status:",19f)
        report = TextView(this).apply { textSize=15f; setTextIsSelectable(true); setPadding(6,12,6,12) }
        layout.addView(report)
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
    }
    @Deprecated("Android activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==exportReportRequest) {
            if (resultCode==RESULT_OK && data?.data!=null) saveReport(data.data!!)
            return
        }
        if (requestCode==100 && resultCode==RESULT_OK && data!=null) {
            startForegroundService(Intent(this,CaptureService::class.java).apply {
                action="START"; putExtra("code",resultCode); putExtra("data",data)
            })
        }
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
