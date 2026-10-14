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
    private var lastTouchPath = "No gesture dispatched yet."
    private var lastPlacementCheck = "No confirmed placement yet."
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
        val text = "Status: $status\nCalibration: X ${xOffset}px, Y ${yOffset}px, Tall delta ${tallDelta}px, Tall Y ${yOffset + tallDelta}px\nLast gesture result: $lastGestureOutcome\nLast touch path: $lastTouchPath\nLast placement check: $lastPlacementCheck\nLast game frame: $time | stable frames: $stableCount\n\n" +
            (f?.report() ?: "No game board captured yet.") + "\n\nUser-confirmed moves only. STOP removes floating controls."
        getSharedPreferences("capture",MODE_PRIVATE).edit()
            .putString("report",text).putString("placementCheck",lastPlacementCheck)
            .putString("lastTouchPath",lastTouchPath).apply()
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
                    if (now-lastAnalyzed < 700L || captureFrozen) return@setOnImageAvailableListener
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
                            if (frame!=null) handler.post {
                                if (stopped || (prepared != null && !inProgress)) return@post
                                latest=frame; latestAt=now
                                if (frame.signature==stableSignature) stableCount++
                                else { stableSignature=frame.signature; stableCount=1 }
                                if (!inProgress) publish()
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
        // Keep the board unobstructed: the former always-visible resume and
        // calibration rows reached the first board row on this phone.
        val optionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        optionsRow.addView(Button(this).apply {
            text = "RESUME 1/2"
            textSize = 11f
            setOnClickListener { resumePartialTray() }
        })
        val adjustments = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val globalAdjustments = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tallAdjustments = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        adjustments.addView(globalAdjustments)
        adjustments.addView(tallAdjustments)
        shapeCalibrationRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        adjustments.addView(shapeCalibrationRow)
        val calibrationToggle = Button(this).apply {
            text = "SHOW CAL"
            textSize = 11f
            setOnClickListener {
                val opening = adjustments.visibility != View.VISIBLE
                adjustments.visibility = if (opening) View.VISIBLE else View.GONE
                text = if (opening) "HIDE CAL" else "SHOW CAL"
            }
        }
        optionsRow.addView(calibrationToggle)
        panel.addView(optionsRow)
        fun adjustButton(parent: LinearLayout, label: String, onPress: () -> Unit) {
            parent.addView(Button(this).apply {
                text = label
                textSize = 11f
                setOnClickListener { onPress() }
            })
        }
        adjustButton(globalAdjustments, "X -") { adjust(-20, 0) }
        adjustButton(globalAdjustments, "X +") { adjust(20, 0) }
        adjustButton(globalAdjustments, "Y -") { adjust(0, -20) }
        adjustButton(globalAdjustments, "Y +") { adjust(0, 20) }
        adjustButton(tallAdjustments, "TALL Y -") { adjustTall(-20) }
        adjustButton(tallAdjustments, "TALL Y +") { adjustTall(20) }
        shapeCalibrationRow?.let { row ->
            adjustButton(row, "PIECE X-") { adjustPiece(-20,0) }
            adjustButton(row, "PIECE X+") { adjustPiece(20,0) }
            adjustButton(row, "PIECE Y-") { adjustPiece(0,-20) }
            adjustButton(row, "PIECE Y+") { adjustPiece(0,20) }
        }
        panel.addView(adjustments)
        offsetLabel = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
        }
        panel.addView(offsetLabel)
        shapeCalibrationLabel = TextView(this).apply {
            setTextColor(0xFFB7F6CF.toInt())
            textSize = 11f
            text = "Per-piece calibration becomes available after PREVIEW."
        }
        panel.addView(shapeCalibrationLabel)
        shapeLabel = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            maxLines = 3
            text = "Tap PREVIEW to inspect the detected shape."
        }
        panel.addView(shapeLabel)
        coordsLabel = TextView(this).apply {
            setTextColor(0xFFE1F7FF.toInt())
            textSize = 11f
            maxLines = 2
            text = "Pickup/release coordinates appear after PREVIEW."
        }
        panel.addView(coordsLabel)
        updateOffsetLabel()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            // At the very top of the usable window, below Android's system bar.
            // The shortened panel ends well above the game's first board row.
            gravity = Gravity.TOP or Gravity.START
            x = 4
            y = 0
        }
        wm?.addView(panel,params)
        overlay = panel
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
        val toX = (163f + (plan.move.col + plan.observed.gripCol)*55f) * sx + effectiveX
        val toY = (454f + (plan.move.row + plan.observed.gripRow)*55f) * sy + effectiveY
        return floatArrayOf(fromX,fromY,toX,toY)
    }

    private fun updateMarker() {
        val plan = prepared ?: return
        shapeLabel?.text = "Piece ${plan.move.pieceIndex+1}: ${plan.observed.signature} " +
            "(${plan.observed.count} blocks); pickup tile: " +
            "row ${plan.observed.gripRow+1}, col ${plan.observed.gripCol+1}. " +
            "Shape correction: ${PlacementGeometry.shapeCorrectionCells(plan.observed.signature,plan.observed.tileColor)} cells; " +
            (if (PlacementGeometry.directTallAnchor(plan.piece.height,plan.piece.width))
                "Tall Y calibrated (trial). " else "") + "Check shape!"
        val screen = screenSize()
        val coords = gestureCoords(plan)
        val (actualX,actualY) = effectiveOffsets(plan,screen)
        val personal = pieceAdjustment(plan.observed)
        shapeCalibrationLabel?.text = "Current ${plan.observed.tileColor} ${plan.observed.signature}: " +
            "extra X=${personal.xPx}, Y=${personal.yPx}px (saved per shape+color)"
        coordsLabel?.text = "Finger: (${coords[0].toInt()}, ${coords[1].toInt()})" +
            " -> (${coords[2].toInt()}, ${coords[3].toInt()}) px; effective X=$actualX Y=$actualY"
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
        if (frame == null || frame.recognized !in 1..2 || stableCount < 2 ||
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
        val frame = latest
        val expectedPieces = 3 - (moveCount % 3)
        if (frame?.missingVisiblePiece == true) {
            setStatus("PREVIEW BLOCKED: At least one colored tray piece was not recognized. " +
                "Do NOT use RESUME to skip it. STOP and send the report.")
            return
        }
        if (frame == null || frame.recognized != expectedPieces || stableCount < 2 ||
            SystemClock.elapsedRealtime() - latestAt > 4000L || frame.width >= frame.height) {
            setStatus("Need a stable portrait game board with $expectedPieces/$expectedPieces " +
                "remaining pieces recognized. If a tile is missing, STOP and send the report.")
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
                    moveButton?.isEnabled = true
                    setStatus("PREVIEW only: piece ${move.pieceIndex+1}, row ${move.row+1}, col ${move.col+1}. " +
                        "${piece.normalized.size} green cells = full detected shape; blue = pickup tile; red = release. " +
                        "If count/shape differs from the real piece, STOP. Calibration X=$xOffset Y=$yOffset. " +
                        "Preset shape adjustment ${PlacementGeometry.shapeCorrectionCells(observed.signature,observed.tileColor)} cells; " +
                        "personal adjustment ${pieceAdjustment(observed)}. " +
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

    private fun attemptOneMove() {
        if (inProgress || sessionBlocked) return
        val plan = prepared ?: run { setStatus("Tap PREVIEW first."); return }
        if (TouchService.current == null) {
            setStatus("Enable the accessibility service before NEXT MOVE."); return
        }
        // Refuse stale previews and mismatched shapes rather than making a blind move.
        if (plan.piece.normalized.size != plan.observed.count) {
            sessionBlocked = true
            finish("Piece shape count mismatch. STOP; no move made.")
            return
        }
        if (SystemClock.elapsedRealtime()-plan.time > 30000L) {
            prepared = null; captureFrozen = false
            moveButton?.isEnabled = false; removeMarker()
            setStatus("Preview expired after 30 seconds. Wait for a new frame, then PREVIEW again.")
            return
        }
        val coords = gestureCoords(plan)
        val screen = screenSize()
        if (coords[0] !in 0f..screen.x.toFloat() || coords[1] !in 0f..screen.y.toFloat() ||
            coords[2] !in 0f..screen.x.toFloat() || coords[3] !in 0f..screen.y.toFloat()) {
            setStatus("Drag would leave the screen. Adjust X/Y or TALL Y, then check the preview."); return
        }
        // Hard safety limit: refuse release points beyond the board edge.
        // Reference board centers run x=163..548, y=454..839; one half-cell
        // margin allows a little touch imprecision without off-board drags.
        val boardLeft = (163f - 27.5f) * screen.x / BoardRecognizer.REF_WIDTH
        val boardRight = (548f + 27.5f) * screen.x / BoardRecognizer.REF_WIDTH
        val boardTop = (454f - 27.5f) * screen.y / BoardRecognizer.REF_HEIGHT
        val boardBottom = (839f + 27.5f) * screen.y / BoardRecognizer.REF_HEIGHT
        if (coords[2] !in boardLeft..boardRight || coords[3] !in boardTop..boardBottom) {
            setStatus("BLOCKED: finger release (${coords[2].toInt()},${coords[3].toInt()}) " +
                "is outside board limits. Do not move; inspect grip/lift calibration.")
            return
        }
        inProgress = true
        captureFrozen = false
        moveButton?.isEnabled = false
        previewButton?.isEnabled = false
        removeMarker()
        prepared = null
        shapeLabel?.text = "Moving one piece. Wait for verification."
        lastGestureOutcome = "Waiting for Android gesture result"
        lastTouchPath = "Piece ${plan.move.pieceIndex+1} ${plan.observed.signature}: " +
            "pickup (${coords[0].toInt()},${coords[1].toInt()}) -> " +
            "release (${coords[2].toInt()},${coords[3].toInt()}) screen px, " +
            "global X=$xOffset Y=$yOffset, tall delta=$tallDelta, " +
            "piece extra=${pieceAdjustment(plan.observed)}; effective=${effectiveOffsets(plan,screen)}"
        lastPlacementCheck = "PENDING: Drag started; awaiting settled post-release game frame."
        shapeCalibrationLabel?.text = "Current move: awaiting settled frame (6-8 seconds)."
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
                    // An image captured *during* the drag is not proof of the
                    // board after release. Require a settled post-release frame.
                    val recent = latestAt >= verificationStarted + 350L &&
                        SystemClock.elapsedRealtime() - latestAt < 2000L
                    val pieceRemoved = actual?.pieces?.get(plan.move.pieceIndex) == null
                    val lastPiece = plan.frame.recognized == 1
                    val boardMatched = actual?.board == plan.expected
                    if (recent && stableCount >= 2 && boardMatched && (pieceRemoved || lastPiece)) {
                        moveCount++
                        verifiedThisSession++
                        finish("VERIFIED: Move $verifiedThisSession this session placed successfully. " +
                            "Tap PREVIEW for the next piece (manual confirmation required).")
                        return
                    }
                    if (SystemClock.elapsedRealtime() - verificationStarted > 6000L) {
                        sessionBlocked = true
                        val diagnosis = if (recent && actual != null)
                            displacementReport(plan, actual.board)
                            else "No settled post-release game frame was captured."
                        finish("UNVERIFIED: $diagnosis STOP and inspect game. " +
                            "No further moves this session.")
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
        previewButton?.isEnabled = !sessionBlocked
        setStatus(message)
    }
    override fun onDestroy() {
        // If the user stops before the 6-second verification period completes,
        // preserve that fact instead of saying no move was attempted.
        if (inProgress) lastPlacementCheck =
            "INCOMPLETE: Capture stopped before placement verification finished. " +
            "Do not assume the drag succeeded."
        stopped=true
        captureFrozen=false
        removeMarker()
        try { overlay?.let { wm?.removeView(it) } } catch (_: Exception) {}
        overlay=null; moveButton=null; previewButton=null; offsetLabel=null; shapeLabel=null
        coordsLabel=null; shapeCalibrationLabel=null; shapeCalibrationRow=null; prepared=null
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
