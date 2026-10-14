package com.example.rockblockbot

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics

class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastAnalyzed = 0L
    private var stopping = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { handler.post { stopSelf() } }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("capture", "Rock Block screen recognition", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent?.action != "START" || projection != null) return START_NOT_STICKY
        val notification = Notification.Builder(this, "capture")
            .setContentTitle("Rock Block Bot is reading the screen")
            .setContentText("Return to the app to see the recognition report")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build()
        startForeground(22, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("data", Intent::class.java)
                   else intent.getParcelableExtra("data") as? Intent
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(intent.getIntExtra("code", Activity.RESULT_CANCELED), data)
            projection?.registerCallback(callback, handler)
            val metrics = resources.displayMetrics
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            reader?.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAnalyzed < 1000L) return@setOnImageAvailableListener
                    lastAnalyzed = now
                    val plane = image.planes[0]
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val bmp = Bitmap.createBitmap(w + (rowStride - pixelStride * w) / pixelStride, h, Bitmap.Config.ARGB_8888)
                    plane.buffer.rewind()
                    bmp.copyPixelsFromBuffer(plane.buffer)
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
                    val result = BoardRecognizer.analyze(cropped)
                    getSharedPreferences("capture", MODE_PRIVATE).edit().putString("report", result).apply()
                    cropped.recycle(); bmp.recycle()
                } catch (e: Exception) {
                    getSharedPreferences("capture", MODE_PRIVATE).edit().putString("report", "Capture error: ${e.message}").apply()
                } finally { image.close() }
            }, handler)
            display = projection?.createVirtualDisplay("RockBlockRead", w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
        } catch (e: Exception) {
            getSharedPreferences("capture", MODE_PRIVATE).edit().putString("report", "Start failed: ${e.message}").apply()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        if (!stopping) {
            stopping = true
            reader?.setOnImageAvailableListener(null, null)
            display?.release(); display = null
            reader?.close(); reader = null
            projection?.unregisterCallback(callback)
            projection?.stop(); projection = null
        }
        super.onDestroy()
    }
}
