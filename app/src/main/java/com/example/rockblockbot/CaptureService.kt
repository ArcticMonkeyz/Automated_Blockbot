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
import android.text.TextUtils
import android.widget.ScrollView
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Guided multi-move placement: user must PREVIEW and confirm each move. */
class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var imageThread: HandlerThread? = null
    @Volatile private var captureFrozen = false
    private val worker = Executors.newSingleThreadExecutor()
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: LinearLayout? = null
    private var wm: WindowManager? = null
    private var moveButton: Button? = null
    private var probeButton: Button? = null
    private var previewButton: Button? = null
    private var offsetLabel: TextView? = null
    private var shapeLabel: TextView? = null
    private var marker: CalibrationOverlay? = null
    private var prepared: Prepared? = null
    private var preparing = false
    private var xOffset = 0
    private var yOffset = 400
    // Trial relative offset for pieces taller than they are wide. Unlike
    // v3.7, GLOBAL Y remains active and changing it always moves the endpoint.
    private var tallDelta = PlacementGeometry.DEFAULT_TALL_DELTA_PX
    private var coordsLabel: TextView? = null
    private var shapeCalibrationLabel: TextView? = null
    private var shapeCalibrationRow: LinearLayout? = null
    private var detailsPanel: ScrollView? = null
    private var calibrationControls: LinearLayout? = null
    private var calibrationToggle: Button? = null
    private var infoToggle: Button? = null
    private var calibrationPage = 0 // 0 hidden, 1 global, 2 tall, 3 per-piece
    private var lastTouchPath = "No gesture dispatched yet."
    private var lastPlacementCheck = "No confirmed placement yet."
    private var lastShadowTracking = "Not tested in this session"
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
    private data class TrackingMove(val plan: Prepared, val pickupX: Float, val pickupY: Float,
        var fingerX: Float, var fingerY: Float, var corrections: Int = 0,
        var aligned: Int = 0, var blindReads: Int = 0, var probes: Int = 0,
        val startAt: Long = SystemClock.elapsedRealtime())
    private var tracking: TrackingMove? = null
    @Volatile private var shadowTarget: ShadowTracker.Target? = null
    private var shadowSample: ShadowTracker.Match? = null
    private var shadowDiagnostic: String = "No live game-shadow scan yet"
    private var shadowSampleAt: Long = 0L
    private var shadowFrames = 0
    // v4.4.2: collect the game's live placement shadow during ONE uninterrupted
    // outbound-and-return probe. No chained accessibility strokes are used.
    private var probePlan: Prepared? = null
    private var probeStartedAt = 0L
    private val probeSamples = ArrayList<ShadowTracker.Match>()
    private var probeSampleWindow = ProbeTiming.Window(0L, 0L)
    // Purple four-strip only: two different dwell positions within one stroke.
    @Volatile private var dualProbeWindows: DualProbe.Windows? = null
    private var dualProbeAnchor: Pair<Int,Int>? = null
    private var dualProbeEndpoint: Pair<Float,Float>? = null
    private val probeSamplesA = ArrayList<ShadowTracker.Match>()
    private val probeSamplesB = ArrayList<ShadowTracker.Match>()
    @Volatile private var diagnosticCapturedB = false
    private var probeObserved = 0
    private var probeRejected = 0
    @Volatile private var diagnosticCaptured = false
    private var diagnosticFrameNote = "No diagnostic frame captured"
    private data class LearnedGrip(val x: Float, val y: Float)
    private fun learnedGrip(piece: BoardRecognizer.ObservedPiece): LearnedGrip? {
        val key = PieceCalibration.key(piece.signature, piece.tileColor)
        val prefs = getSharedPreferences("shadow_probe_v442", MODE_PRIVATE)
        if (!prefs.getBoolean("${key}_valid", false)) return null
        val screen = screenSize()
        if (prefs.getInt("${key}_w", -1) != screen.x ||
            prefs.getInt("${key}_h", -1) != screen.y) return null
        return LearnedGrip(prefs.getFloat("${key}_x", 0f), prefs.getFloat("${key}_y", 0f))
    }
    private fun calibratedCoords(plan: Prepared): FloatArray? {
        // Invalidate older single-position measurements for this specific
        // occlusion-prone shape; all other shape calibrations remain untouched.
        val shape = ShadowTracker.Target(plan.frame.board,plan.piece.normalized,
            plan.move.row,plan.move.col,plan.observed.tileColor)
        if (OccludedPurpleShadow.supported(shape)) {
            val key=PieceCalibration.key(plan.observed.signature,plan.observed.tileColor)
            if (!getSharedPreferences("shadow_probe_v442",MODE_PRIVATE)
                    .getBoolean("${key}_dual_valid",false)) return null
        }
        val map = learnedGrip(plan.observed) ?: return null
        val screen = screenSize()
        val (x, y) = GripMapping.releaseFor(plan.move.col,plan.move.row,
            GripMapping.Offset(map.x,map.y),screen.x,screen.y)
        val origin = gestureCoords(plan)
        return floatArrayOf(origin[0], origin[1], x, y)
    }
    private var pendingAbortReason: String? = null

    private var moveCount = 0
    private var verifiedThisSession = 0
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
        val text = "Status: $status\nCalibration: X ${xOffset}px, Y ${yOffset}px, Tall delta ${tallDelta}px, Tall Y ${yOffset + tallDelta}px\nLast gesture result: $lastGestureOutcome\nLast touch path: $lastTouchPath\nLast game shadow tracking: $lastShadowTracking\nLast placement check: $lastPlacementCheck\nLast game frame: $time | stable frames: $stableCount\n\n" +
            (f?.report() ?: "No game board captured yet.") + "\n\nUser-confirmed moves only. STOP removes floating controls."
        getSharedPreferences("capture",MODE_PRIVATE).edit()
            .putString("report",text).putString("placementCheck",lastPlacementCheck)
            .putString("lastTouchPath",lastTouchPath).apply()
    }
    private fun setStatus(s: String) {
        status=s
        // PREVIEW failures must be visible in the floating panel, not only in
        // the main activity (which is behind the captured game).
        if (s.startsWith("PREVIEW BLOCKED") || s.startsWith("Need a stable") ||
            s.startsWith("No complete plan") || s.startsWith("Game changed during") ||
            s.startsWith("Preview solver error")) shapeLabel?.text=s
        publish()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { requestStop(); return START_NOT_STICKY }
        if (intent?.action != "START" || projection != null) return START_NOT_STICKY
        val notification = Notification.Builder(this,"capture")
            .setContentTitle("Rock Block Bot: guided moves")
            .setContentText("Floating PREVIEW / NEXT MOVE / STOP controls active")
            .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build()
        startForeground(22,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val prefs = getSharedPreferences("calibration", MODE_PRIVATE)
        lastPlacementCheck = getSharedPreferences("capture",MODE_PRIVATE)
            .getString("placementCheck", "No confirmed placement yet.") ?: "No confirmed placement yet."
        lastTouchPath = getSharedPreferences("capture",MODE_PRIVATE)
            .getString("lastTouchPath", "No gesture dispatched yet.") ?: "No gesture dispatched yet."
        tallDelta = prefs.getInt("tallDeltaV38", PlacementGeometry.DEFAULT_TALL_DELTA_PX)

        // v2.9 successfully picked up the green four-strip, but in the observed
        // board test it landed one COLUMN left and one ROW above the preview.
        // Shift the *finger release*, not the planned board cells or pickup.
        // Migrate once, including when installing over existing saved 0/+400
        // calibration. Never stack this adjustment on subsequent app starts.
        if (!prefs.getBoolean("placementGridCorrectionV30", false)) {
            @Suppress("DEPRECATION")
            val display = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
            val size = Point().also { display.getRealSize(it) }
            xOffset = (prefs.getInt("x", 0) + PlacementGeometry.cellWidthPx(size.x))
                .coerceIn(-400,400)
            yOffset = (prefs.getInt("y", 400) + PlacementGeometry.cellHeightPx(size.y))
                .coerceIn(-500,900)
            prefs.edit().putInt("x",xOffset).putInt("y",yOffset)
                .putBoolean("placementGridCorrectionV30",true).apply()
        } else {
            xOffset = prefs.getInt("x", 0)
            yOffset = prefs.getInt("y", 400)
        }
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
            // Image processing is too expensive for Android's main/UI thread.
            // PREVIEW and STOP must remain responsive during recognition.
            imageThread = HandlerThread("RockBlockImageAnalysis").apply { start() }
            reader?.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = SystemClock.elapsedRealtime()
                    val track = shadowTarget
                    if (now-lastAnalyzed < (if (track != null) 270L else 700L) ||
                        (captureFrozen && track == null)) return@setOnImageAvailableListener
                    lastAnalyzed=now
                    val plane=image.planes[0]
                    val stride=plane.pixelStride
                    val paddedWidth=w+(plane.rowStride-stride*w)/stride
                    val bmp=Bitmap.createBitmap(paddedWidth,h,Bitmap.Config.ARGB_8888)
                    try {
                        plane.buffer.rewind(); bmp.copyPixelsFromBuffer(plane.buffer)
                        val cropped=Bitmap.createBitmap(bmp,0,0,w,h)
                        try {
                            if (track != null) {
                                val elapsed = now - probeStartedAt
                                val dual = dualProbeWindows
                                val phase = if (probePlan != null && dual != null) {
                                    when {
                                        dual.first.includes(elapsed) -> 1
                                        dual.second.includes(elapsed) -> 2
                                        else -> 0
                                    }
                                } else if (probePlan != null &&
                                    probeSampleWindow.includes(elapsed)) 1 else 0
                                val effective = if (phase==2 && dualProbeAnchor!=null)
                                    track.copy(probeRow=dualProbeAnchor!!.first,
                                        probeCol=dualProbeAnchor!!.second) else track
                                val scan = ShadowTracker.inspect(w,h,
                                    { x,y -> cropped.getPixel(x,y) },effective)
                                // Preserve proven conservative recognition. Only rescue
                                // the purple four-strip when a solid lifted block hides
                                // three cells and a distinct translucent end survives.
                                val global = if (scan.match == null &&
                                    effective.probeRow != null && effective.probeCol != null)
                                    ShadowTracker.inspect(w,h,{ x,y -> cropped.getPixel(x,y) },
                                        effective.copy(probeRow=null, probeCol=null)) else null
                                val distant = global?.match?.takeIf { m ->
                                    kotlin.math.abs(m.row-effective.probeRow!!) <= 4 &&
                                    kotlin.math.abs(m.col-effective.probeCol!!) <= 4 &&
                                    m.strength >= .85 && m.confidence >= .18 &&
                                    m.litCells == effective.cells.size
                                }?.copy(fallback=true)
                                val occluded = if (dual!=null && phase!=0 &&
                                    scan.match==null && distant==null)
                                    OccludedPurpleShadow.inspect(w,h,
                                        { x,y -> cropped.getPixel(x,y) },effective) else null
                                val accepted = scan.match ?: distant ?: occluded?.match
                                val diagnostic = if (accepted != null && occluded?.match === accepted)
                                    "OCCLUDED (PROVISIONAL): ${occluded.diagnostic}; local: ${scan.diagnostic}"
                                else if (accepted?.fallback == true)
                                    "STRICT GLOBAL FALLBACK: ${global?.diagnostic}; local: ${scan.diagnostic}"
                                else if (scan.match == null && global != null)
                                    "LOCAL REJECTED: ${scan.diagnostic}; global: ${global.diagnostic}" +
                                        (occluded?.let { "; composite: ${it.diagnostic}" } ?: "")
                                else scan.diagnostic
                                // Retain the original export and capture a second distinct
                                // position for testing the required one-cell shadow shift.
                                val firstFrame = phase==1 && !diagnosticCaptured
                                val secondFrame = phase==2 && !diagnosticCapturedB
                                if (firstFrame || secondFrame) {
                                    if(firstFrame) diagnosticCaptured=true
                                    if(secondFrame) diagnosticCapturedB=true
                                    try {
                                        val folder = File(filesDir, "calibration_diagnostics")
                                        folder.mkdirs()
                                        val suffix = if (secondFrame) "_b" else ""
                                        val png = File(folder, "latest_probe${suffix}.png")
                                        FileOutputStream(png).use { out ->
                                            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                        val note = "elapsedMs=$elapsed phase=$phase\n" +
                                            "probe=${effective.probeRow},${effective.probeCol}\n" +
                                            "target=${track.row},${track.col} color=${track.color}\n" +
                                            "local=${scan.diagnostic}\nglobal=${global?.diagnostic}\n" +
                                            "occluded=${occluded?.diagnostic}\n" +
                                            "accepted=${accepted?.row},${accepted?.col}\n"
                                        File(folder,"latest_probe${suffix}.txt").writeText(note)
                                        diagnosticFrameNote = if (secondFrame)
                                            "Saved both distinct probe frames for export" else
                                            "Saved first probe frame for export"
                                    } catch (e: Exception) {
                                        diagnosticFrameNote = "Diagnostic save failed: ${e.javaClass.simpleName}: ${e.message}"
                                    }
                                }
                                handler.post {
                                    if (stopped || shadowTarget !== track) return@post
                                    shadowSample=accepted
                                    shadowDiagnostic=diagnostic
                                    shadowSampleAt=now
                                    shadowFrames++
                                    if (probePlan != null && phase!=0) {
                                        if (accepted != null) {
                                            if (dual!=null) {
                                                if(phase==1)probeSamplesA.add(accepted)
                                                else probeSamplesB.add(accepted)
                                            } else probeSamples.add(accepted)
                                            probeObserved++
                                        } else probeRejected++
                                    }
                                }
                            } else {
                                val frame=BoardRecognizer.analyze(cropped)
                                if (frame!=null) handler.post {
                                    if (stopped || (prepared != null && !inProgress)) return@post
                                    latest=frame; latestAt=now
                                    if (frame.signature==stableSignature) stableCount++
                                    else { stableSignature=frame.signature; stableCount=1 }
                                    if (!inProgress) publish()
                                }
                            }
                        } finally { cropped.recycle() }
                    } finally { bmp.recycle() }
                } catch (e: Exception) {
                    handler.post { if (!stopped) setStatus("Capture error: ${e.message}") }
                } finally { image.close() }
            },Handler(imageThread!!.looper))
            display=projection?.createVirtualDisplay("RockBlockRead",w,h,metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
            createOverlay()
            setStatus("Capture running. Open Rock Block Saga, tap PREVIEW, then NEXT MOVE. Re-preview after every verified move.")
        } catch (e: Exception) { setStatus("Start failed: ${e.message}"); stopSelf() }
        return START_NOT_STICKY
    }
    /** One compact dock ABOVE the board. No row can grow because of button
     * text wrapping, and optional calibration/info panels have fixed heights.
     * The dock stays available during a gesture but never expands over play.
     */
    private fun createOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            setStatus("Floating controls permission missing."); return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dpScale = resources.displayMetrics.density
        fun dp(n: Int) = (n * dpScale + .5f).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(1), dp(3), dp(1))
            setBackgroundColor(0xD91B2631.toInt())
        }
        fun button(textValue: String, sizeSp: Float, weight: Float,
                   parent: LinearLayout, heightDp: Int,
                   onTap: () -> Unit): Button {
            val b = Button(this).apply {
                text = textValue
                textSize = sizeSp
                setAllCaps(false)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(2), 0, dp(2), 0)
                gravity = Gravity.CENTER
                setOnClickListener { onTap() }
            }
            parent.addView(b, LinearLayout.LayoutParams(0, dp(heightDp), weight))
            return b
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        previewButton = button("PREVIEW", 11f, 1.02f, actions, 40) { prepareMove() }
        moveButton = button("NEXT MOVE", 10f, 1.17f, actions, 40) { attemptOneMove() }.also {
            it.isEnabled = false
        }
        probeButton = button("CAL DRAG", 10f, 1.13f, actions, 40) { calibrateSingleStroke() }.also {
            it.isEnabled = false
        }
        button("STOP", 11f, .72f, actions, 40) { requestStop() }
        panel.addView(actions)

        val options = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        button("RESUME 1/2", 10f, 1f, options, 28) { resumePartialTray() }
        calibrationToggle = button("CAL", 10f, .70f, options, 28) {
            calibrationPage = (calibrationPage + 1) % 4
            updateCalibrationControls()
        }
        infoToggle = button("INFO", 10f, .70f, options, 28) {
            val opening = detailsPanel?.visibility != View.VISIBLE
            calibrationPage = 0
            updateCalibrationControls()
            detailsPanel?.visibility = if (opening) View.VISIBLE else View.GONE
            infoToggle?.text = if (opening) "HIDE INFO" else "INFO"
        }
        panel.addView(options)

        calibrationControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        shapeCalibrationRow = calibrationControls
        panel.addView(calibrationControls)

        shapeLabel = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            text = "PREVIEW a piece. CAL DRAG measures its landing shadow."
            setPadding(dp(3), dp(1), dp(3), dp(1))
        }
        panel.addView(shapeLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))

        val infoText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        offsetLabel = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            maxLines = 1
        }
        infoText.addView(offsetLabel)
        shapeCalibrationLabel = TextView(this).apply {
            setTextColor(0xFFB7F6CF.toInt())
            textSize = 10f
            maxLines = 2
            text = "Per-piece calibration available after PREVIEW."
        }
        infoText.addView(shapeCalibrationLabel)
        coordsLabel = TextView(this).apply {
            setTextColor(0xFFE1F7FF.toInt())
            textSize = 10f
            maxLines = 2
            text = "Pickup/release coordinates appear after PREVIEW."
        }
        infoText.addView(coordsLabel)
        detailsPanel = ScrollView(this).apply {
            visibility = View.GONE
            isVerticalScrollBarEnabled = true
            addView(infoText)
        }
        panel.addView(detailsPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))
        updateOffsetLabel()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        wm?.addView(panel, params)
        overlay = panel
    }

    /** Switch calibration categories WITHOUT adding rows below the board. */
    private fun updateCalibrationControls() {
        detailsPanel?.visibility = View.GONE
        infoToggle?.text = "INFO"
        val host = calibrationControls ?: return
        host.removeAllViews()
        host.visibility = if (calibrationPage == 0) View.GONE else View.VISIBLE
        calibrationToggle?.text = when (calibrationPage) {
            1 -> "X/Y"
            2 -> "TALL Y"
            3 -> "PIECE"
            else -> "CAL"
        }
        if (calibrationPage == 0) return
        val dpScale = resources.displayMetrics.density
        fun dp(n: Int) = (n * dpScale + .5f).toInt()
        fun control(textValue: String, callback: () -> Unit) {
            val b = Button(this).apply {
                text = textValue
                textSize = 10f
                setAllCaps(false)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(0,0,0,0)
                setOnClickListener { callback() }
            }
            host.addView(b, LinearLayout.LayoutParams(0, dp(28), 1f))
        }
        when (calibrationPage) {
            1 -> {
                control("X -") { adjust(-20, 0) }
                control("X +") { adjust(20, 0) }
                control("Y -") { adjust(0, -20) }
                control("Y +") { adjust(0, 20) }
            }
            2 -> {
                control("TALL Y -") { adjustTall(-20) }
                control("TALL Y +") { adjustTall(20) }
                control("HIDE") { calibrationPage = 0; updateCalibrationControls() }
            }
            3 -> {
                control("PIECE X-") { adjustPiece(-20, 0) }
                control("PIECE X+") { adjustPiece(20, 0) }
                control("PIECE Y-") { adjustPiece(0, -20) }
                control("PIECE Y+") { adjustPiece(0, 20) }
            }
        }
    }

    private fun closeFloatingDetailsForDrag() {
        calibrationPage = 0
        updateCalibrationControls()
        detailsPanel?.visibility = View.GONE
        infoToggle?.text = "INFO"
    }

    private fun updateOffsetLabel() {
        val effectiveTallY = yOffset + tallDelta
        offsetLabel?.text = "X ${xOffset}px | Y ${yOffset}px | Tall delta ${tallDelta}px (tall Y ${effectiveTallY}px)"
    }

    private fun adjust(dx: Int, dy: Int) {
        if (inProgress || sessionBlocked) return
        xOffset = (xOffset + dx).coerceIn(-400,400)
        yOffset = (yOffset + dy).coerceIn(-500,900)
        getSharedPreferences("calibration",MODE_PRIVATE).edit()
            .putInt("x",xOffset).putInt("y",yOffset).apply()
        updateOffsetLabel()
        prepared = prepared?.copy(time=SystemClock.elapsedRealtime())
        updateMarker()
        setStatus("Offsets updated. PREVIEW is visual only; NEXT MOVE performs one confirmed drag.")
    }

    /** A tall-only adjustment; never changes the global horizontal-piece Y. */
    private fun adjustTall(dy: Int) {
        if (inProgress || sessionBlocked) return
        tallDelta = (tallDelta + dy).coerceIn(-800, 400)
        getSharedPreferences("calibration",MODE_PRIVATE).edit()
            .putInt("tallDeltaV38",tallDelta).apply()
        updateOffsetLabel()
        prepared = prepared?.copy(time=SystemClock.elapsedRealtime())
        updateMarker()
        setStatus("Tall Y adjusted. Both global Y and tall delta affect tall-piece release; check red marker.")
    }

    private fun pieceAdjustment(observed: BoardRecognizer.ObservedPiece): PieceCalibration.Adjustment {
        val prefix = PieceCalibration.key(observed.signature,observed.tileColor)
        val prefs = getSharedPreferences("piece_calibration",MODE_PRIVATE)
        return PieceCalibration.Adjustment(prefs.getInt("${prefix}_x",0),prefs.getInt("${prefix}_y",0))
    }

    private fun adjustPiece(dx: Int, dy: Int) {
        val plan = prepared ?: run {
            setStatus("PREVIEW a piece before adjusting its individual calibration.")
            return
        }
        if (inProgress || sessionBlocked || preparing) return
        val id = PieceCalibration.key(plan.observed.signature, plan.observed.tileColor)
        val new = PieceCalibration.changed(pieceAdjustment(plan.observed),dx,dy)
        getSharedPreferences("piece_calibration",MODE_PRIVATE).edit()
            .putInt("${id}_x",new.xPx).putInt("${id}_y",new.yPx).apply()
        // The prepared board is unchanged; refresh the preview expiration
        // while the user is actively calibrating the release marker.
        prepared = plan.copy(time=SystemClock.elapsedRealtime())
        updateMarker()
        setStatus("Saved calibration for ${plan.observed.tileColor} ${plan.observed.signature}: " +
            "additional X=${new.xPx}px Y=${new.yPx}px. Check the red marker before NEXT MOVE.")
    }

    private fun screenSize(): Point = Point().also { point ->
        @Suppress("DEPRECATION")
        wm!!.defaultDisplay.getRealSize(point)
    }

    private fun effectiveOffsets(plan: Prepared, screen: Point): Pair<Int,Int> {
        val (biasCol,biasRow) = PlacementGeometry.shapeCorrectionCells(
            plan.observed.signature,plan.observed.tileColor)
        val personal = pieceAdjustment(plan.observed)
        val baseY = PlacementGeometry.effectiveYOffsetPx(
            plan.piece.height,plan.piece.width,yOffset,tallDelta,0,
            PlacementGeometry.cellHeightPx(screen.y))
        return PieceCalibration.effectiveX(xOffset,biasCol,
                   PlacementGeometry.cellWidthPx(screen.x),personal) to
               PieceCalibration.effectiveY(baseY,biasRow,
                   PlacementGeometry.cellHeightPx(screen.y),personal)
    }

    private fun gestureCoords(plan: Prepared): FloatArray {
        val screen = screenSize()
        val sx = screen.x / BoardRecognizer.REF_WIDTH
        val sy = screen.y / BoardRecognizer.REF_HEIGHT
        val (effectiveX,effectiveY) = effectiveOffsets(plan,screen)
        val fromX = plan.observed.gripX * sx
        val fromY = plan.observed.gripY * sy
        val rawToX = (163f + (plan.move.col + plan.observed.gripCol)*55f) * sx + effectiveX
        val rawToY = (454f + (plan.move.row + plan.observed.gripRow)*55f) * sy + effectiveY
        // The old guessed endpoint can be below the board. For shadow-guided
        // control it is only an approach point, NOT a place to release.
        val approach = DragApproach.boardEntry(rawToX, rawToY, screen.x, screen.y)
        return floatArrayOf(fromX, fromY, approach.x, approach.y)
    }

    private fun updateMarker() {
        val plan = prepared ?: return
        shapeLabel?.text = "Piece ${plan.move.pieceIndex+1}: ${plan.observed.tileColor} " +
            "${plan.observed.signature}, ${plan.observed.count} tiles. " +
            "Check cyan markers and green target before CAL DRAG."
        val screen = screenSize()
        val learnedCoords = calibratedCoords(plan)
        val coords = learnedCoords ?: gestureCoords(plan)
        val (actualX,actualY) = effectiveOffsets(plan,screen)
        val personal = pieceAdjustment(plan.observed)
        shapeCalibrationLabel?.text = "Current ${plan.observed.tileColor} ${plan.observed.signature}: " +
            "extra X=${personal.xPx}, Y=${personal.yPx}px (saved per shape+color)"
        coordsLabel?.text = "Finger: (${coords[0].toInt()}, ${coords[1].toInt()})" +
            " -> ${if (learnedCoords == null) "PROBE ENTRY" else "CALIBRATED RELEASE"} " +
            "(${coords[2].toInt()}, ${coords[3].toInt()}) px; " +
            "single-stroke mode (no chained hold); legacy X=$actualX Y=$actualY"
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
            screen.x / BoardRecognizer.REF_WIDTH, screen.y / BoardRecognizer.REF_HEIGHT,
            plan.observed.tileCenters
        )
    }

    private fun removeMarker() {
        try { marker?.let { wm?.removeView(it) } } catch (_: Exception) {}
        marker = null
        coordsLabel?.text = "Pickup/release coordinates appear after PREVIEW."
    }


    /** Resuming an old game/session with only 1-2 physical tray pieces left is
     * a deliberate user action; never assume missing detections mean a used slot.
     */
    private fun resumePartialTray() {
        if (preparing || inProgress || sessionBlocked || prepared != null) {
            setStatus("Cannot resume while a move is prepared or running. STOP if unsure.")
            return
        }
        val frame = latest
        if (frame == null || frame.recognized !in 1..2 || stableCount < 3 ||
            SystemClock.elapsedRealtime()-latestAt > 4000L) {
            setStatus("Resume requires a recent stable board with exactly 1 or 2 visible pieces.")
            return
        }
        if (frame.missingVisiblePiece) {
            setStatus("RESUME BLOCKED: Colored blocks are still visible in an unrecognized tray slot. " +
                "All visible pieces must be recognized. STOP and send the report.")
            return
        }
        moveCount = 3 - frame.recognized
        setStatus("PARTIAL TRAY: ${frame.recognized} detected; resume enabled for this session. " +
            "Confirm that ONLY ${frame.recognized} pieces really remain in the tray. " +
            "If another visible piece is missing from detection, STOP. Otherwise tap PREVIEW.")
    }
    private fun prepareMove() {
        if (preparing || inProgress || sessionBlocked) return
        prepared = null
        captureFrozen = false
        removeMarker()
        moveButton?.isEnabled = false
        probeButton?.isEnabled = false
        val frame = latest
        val expectedPieces = 3 - (moveCount % 3)
        if (frame?.missingVisiblePiece == true) {
            setStatus("PREVIEW BLOCKED: At least one colored tray piece was not recognized. " +
                "Do NOT use RESUME to skip it. STOP and send the report.")
            return
        }
        // The historic geometric fallback once interpreted a green 2x2 as a
        // six-block rectangle. Never plan around an unverified fallback.
        val doubtful = frame?.pieces?.indexOfFirst { p ->
            p != null && p.tileCenters.size != p.count
        } ?: -1
        if (doubtful >= 0) {
            setStatus("PREVIEW BLOCKED: Piece ${doubtful + 1} lacks confirmed individual tile " +
                "centers. Wait for a new frame; never move a guessed block count.")
            return
        }
        if (frame == null || frame.recognized != expectedPieces || stableCount < 3 ||
            SystemClock.elapsedRealtime() - latestAt > 4000L || frame.width >= frame.height) {
            setStatus("Need $expectedPieces/$expectedPieces detected tray pieces and 3 stable frames " +
                "(currently ${frame?.recognized ?: 0}/3, $stableCount stable). " +
                "If a visible tile is missing, STOP and send the report; do not use RESUME.")
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
                    val observed = frame.pieces[move.pieceIndex]!!
                    prepared = Prepared(frame,move,piece,observed,
                        Solver.placeAndClear(frame.board,piece,move.row,move.col),
                        SystemClock.elapsedRealtime())
                    captureFrozen = true
                    updateMarker()
                    probeButton?.isEnabled = true
                    moveButton?.isEnabled = calibratedCoords(prepared!!) != null
                    setStatus("PREVIEW only: piece ${move.pieceIndex+1}, row ${move.row+1}, col ${move.col+1}. " +
                        "${piece.normalized.size} green cells = full detected shape; blue = pickup tile; red = release. " +
                        "If count/shape differs from the real piece, STOP. Calibration X=$xOffset Y=$yOffset. " +
                        "Preset shape adjustment ${PlacementGeometry.shapeCorrectionCells(observed.signature,observed.tileColor)} cells; " +
                        "personal adjustment ${pieceAdjustment(observed)}. " +
                        (if (calibratedCoords(prepared!!) == null)
                            "CAL DRAG required first: returns piece toward tray and measures the shadow. "
                        else "Calibrated single-stroke NEXT MOVE available. " ) +
                        (if (PlacementGeometry.directTallAnchor(piece.height,piece.width))
                            "Tall-adjusted release (trial): effective Y=${yOffset+tallDelta}px before shape bias. " else ""))
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

    /** Probe first, without attempting a board placement. The finger travels
     * from the tray to the board and BACK in one uninterrupted Android stroke.
     * The image thread collects the game's translucent landing shadow along
     * that path. No calibration is saved unless multiple samples agree AND the
     * board/tray return to their pre-probe state.
     */
    /** Select a legal, preferably interior probe footprint. The actual move target
     * stays unchanged; only the temporary out-and-back calibration destination moves.
     * Keep at least one cell of breathing room around the raised piece when possible.
     */
    private fun safeProbeAnchor(plan: Prepared): Pair<Int, Int>? {
        val cells = plan.piece.normalized
        if (cells.isEmpty()) return null
        val occupied = plan.frame.board
        val options = ArrayList<Triple<Int, Int, Int>>()
        for (r in 0..7) for (c in 0..7) {
            var legal = true
            for (cell in cells) {
                val rr = r + cell.r; val cc = c + cell.c
                if (rr !in 0..7 || cc !in 0..7 ||
                    (occupied and (1L shl (rr * 8 + cc))) != 0L) { legal = false; break }
            }
            if (!legal) continue
            val maxR = r + cells.maxOf { it.r }
            val maxC = c + cells.maxOf { it.c }
            val edge = minOf(r, c, 7-maxR, 7-maxC)
            var neighbours = 0
            for (cell in cells) for (dr in -1..1) for (dc in -1..1) {
                val rr = r+cell.r+dr; val cc = c+cell.c+dc
                if (rr in 0..7 && cc in 0..7 &&
                    (occupied and (1L shl (rr*8+cc))) != 0L) neighbours++
            }
            val distance = kotlin.math.abs(r-plan.move.row)+kotlin.math.abs(c-plan.move.col)
            options.add(Triple(r,c,edge*100-neighbours*12-distance*4))
        }
        return options.maxByOrNull { it.third }?.let { it.first to it.second }
    }

    /** Second safe anchor, a single cell from the first. Prefer horizontal
     * movement for the four-wide strip; avoid occupied or clipped destinations. */
    private fun alternateProbeAnchor(plan:Prepared, first:Pair<Int,Int>):Pair<Int,Int>? {
        val options=listOf(0 to 1,0 to -1,1 to 0,-1 to 0)
        val occupied=plan.frame.board
        for ((dr,dc) in options) {
            val nr=first.first+dr;val nc=first.second+dc
            if(plan.piece.normalized.all { cell ->
                val r=nr+cell.r;val c=nc+cell.c
                r in 0..7 && c in 0..7 &&
                    (occupied and (1L shl (r*8+c)))==0L
            }) return nr to nc
        }
        return null
    }

    private fun calibrateSingleStroke() {
        if (inProgress || sessionBlocked) return
        val plan=prepared ?: run { setStatus("Press PREVIEW before CAL DRAG."); return }
        val touch=TouchService.current ?: run {setStatus("Accessibility service missing.");return}
        if (plan.observed.tileCenters.size != plan.observed.count) {
            setStatus("Calibration BLOCKED: individual tile centers are uncertain.");return
        }
        if (SystemClock.elapsedRealtime() - plan.time > 30000L) {
            setStatus("PREVIEW expired; press PREVIEW again."); return
        }
        val coords=gestureCoords(plan)
        val screen=screenSize()
        val probeAnchor = safeProbeAnchor(plan) ?: run {
            setStatus("CAL DRAG blocked: no legal probe footprint."); return
        }
        // Shift the estimated finger endpoint by the same grid displacement.
        // GripMapping learns from the actual probe endpoint and measured shadow,
        // so this does not alter the final green-preview destination.
        coords[2] += (probeAnchor.second-plan.move.col)*ShadowTracker.pitchX(screen.x)
        coords[3] += (probeAnchor.first-plan.move.row)*ShadowTracker.pitchY(screen.y)
        val baseTarget=ShadowTracker.Target(plan.frame.board,plan.piece.normalized,
            plan.move.row,plan.move.col,plan.observed.tileColor,
            probeAnchor.first,probeAnchor.second)
        val possibleSecond=if (OccludedPurpleShadow.supported(baseTarget))
            alternateProbeAnchor(plan,probeAnchor) else null
        if (OccludedPurpleShadow.supported(baseTarget) && possibleSecond==null) {
            setStatus("CAL DRAG blocked: this purple four-strip needs two safe probe positions. " +
                "Move manually or wait for a safer board; do not guess calibration.")
            return
        }
        val secondCoords=possibleSecond?.let {
            Pair(coords[2]+(it.second-probeAnchor.second)*ShadowTracker.pitchX(screen.x),
                coords[3]+(it.first-probeAnchor.first)*ShadowTracker.pitchY(screen.y))
        }
        if(coords.any { it !in 12f..(screen.y-12f) } || coords[0]>=screen.x-12 ||
            coords[2]>=screen.x-12 ||
            (secondCoords!=null && (secondCoords.first !in 12f..(screen.x-12f) ||
                secondCoords.second !in 12f..(screen.y-12f)))) {
            setStatus("Calibration BLOCKED: invalid finger coordinates."); return
        }
        probePlan=plan
        probeSamples.clear()
        probeSamplesA.clear();probeSamplesB.clear()
        dualProbeAnchor=possibleSecond
        dualProbeEndpoint=secondCoords
        dualProbeWindows=if (possibleSecond!=null && secondCoords!=null)
            DualProbe.windows(coords[0],coords[1],coords[2],coords[3],
                secondCoords.first,secondCoords.second) else null
        // Never reuse a second-phase screenshot from an older calibration.
        for (name in listOf("latest_probe.png","latest_probe.txt",
            "latest_probe_b.png","latest_probe_b.txt"))
            File(filesDir,"calibration_diagnostics/$name").delete()
        probeObserved=0
        probeRejected=0
        diagnosticCaptured=false;diagnosticCapturedB=false
        diagnosticFrameNote="Waiting for probe dwell frame"
        probeStartedAt=SystemClock.elapsedRealtime()
        probeSampleWindow=dualProbeWindows?.first ?: ProbeTiming.observationWindow(
            coords[0],coords[1],coords[2],coords[3])
        inProgress=true
        closeFloatingDetailsForDrag()
        captureFrozen=false
        prepared=null
        removeMarker()
        moveButton?.isEnabled=false
        probeButton?.isEnabled=false
        previewButton?.isEnabled=false
        shadowSample=null;shadowFrames=0;shadowSampleAt=0L
        shadowTarget=baseTarget
        lastTouchPath="CAL DRAG for ${plan.observed.tileColor} ${plan.observed.signature}: " +
            "planned preview top-left r${plan.move.row+1} c${plan.move.col+1}; " +
            "safe probe r${probeAnchor.first+1} c${probeAnchor.second+1}; " +
            "pickup (${coords[0].toInt()},${coords[1].toInt()}) -> " +
            "interior board approach (${coords[2].toInt()},${coords[3].toInt()}) " +
            (if(secondCoords!=null) "-> second probe (${secondCoords.first.toInt()},${secondCoords.second.toInt()}) " else "") +
            "-> back to tray (ONE stroke)."
        lastShadowTracking="Calibrating: observing " +
            "${probeSampleWindow.fromMs}-${probeSampleWindow.toMs} ms" +
            (dualProbeWindows?.let { " AND ${it.second.fromMs}-${it.second.toMs} ms (independent second position)" }
                ?: "") + "; never sampling while travelling."
        lastPlacementCheck="PENDING: Return-to-tray calibration probe; no placement requested."
        shapeLabel?.text="CAL DRAG: checking preview-derived approach, then returning to tray. Do not touch."
        setStatus("Calibration trial: one uninterrupted drag out and back, then board check.")
        val callback: (Boolean,String)->Unit = {ok,msg ->
            handler.post { if(!stopped && probePlan===plan) finishProbe(plan,coords,ok,msg) }
        }
        val accepted=if (secondCoords!=null)
            touch.probeWithTwoStops(coords[0],coords[1],coords[2],coords[3],
                secondCoords.first,secondCoords.second,callback)
            else touch.probeWithReturn(coords[0],coords[1],coords[2],coords[3],callback)
        if(!accepted)finishProbe(plan,coords,false,"Android rejected single-stroke probe")
    }

    private fun finishProbe(plan:Prepared, coords:FloatArray, gestureOk:Boolean, gestureResult:String) {
        if(probePlan!==plan || stopped)return
        val shadowTargetProbeRow=shadowTarget?.probeRow
        val shadowTargetProbeCol=shadowTarget?.probeCol
        probePlan=null
        shadowTarget=null
        lastGestureOutcome=gestureResult
        // Do not infer accuracy from just one image. Two matching independently
        // captured frames are required, and neither can be a moving-piece mask.
        val windows=dualProbeWindows
        val dual=windows!=null && dualProbeAnchor!=null
        val secondary=dualProbeAnchor
        val verdict=if (dual && secondary!=null)
            DualProbe.validate(probeSamplesA,probeSamplesB,
                secondary.first - (shadowTargetProbeRow ?: secondary.first),
                secondary.second - (shadowTargetProbeCol ?: secondary.second)) else null
        val readings=probeSamples.groupBy { it.row to it.col }
            .toList().sortedByDescending { (_, values) -> values.size }
        val best=readings.firstOrNull()
        val second=readings.getOrNull(1)?.second?.size ?: 0
        val observed=if (dual)probeSamplesA.size+probeSamplesB.size
                     else best?.second?.size ?: 0
        val sample=if(dual)verdict?.first else
            best?.second?.maxByOrNull {it.strength + it.confidence}
        val confident=gestureOk && (if(dual) verdict?.accepted==true else
            observed >= (if(sample?.fallback==true) 3 else 2) &&
            observed > second && sample != null &&
            best?.second?.all { it.fallback == sample.fallback } == true)
        lastShadowTracking=if(sample!=null)
            "Probe shadow ${sample.colorlessPosition()}, target row ${plan.move.row+1} " +
            "col ${plan.move.col+1} (delta ${sample.col-plan.move.col} columns, " +
            "${sample.row-plan.move.row} rows); " +
            (if(dual) "dual-position: ${verdict?.reason}; " else "$observed agreeing frames; ") +
            "$probeObserved candidate and $probeRejected rejected frames; " +
            "fallback=${sample.fallback}. $diagnosticFrameNote. " +
            "Fingerprint ${plan.observed.tileColor} ${plan.observed.signature}."
        else "No agreeing shadow: $probeObserved accepted-frame candidate(s), " +
            "$probeRejected rejected; " +
            (if(dual) "two-position result ${verdict?.reason}; " else "") +
            "$shadowDiagnostic; $diagnosticFrameNote."
        probeSamples.clear();probeSamplesA.clear();probeSamplesB.clear()
        dualProbeWindows=null; dualProbeAnchor=null;dualProbeEndpoint=null
        // Resume ordinary recognition after the finger has returned toward tray.
        val waitingSince=SystemClock.elapsedRealtime()
        fun settle() {
            if (stopped)return
            val actual=latest
            val fresh=latestAt>waitingSince+300L &&
                SystemClock.elapsedRealtime()-latestAt<2100L
            val returned=fresh && actual?.board==plan.frame.board &&
                actual?.pieces?.getOrNull(plan.move.pieceIndex)?.signature==plan.observed.signature
            if (returned && stableCount>=2) {
                if(confident && sample!=null) {
                    val screen=screenSize()
                    val mapping=GripMapping.fromProbe(coords[2],coords[3],
                        sample.col,sample.row,screen.x,screen.y)
                    val offsetX=mapping.x
                    val offsetY=mapping.y
                    val key=PieceCalibration.key(plan.observed.signature,plan.observed.tileColor)
                    getSharedPreferences("shadow_probe_v442",MODE_PRIVATE).edit()
                        .putFloat("${key}_x",offsetX).putFloat("${key}_y",offsetY)
                        .putInt("${key}_w",screen.x).putInt("${key}_h",screen.y)
                        .putBoolean("${key}_valid",true)
                        .putBoolean("${key}_dual_valid",dual).apply()
                    lastPlacementCheck="CALIBRATED: The piece returned to the tray; " +
                        (if(dual) "two distinct probe positions agreed; " else "") +
                        "game shadow matched $observed frames at row ${sample.row+1}, " +
                        "col ${sample.col+1}. Learned finger-to-shadow offset " +
                        "X=${offsetX.toInt()} Y=${offsetY.toInt()} px. " +
                        "Press PREVIEW again to inspect the calibrated NEXT MOVE. " +
                        "Single-stroke placement is a trial, NOT live closed-loop control."
                    sessionBlocked=false
                } else {
                    lastPlacementCheck="NO CALIBRATION: Returned to tray but game shadow was " +
                        "uncertain ($observed agreeing captures). $gestureResult"
                    sessionBlocked=false
                }
            } else if(SystemClock.elapsedRealtime()-waitingSince>6500L) {
                sessionBlocked=true
                lastPlacementCheck="UNVERIFIED PROBE: Piece did not clearly return to tray " +
                    "or board changed. STOP and inspect game. $gestureResult"
            } else {
                handler.postDelayed({settle()},350L)
                return
            }
            inProgress=false
            shapeLabel?.text=lastPlacementCheck
            coordsLabel?.text="See last shadow tracking report."
            previewButton?.isEnabled=!sessionBlocked
            moveButton?.isEnabled=false;probeButton?.isEnabled=false
            setStatus(lastPlacementCheck)
        }
        handler.postDelayed({settle()},900L)
    }

    private fun ShadowTracker.Match.colorlessPosition():String =
        "row ${row+1}, col ${col+1}"

    /** Uses the measured finger-to-shadow relation and a SINGLE Android
     * gesture. No more cancelled continuation segment 2. This is a calibrated
     * open-loop release, explicitly NOT a live shadow-verified release.
     */
    private fun attemptOneMove() {
        if (inProgress || sessionBlocked) return
        val plan=prepared ?: run {setStatus("Tap PREVIEW first.");return}
        val touch=TouchService.current ?: run {setStatus("Accessibility service missing.");return}
        if (plan.observed.tileCenters.size!=plan.observed.count) {
            setStatus("MOVE BLOCKED: individual tile centers uncertain.");return
        }
        val coords=calibratedCoords(plan) ?: run {
            setStatus("MOVE BLOCKED: Run CAL DRAG for this piece first, then PREVIEW again.");return
        }
        val screen=screenSize()
        if (SystemClock.elapsedRealtime()-plan.time>30000L || coords[0] !in 12f..(screen.x-12f) ||
            coords[1] !in 12f..(screen.y-12f) || coords[2] !in 12f..(screen.x-12f) ||
            coords[3] !in 12f..(screen.y-12f)) {
            setStatus("MOVE BLOCKED: stale preview or calibrated release outside screen.");return
        }
        inProgress=true
        closeFloatingDetailsForDrag()
        captureFrozen=false
        prepared=null
        removeMarker()
        moveButton?.isEnabled=false;probeButton?.isEnabled=false
        previewButton?.isEnabled=false
        lastTouchPath="CALIBRATED ONE STROKE ${plan.observed.tileColor} ${plan.observed.signature}: " +
            "(${coords[0].toInt()},${coords[1].toInt()}) -> " +
            "(${coords[2].toInt()},${coords[3].toInt()}); no segment 2."
        lastPlacementCheck="PENDING: one uninterrupted calibrated gesture."
        lastGestureOutcome="Calibrated single stroke in progress"
        shapeLabel?.text="Calibrated single-stroke placement. Wait for board verification."
        setStatus("Single-stroke trial: moving and releasing once; no Android continuation.")
        val accepted=touch.calibratedSingleDrag(coords[0],coords[1],coords[2],coords[3]) {ok,msg->
            handler.post {
                if(stopped)return@post
                lastGestureOutcome=msg
                if (ok) verifyReleasedPiece(plan)
                else {
                    sessionBlocked=true
                    finish("UNVERIFIED: $msg; cancellation can accidentally place a piece. Inspect board.")
                }
            }
        }
        if(!accepted) {
            sessionBlocked=true
            finish("UNVERIFIED: Android refused single-stroke placement. Inspect board.")
        }
    }

    private fun holdForVisualCheck(t: TrackingMove) {
        if(tracking!==t || stopped)return
        pendingAbortReason?.let { abortTracking(it); return }
        if(SystemClock.elapsedRealtime()-t.startAt>18000L) {
            abortTracking("18-second shadow tracking timeout")
            return
        }
        // TouchService dispatches hold continuations immediately from Android's
        // gesture callbacks. Do NOT restart a hold from this delayed UI callback:
        // that exact gap caused cancelled gestures on the user's device.
        val observationStarted=SystemClock.elapsedRealtime()
        handler.postDelayed({
            if(tracking===t && !stopped) evaluateShadow(t,observationStarted)
        },850L)
    }

    private fun evaluateShadow(t: TrackingMove, holdStart: Long) {
        if(tracking!==t || stopped)return
        pendingAbortReason?.let { abortTracking(it); return }
        val sample=shadowSample
        val fresh=shadowSampleAt>=holdStart+250L &&
            SystemClock.elapsedRealtime()-shadowSampleAt<1050L
        if(!fresh || sample==null) {
            t.blindReads++
            lastShadowTracking="No unambiguous ${t.plan.observed.tileColor} game shadow " +
                "on scan $shadowFrames: $shadowDiagnostic. Search attempt ${t.probes}; target " +
                "row ${t.plan.move.row+1}, col ${t.plan.move.col+1}."
            t.aligned=0
            shapeLabel?.text="No reliable ${t.plan.observed.tileColor} game shadow. " +
                "Seeking valid snap preview without releasing..."
            // A finger just outside the board may produce NO game ghost at all.
            // After an extra stationary check, probe one cell TOWARD the board
            // centre. Never release because of a probe; still require 2 matches.
            if(t.blindReads==1)holdForVisualCheck(t)
            else if(t.probes<2 && t.corrections<5) {
                t.probes++;t.blindReads=0
                val screen=screenSize()
                val cx=(ShadowTracker.boardX(3,screen.x)+ShadowTracker.boardX(4,screen.x))/2f
                val cy=(454f+3.5f*55f)*screen.y/BoardRecognizer.REF_HEIGHT
                val signX=if(t.fingerX>cx) -1 else 1
                val signY=if(t.fingerY>cy) -1 else 1
                val nextX=t.fingerX+signX*ShadowTracker.pitchX(screen.x)
                val nextY=t.fingerY+signY*ShadowTracker.pitchY(screen.y)
                if(nextX !in 15f..(screen.x-15f) || nextY !in 15f..(screen.y-15f)) {
                    abortTracking("Cannot safely probe for a game shadow")
                    return
                }
                t.corrections++
                setStatus("Game ghost absent: probing one grid cell toward board center (${t.probes}/2).")
                val ok=TouchService.current?.continueHeldDrag(nextX,nextY,750L){success,msg->
                    handler.post {
                        if(tracking!==t || stopped)return@post
                        if(!success)dragFailure(t,msg)
                        else { t.fingerX=nextX;t.fingerY=nextY;holdForVisualCheck(t) }
                    }
                } ?: false
                if(!ok)dragFailure(t,"Cannot continue search gesture")
            } else abortTracking("Game's placement shadow not reliably detected")
            return
        }
        t.blindReads=0
        lastShadowTracking="Detected ${t.plan.observed.tileColor} translucent game ghost at " +
            "row ${sample.row+1}, col ${sample.col+1}; " +
            "target row ${t.plan.move.row+1}, col ${t.plan.move.col+1}; " +
            "strength ${"%.2f".format(Locale.US,sample.strength)}, " +
            "runner-up margin ${"%.2f".format(Locale.US,sample.confidence)}. " +
            "Adjustments so far ${t.corrections}."
        val dx=t.plan.move.col-sample.col
        val dy=t.plan.move.row-sample.row
        if(dx==0 && dy==0) {
            t.aligned++
            shapeLabel?.text="Game ghost aligned ${t.aligned}/2 frames " +
                "(score ${"%.2f".format(Locale.US,sample.strength)}). Holding."
            setStatus("Game shadow aligned with target (${t.aligned}/2 confirmations).")
            if(t.aligned>=2) releaseAlignedPiece(t) else holdForVisualCheck(t)
            return
        }
        t.aligned=0
        if(t.corrections>=5) {
            abortTracking("Five corrections exhausted; shadow still offset ($dx,$dy)")
            return
        }
        val screen=screenSize()
        val stepX=dx.coerceIn(-2,2)*ShadowTracker.pitchX(screen.x)
        val stepY=dy.coerceIn(-2,2)*ShadowTracker.pitchY(screen.y)
        val nextX=t.fingerX+stepX
        val nextY=t.fingerY+stepY
        if(nextX !in 15f..(screen.x-15f) || nextY !in 15f..(screen.y-15f)) {
            abortTracking("Correction would move finger off screen")
            return
        }
        t.corrections++
        shapeLabel?.text="Game shadow ${-dx} cols, ${-dy} rows from target. " +
            "Correction ${t.corrections}/5, then recheck."
        setStatus("Correcting observed ghost offset: cols $dx, rows $dy, attempt ${t.corrections}.")
        val ok=TouchService.current?.continueHeldDrag(nextX,nextY,750L){success,msg->
            handler.post {
                if(tracking!==t || stopped)return@post
                if(!success)dragFailure(t,msg)
                else {
                    t.fingerX=nextX;t.fingerY=nextY
                    holdForVisualCheck(t)
                }
            }
        } ?: false
        if(!ok)dragFailure(t,"Could not continue pointer toward target")
    }

    private fun releaseAlignedPiece(t: TrackingMove) {
        if(tracking!==t || stopped)return
        pendingAbortReason?.let { abortTracking(it); return }
        shadowTarget=null
        lastTouchPath += " -> visually aligned release (${t.fingerX.toInt()},${t.fingerY.toInt()}); " +
            "${t.corrections} corrections; 2 ghost confirmations"
        val ok=TouchService.current?.releaseHeldDrag{success,msg->
            handler.post {
                if(tracking!==t || stopped)return@post
                tracking=null
                lastGestureOutcome="${if(success)"Released" else "Release error"}: $msg"
                if(success) verifyReleasedPiece(t.plan)
                else { sessionBlocked=true;finish("UNVERIFIED: $msg; inspect game before continuing.") }
            }
        } ?: false
        if(!ok) dragFailure(t,"Accessibility refused final release")
    }

    private fun abortTracking(reason: String) {
        val t=tracking ?: return
        val touch=TouchService.current
        // TouchService maintains the finger and queues the tray-return motion
        // for the next segment boundary; no main-thread timing gap is needed.
        pendingAbortReason=null
        shadowTarget=null
        lastPlacementCheck="UNVERIFIED: Visual alignment not confirmed: $reason. " +
            "Attempting to return held piece to tray."
        shapeLabel?.text="Returning piece to tray: $reason"
        setStatus(lastPlacementCheck)
        if(touch == null || !touch.isHolding) {
            tracking=null;sessionBlocked=true
            finish("UNVERIFIED: $reason. Finger may already have released; inspect board.")
            return
        }
        val ok=touch.returnToTray { success,msg ->
            handler.post {
                if(tracking!==t || stopped)return@post
                tracking=null;sessionBlocked=true
                lastGestureOutcome=msg
                finish("UNVERIFIED: $reason. Return-to-tray ${if(success)"attempt completed" else "FAILED"}; " +
                    "inspect the board and tray. Do not assume the piece was recovered.")
            }
        }
        if(!ok) {
            tracking=null;sessionBlocked=true
            finish("UNVERIFIED: $reason. Could not start tray return; inspect game.")
        }
    }

    private fun dragFailure(t:TrackingMove, reason:String) {
        if(tracking!==t)return
        // Cancellation may have released the pointer already. Never claim the
        // piece returned to the tray without visual confirmation.
        abortTracking("Android held gesture error: $reason")
    }

    private fun requestStop() {
        if (probePlan != null || (inProgress && tracking == null)) {
            setStatus("One-stroke calibration/placement is active. Wait for its result before STOP.")
            return
        }
        if(tracking != null) {
            abortTracking("STOP pressed during drag")
            return // The user can press STOP again after return concludes.
        }
        stopSelf()
    }

    private fun verifyReleasedPiece(plan: Prepared) {
        // Only the game's ghost alignment authorizes release. Then verify the
        // post-release board as before, including any row or column clears.
        val verificationStarted=SystemClock.elapsedRealtime()
        shapeLabel?.text="Gesture ended. Verifying settled board..."
        fun verify() {
            if(stopped)return
            val actual=latest
            val recent=latestAt>=verificationStarted+350L &&
                SystemClock.elapsedRealtime()-latestAt<2000L
            val pieceRemoved=actual?.pieces?.get(plan.move.pieceIndex)==null
            val lastPiece=plan.frame.recognized==1
            if(recent && stableCount>=2 && actual?.board==plan.expected &&
                (pieceRemoved || lastPiece)) {
                moveCount++;verifiedThisSession++
                finish("VERIFIED: Calibrated single-stroke move $verifiedThisSession placed successfully. " +
                    "Tap PREVIEW for the next piece.")
                return
            }
            if(SystemClock.elapsedRealtime()-verificationStarted>6500L) {
                sessionBlocked=true
                val diagnosis=if(recent && actual!=null) displacementReport(plan,actual.board)
                    else "No settled post-release game frame captured."
                finish("UNVERIFIED: $diagnosis STOP and inspect game.")
            } else handler.postDelayed({verify()},400L)
        }
        handler.postDelayed({verify()},900L)
    }

    /** Diagnose placements by simulating legal final boards, including clears.
     *  The saved per-piece calibration is never silently changed by a guess.
     */
    private fun displacementReport(plan: Prepared, actual: Long): String {
        val matches = PlacementDiagnostics.findMatches(plan.frame.board,plan.piece,
            plan.move.row,plan.move.col,actual)
        if (matches.size == 1) {
            val shift = matches.single()
            if (shift.dx==0 && shift.dy==0)
                return "Board matched preview, but the tray or timing verification disagreed."
            val screen = screenSize()
            val (x,y) = effectiveOffsets(plan,screen)
            val personal = pieceAdjustment(plan.observed)
            val changeX = -shift.dx*PlacementGeometry.cellWidthPx(screen.x)
            val changeY = -shift.dy*PlacementGeometry.cellHeightPx(screen.y)
            return "Shape ${plan.observed.tileColor} ${plan.observed.signature} landed " +
                "${shift.dx} columns, ${shift.dy} rows from preview" +
                (if (shift.clearedLines>0) "; cleared ${shift.clearedLines} line(s)" else "") +
                ". Effective release X=$x Y=$y. For THIS shape only, " +
                "suggest an EXTRA calibration change X=$changeX Y=$changeY px " +
                "(current extra X=${personal.xPx}, Y=${personal.yPx}; NOT auto-applied)."
        }
        return PlacementDiagnostics.diagnose(plan.frame.board,plan.piece,
            plan.move.row,plan.move.col,actual)
    }

    private fun finish(message: String) {
        lastPlacementCheck = message
        inProgress=false
        // Surface the verification result in the floating overlay itself.
        shapeLabel?.text = message
        coordsLabel?.text = "See Last placement check after STOP."
        shapeCalibrationLabel?.text = "Per-piece calibration is saved. Tap PREVIEW before moving again."
        // Every move requires a fresh preview and explicit NEXT MOVE tap.
        moveButton?.isEnabled=false
        probeButton?.isEnabled=false
        previewButton?.isEnabled = !sessionBlocked
        setStatus(message)
    }
    override fun onDestroy() {
        // If the user stops before the 6-second verification period completes,
        // preserve that fact instead of saying no move was attempted.
        if (inProgress) lastPlacementCheck =
            "INCOMPLETE: Capture stopped before calibration/placement verification finished. " +
            "Do not assume the drag succeeded."
        if (tracking != null) TouchService.current?.let { touch ->
            if (touch.isHolding) touch.returnToTray { _, _ -> }
        }
        shadowTarget=null
        tracking=null
        probePlan=null
        stopped=true
        captureFrozen=false
        removeMarker()
        try { overlay?.let { wm?.removeView(it) } } catch (_: Exception) {}
        overlay=null; moveButton=null; probeButton=null; previewButton=null; offsetLabel=null; shapeLabel=null
        coordsLabel=null; shapeCalibrationLabel=null; shapeCalibrationRow=null
        detailsPanel=null; calibrationControls=null; calibrationToggle=null; infoToggle=null
        prepared=null
        reader?.setOnImageAvailableListener(null,null)
        imageThread?.quitSafely(); imageThread=null
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
