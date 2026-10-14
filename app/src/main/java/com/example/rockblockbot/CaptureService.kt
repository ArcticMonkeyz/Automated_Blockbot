package com.example.rockblockbot

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.view.View
import android.widget.TextView
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

/** Guided multi-move placement: user must PREVIEW and confirm each move. */
class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: LinearLayout? = null
    private var wm: WindowManager? = null
    private var moveButton: Button? = null
    private var previewButton: Button? = null
    private var offsetLabel: TextView? = null
    private var marker: CalibrationOverlay? = null
    private var prepared: Prepared? = null
    private var preparing = false
    private var xOffset = 0
    private var yOffset = 0
    private data class Prepared(
        val frame: BoardRecognizer.Frame,
        val move: Move,
        val piece: Piece,
        val observed: BoardRecognizer.ObservedPiece,
        val expected: Long,
        val time: Long
    )
    private var lastAnalyzed = 0L
    private var latest: BoardRecognizer.Frame? = null
    private var latestAt = 0L
    private var stableSignature: String? = null
    private var stableCount = 0
    private var inProgress = false
    private var moveCount = 0
    private var sessionBlocked = false
    private var lastGestureOutcome = "Not tested in this session"
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
        val text = "Status: $status\nCalibration: X ${xOffset}px, Y ${yOffset}px\nLast gesture result: $lastGestureOutcome\nLast game frame: $time | stable frames: $stableCount\n\n" +
            (f?.report() ?: "No game board captured yet.") + "\n\nUser-confirmed moves only. STOP removes floating controls."
        getSharedPreferences("capture",MODE_PRIVATE).edit().putString("report",text).apply()
    }
    private fun setStatus(s: String) { status=s; publish() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent?.action != "START" || projection != null) return START_NOT_STICKY
        val notification = Notification.Builder(this,"capture")
            .setContentTitle("Rock Block Bot: guided moves")
            .setContentText("Floating PREVIEW / NEXT MOVE / STOP controls active")
            .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build()
        startForeground(22,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val prefs = getSharedPreferences("calibration", MODE_PRIVATE)
        xOffset = prefs.getInt("x", 0)
        yOffset = prefs.getInt("y", 0)
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
                    if (now-lastAnalyzed < 450L || (prepared != null && !inProgress)) return@setOnImageAvailableListener
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
            setStatus("Capture running. Open Rock Block Saga, tap PREVIEW, then NEXT MOVE. Re-preview after every verified move.")
        } catch (e: Exception) { setStatus("Start failed: ${e.message}"); stopSelf() }
        return START_NOT_STICKY
    }
    private fun createOverlay() {
        if (!Settings.canDrawOverlays(this)) { setStatus("Floating controls permission missing."); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD222222.toInt())
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        previewButton = Button(this).apply { text = "PREVIEW"; setOnClickListener { prepareMove() } }
        moveButton = Button(this).apply { text = "NEXT MOVE"; isEnabled = false; setOnClickListener { attemptOneMove() } }
        actions.addView(previewButton)
        actions.addView(moveButton)
        actions.addView(Button(this).apply { text = "STOP"; setOnClickListener { stopSelf() } })
        panel.addView(actions)
        val adjustments = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun adjustButton(label: String, dx: Int, dy: Int) {
            adjustments.addView(Button(this).apply {
                text = label
                setOnClickListener { adjust(dx,dy) }
            })
        }
        adjustButton("X -",-20,0); adjustButton("X +",20,0)
        adjustButton("Y -",0,-20); adjustButton("Y +",0,20)
        panel.addView(adjustments)
        offsetLabel = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()); textSize = 13f }
        panel.addView(offsetLabel)
        updateOffsetLabel()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 8; y = 45 }
        wm?.addView(panel,params)
        overlay = panel
    }

    private fun updateOffsetLabel() {
        offsetLabel?.text = "Touch endpoint offset: X ${xOffset}px, Y ${yOffset}px (20px steps)"
    }

    private fun adjust(dx: Int, dy: Int) {
        if (inProgress || sessionBlocked) return
        xOffset = (xOffset + dx).coerceIn(-240,240)
        yOffset = (yOffset + dy).coerceIn(-400,400)
        getSharedPreferences("calibration",MODE_PRIVATE).edit()
            .putInt("x",xOffset).putInt("y",yOffset).apply()
        updateOffsetLabel()
        updateMarker()
        setStatus("Offsets updated. PREVIEW is visual only; NEXT MOVE performs one confirmed drag.")
    }

    private fun screenSize(): Point = Point().also { point ->
        @Suppress("DEPRECATION")
        wm!!.defaultDisplay.getRealSize(point)
    }

    private fun gestureCoords(plan: Prepared): FloatArray {
        val screen = screenSize()
        val sx = screen.x / BoardRecognizer.REF_WIDTH
        val sy = screen.y / BoardRecognizer.REF_HEIGHT
        val fromX = plan.observed.centerX * sx
        val fromY = plan.observed.centerY * sy
        val toX = (163f + plan.move.col*55f + (plan.piece.width-1)*27.5f) * sx + xOffset
        // No assumed 95px lift: use a measured adjustment in SCREEN pixels.
        val toY = (454f + plan.move.row*55f + (plan.piece.height-1)*27.5f) * sy + yOffset
        return floatArrayOf(fromX,fromY,toX,toY)
    }

    private fun updateMarker() {
        val plan = prepared ?: return
        val screen = screenSize()
        val coords = gestureCoords(plan)
        if (marker == null) {
            marker = CalibrationOverlay(this)
            val params = WindowManager.LayoutParams(
                screen.x,screen.y,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }
            wm?.addView(marker,params)
        }
        marker?.setPlan(
            coords, plan.move.row, plan.move.col, plan.piece.normalized,
            screen.x / BoardRecognizer.REF_WIDTH, screen.y / BoardRecognizer.REF_HEIGHT
        )
    }

    private fun removeMarker() {
        try { marker?.let { wm?.removeView(it) } } catch (_: Exception) {}
        marker = null
    }

    private fun prepareMove() {
        if (preparing || inProgress || sessionBlocked) return
        val frame = latest
        if (frame == null || frame.recognized !in 1..3 || stableCount < 2 ||
            SystemClock.elapsedRealtime() - latestAt > 4000L || frame.width >= frame.height) {
            setStatus("Need a recent stable portrait board and at least one recognized piece.")
            return
        }
        preparing = true
        previewButton?.isEnabled = false
        setStatus("Calculating a visual preview (no touch gesture)...")
        worker.execute {
            try {
                val available = frame.pieces.mapIndexedNotNull { i,p -> p?.let { i to it.asPiece() } }
                val plan = Solver.solveVisible(frame.board,available)
                handler.post {
                    preparing = false
                    if (stopped) return@post
                    previewButton?.isEnabled = true
                    if (!plan.complete || plan.moves.isEmpty()) {
                        setStatus("No complete plan for the remaining visible pieces. No move available.")
                        return@post
                    }
                    if (latest?.signature != frame.signature ||
                        SystemClock.elapsedRealtime()-latestAt > 6000L) {
                        setStatus("Game changed during planning. Tap PREVIEW again.")
                        return@post
                    }
                    val move = plan.moves.first()
                    val piece = available.first { it.first == move.pieceIndex }.second
                    prepared = Prepared(frame,move,piece,frame.pieces[move.pieceIndex]!!,
                        Solver.placeAndClear(frame.board,piece,move.row,move.col),
                        SystemClock.elapsedRealtime())
                    updateMarker()
                    moveButton?.isEnabled = true
                    setStatus("PREVIEW only: piece ${move.pieceIndex+1}, row ${move.row+1}, col ${move.col+1}. " +
                        "Green = planned cells; red = touch endpoint. Adjust X/Y or press NEXT MOVE.")
                }
            } catch (e: Exception) {
                handler.post {
                    preparing = false
                    previewButton?.isEnabled = true
                    setStatus("Preview solver error: ${e.message}")
                }
            }
        }
    }

    private fun attemptOneMove() {
        if (inProgress || sessionBlocked) return
        val plan = prepared ?: run { setStatus("Tap PREVIEW first."); return }
        if (TouchService.current == null) {
            setStatus("Enable the accessibility service before NEXT MOVE."); return
        }
        if (SystemClock.elapsedRealtime()-plan.time > 30000L) {
            prepared = null; moveButton?.isEnabled = false; removeMarker()
            setStatus("Preview expired after 30 seconds. Tap PREVIEW again.")
            return
        }
        val coords = gestureCoords(plan)
        val screen = screenSize()
        if (coords[0] !in 0f..screen.x.toFloat() || coords[1] !in 0f..screen.y.toFloat() ||
            coords[2] !in 0f..screen.x.toFloat() || coords[3] !in 0f..screen.y.toFloat()) {
            setStatus("Drag would leave the screen. Adjust X/Y first."); return
        }
        inProgress = true
        moveButton?.isEnabled = false
        previewButton?.isEnabled = false
        removeMarker()
        prepared = null
        lastGestureOutcome = "Waiting for Android gesture result"
        setStatus("Testing piece ${plan.move.pieceIndex+1} at row ${plan.move.row+1}, " +
            "col ${plan.move.col+1}; endpoint (${coords[2].toInt()}, ${coords[3].toInt()}).")
        val accepted = TouchService.current?.dragWithHold(coords[0],coords[1],coords[2],coords[3]) { success,detail ->
            handler.post {
                if (stopped) return@post
                lastGestureOutcome = detail
                if (!success) {
                    sessionBlocked = true
                    finish("Android gesture failed: $detail. STOP and inspect game.")
                    return@post
                }
                // Wait for game animation and 2 stable captures after release.
                val verificationStarted = SystemClock.elapsedRealtime()
                fun verify() {
                    if (stopped) return
                    val actual = latest
                    val recent = SystemClock.elapsedRealtime() - latestAt < 2000L
                    val pieceRemoved = actual?.pieces?.get(plan.move.pieceIndex) == null
                    val lastPiece = plan.frame.recognized == 1
                    val boardMatched = actual?.board == plan.expected
                    if (recent && stableCount >= 2 && boardMatched && (pieceRemoved || lastPiece)) {
                        moveCount++
                        finish("VERIFIED: Move $moveCount placed successfully. " +
                            "Tap PREVIEW for the next piece (manual confirmation required).")
                        return
                    }
                    if (SystemClock.elapsedRealtime() - verificationStarted > 4800L) {
                        sessionBlocked = true
                        finish("UNVERIFIED: board or piece slot did not match prediction. " +
                            "STOP and inspect game. No further moves this session.")
                    } else handler.postDelayed({ verify() }, 400L)
                }
                handler.postDelayed({ verify() }, 900L)
            }
        } ?: false
        if (!accepted) {
            lastGestureOutcome = "Android rejected gesture dispatch"
            sessionBlocked = true
            finish("Accessibility service rejected gesture. No move made.")
        }
    }

    private fun finish(message: String) {
        inProgress=false
        // Every move requires a fresh preview and explicit NEXT MOVE tap.
        moveButton?.isEnabled=false
        previewButton?.isEnabled = !sessionBlocked
        setStatus(message)
    }
    override fun onDestroy() {
        stopped=true
        removeMarker()
        try { overlay?.let { wm?.removeView(it) } } catch (_: Exception) {}
        overlay=null; moveButton=null; previewButton=null; offsetLabel=null; prepared=null
        reader?.setOnImageAvailableListener(null,null)
        display?.release(); display=null
        reader?.close(); reader=null
        projection?.unregisterCallback(callback)
        projection?.stop(); projection=null
        worker.shutdownNow()
        // Preserve the last Android gesture outcome when capture is stopped.
        setStatus("Stopped. Last gesture result preserved. No automatic moves will occur.")
        super.onDestroy()
    }
}
