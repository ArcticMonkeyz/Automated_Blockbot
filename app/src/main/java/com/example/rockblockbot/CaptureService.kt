package com.example.rockblockbot

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/** A single, explicitly user-triggered placement. No unattended loop. */
class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: LinearLayout? = null
    private var wm: WindowManager? = null
    private var moveButton: Button? = null
    private var lastAnalyzed = 0L
    private var latest: BoardRecognizer.Frame? = null
    private var latestAt = 0L
    private var stableSignature: String? = null
    private var stableCount = 0
    private var inProgress = false
    private var status = "Waiting for an active game board."
    private var stopped = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { handler.post { stopSelf() } }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("capture", "Rock Block screen recognition",NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    private fun publish() {
        val f = latest
        val time = if (latestAt > 0) SimpleDateFormat("HH:mm:ss",Locale.US).format(Date(System.currentTimeMillis() - (SystemClock.elapsedRealtime()-latestAt))) else "none"
        val text = "Status: $status\nLast game frame: $time | stable frames: $stableCount\n\n" +
            (f?.report() ?: "No game board captured yet.") + "\n\nOne-move test only. STOP removes floating controls."
        getSharedPreferences("capture",MODE_PRIVATE).edit().putString("report",text).apply()
    }
    private fun setStatus(s: String) { status=s; publish() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent?.action != "START" || projection != null) return START_NOT_STICKY
        val notification = Notification.Builder(this,"capture")
            .setContentTitle("Rock Block Bot: one-move test")
            .setContentText("Floating MOVE 1 / STOP controls active")
            .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build()
        startForeground(22,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("data",Intent::class.java)
                   else @Suppress("DEPRECATION") (intent.getParcelableExtra("data") as? Intent)
        if (data == null) { setStatus("Missing screen-capture permission."); stopSelf(); return START_NOT_STICKY }
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),data)
            projection?.registerCallback(callback,handler)
            val metrics = resources.displayMetrics
            val w = metrics.widthPixels; val h = metrics.heightPixels
            reader = ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
            reader?.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now-lastAnalyzed < 450L) return@setOnImageAvailableListener
                    lastAnalyzed=now
                    val plane=image.planes[0]
                    val stride=plane.pixelStride
                    val paddedWidth=w+(plane.rowStride-stride*w)/stride
                    val bmp=Bitmap.createBitmap(paddedWidth,h,Bitmap.Config.ARGB_8888)
                    try {
                        plane.buffer.rewind(); bmp.copyPixelsFromBuffer(plane.buffer)
                        val cropped=Bitmap.createBitmap(bmp,0,0,w,h)
                        try {
                            val frame=BoardRecognizer.analyze(cropped)
                            if (frame!=null) {
                                latest=frame; latestAt=now
                                if (frame.signature==stableSignature) stableCount++
                                else { stableSignature=frame.signature; stableCount=1 }
                                if (!inProgress) publish()
                            }
                        } finally { cropped.recycle() }
                    } finally { bmp.recycle() }
                } catch (e: Exception) { setStatus("Capture error: ${e.message}") }
                finally { image.close() }
            },handler)
            display=projection?.createVirtualDisplay("RockBlockRead",w,h,metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
            createOverlay()
            setStatus("Capture running. Open Rock Block Saga; wait 2 seconds, then tap MOVE 1.")
        } catch (e: Exception) { setStatus("Start failed: ${e.message}"); stopSelf() }
        return START_NOT_STICKY
    }
    private fun createOverlay() {
        if (!Settings.canDrawOverlays(this)) { setStatus("Floating controls permission missing."); return }
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setBackgroundColor(0xCC222222.toInt()) }
        moveButton=Button(this).apply { text="MOVE 1"; setOnClickListener { attemptOneMove() } }
        row.addView(moveButton)
        row.addView(Button(this).apply { text="STOP"; setOnClickListener { stopSelf() } })
        val params=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=8; y=65 }
        wm?.addView(row,params)
        overlay=row
    }
    private fun attemptOneMove() {
        if (inProgress) return
        val frame=latest
        val now=SystemClock.elapsedRealtime()
        if (TouchService.current==null) { setStatus("Enable Rock Block Bot in Android Accessibility settings first."); return }
        if (frame==null || frame.recognized!=3 || stableCount<2 || now-latestAt>4000) {
            setStatus("No recent stable 8x8 board with all three pieces. No move made."); return
        }
        if (frame.width>=frame.height) { setStatus("Portrait game capture required. No move made."); return }
        inProgress=true; moveButton?.isEnabled=false
        setStatus("Calculating a safe one-piece placement...")
        worker.execute {
            try {
                val pieces=frame.pieces.map { it!!.asPiece() }
                val plan=Solver.solve(frame.board,pieces)
                handler.post {
                    if (stopped) return@post
                    if (!plan.complete || plan.moves.isEmpty()) {
                        finish("No complete three-piece plan found. No move made."); return@post
                    }
                    if (SystemClock.elapsedRealtime()-latestAt>5000 || latest?.signature!=frame.signature) {
                        finish("Game board changed while planning. No move made."); return@post
                    }
                    val move=plan.moves.first()
                    val piece=pieces[move.pieceIndex]
                    val observed=frame.pieces[move.pieceIndex]!!
                    val screen=Point()
                    @Suppress("DEPRECATION")
                    wm!!.defaultDisplay.getRealSize(screen)
                    val scaleX=screen.x/BoardRecognizer.REF_WIDTH
                    val scaleY=screen.y/BoardRecognizer.REF_HEIGHT
                    // Game preview appears about 95 reference pixels above finger position.
                    // This is an experimental offset and must be calibrated on device.
                    val fromX=observed.centerX*scaleX
                    val fromY=observed.centerY*scaleY
                    val targetX=(163f+move.col*55f+(piece.width-1)*27.5f)*scaleX
                    val targetY=(454f+move.row*55f+(piece.height-1)*27.5f+95f)*scaleY
                    val expected=Solver.placeAndClear(frame.board,piece,move.row,move.col)
                    setStatus("Testing piece ${move.pieceIndex+1} to row ${move.row+1}, col ${move.col+1}. One drag only...")
                    val accepted=TouchService.current?.drag(fromX,fromY,targetX,targetY) { success ->
                        handler.post {
                            if (!success) { finish("Android cancelled the drag. No further moves."); return@post }
                            handler.postDelayed({
                                val actual=latest
                                val verified=actual!=null && SystemClock.elapsedRealtime()-latestAt<3000 && actual.board==expected
                                finish(if (verified) "Board matches predicted result after one drag. STOP and inspect the game." else
                                    "Drag finished, but board did NOT match prediction. STOP and inspect the game. No retry.")
                            },1700)
                        }
                    } ?: false
                    if (!accepted) finish("Accessibility service rejected gesture. No move made.")
                }
            } catch (e: Exception) { handler.post { finish("Solver error: ${e.message}. No further moves.") } }
        }
    }
    private fun finish(message: String) { inProgress=false; moveButton?.isEnabled=true; setStatus(message) }
    override fun onDestroy() {
        stopped=true
        try { overlay?.let { wm?.removeView(it) } } catch (_: Exception) {}
        overlay=null; moveButton=null
        reader?.setOnImageAvailableListener(null,null)
        display?.release(); display=null
        reader?.close(); reader=null
        projection?.unregisterCallback(callback)
        projection?.stop(); projection=null
        worker.shutdownNow()
        setStatus("Stopped. No automatic moves will occur.")
        super.onDestroy()
    }
}
