package com.example.rockblockbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * One continuous accessibility finger, with a keep-alive gesture dispatched
 * immediately after EVERY successful segment. Computer vision runs separately
 * while the finger is already being held by the next Android segment.
 *
 * In v4.4 the screen-capture/UI callback had to request the next segment, and
 * a brief delay caused Android to cancel it. Here the accessibility service
 * owns continuation and callers QUEUE movement/release commands instead.
 * Only one finger and one move are supported at a time.
 */
class TouchService : AccessibilityService() {
    companion object { @Volatile var current: TouchService? = null; private set }

    private enum class Action { MOVE, RELEASE }
    private data class Command(
        val action: Action, val x: Float, val y: Float, val durationMs: Long,
        val callback: (Boolean, String) -> Unit
    )

    private var lastStroke: GestureDescription.StrokeDescription? = null
    private var fingerX = 0f
    private var fingerY = 0f
    private var pickupX = 0f
    private var pickupY = 0f
    private var held = false
    private var busy = false
    private var pending: Command? = null
    private var segmentNumber = 0
    private var failureReporter: ((Boolean, String) -> Unit)? = null

    val isHolding get() = held
    val isBusy get() = busy

    override fun onServiceConnected() { super.onServiceConnected(); current = this }
    override fun onDestroy() { reset(); if (current === this) current = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun reset() {
        held = false
        busy = false
        lastStroke = null
        pending = null
        failureReporter = null
    }

    private fun fail(reason: String, extra: ((Boolean, String) -> Unit)? = null) {
        val owner = failureReporter
        val queued = pending?.callback
        reset()
        owner?.invoke(false, reason)
        if (queued !== owner) queued?.invoke(false, reason)
        if (extra !== owner && extra !== queued) extra?.invoke(false, reason)
    }

    /** Travel into the playable board on the FIRST stroke. It must not stop in
     * the tray waiting for a shadow that the game cannot display there.
     */
    fun beginHeldDrag(fromX: Float, fromY: Float, toX: Float, toY: Float,
                      callback: (Boolean, String) -> Unit): Boolean {
        if (held || busy) return false
        pickupX = fromX; pickupY = fromY
        fingerX = fromX; fingerY = fromY
        segmentNumber = 0
        failureReporter = callback
        held = true
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val first = GestureDescription.StrokeDescription(path, 0, 1250L, true)
        val started = dispatchSegment(first, toX, toY, true, null, callback)
        if (!started) reset()
        return started
    }

    /** Nonblocking: queue a movement for the next segment boundary. Android's
     * pointer is already kept down by the active continuation segment.
     */
    fun continueHeldDrag(toX: Float, toY: Float, durationMs: Long = 750L,
                         callback: (Boolean, String) -> Unit): Boolean {
        if (!held || pending != null) return false
        pending = Command(Action.MOVE, toX, toY, durationMs.coerceIn(300L, 1300L), callback)
        return true
    }

    /** Release only when the game's observed placement ghost matches target. */
    fun releaseHeldDrag(callback: (Boolean, String) -> Unit): Boolean {
        if (!held || pending != null) return false
        pending = Command(Action.RELEASE, fingerX, fingerY, 220L, callback)
        return true
    }

    /** Abort attempts to return the held piece toward its original tray slot.
     * This is NOT guaranteed after Android has cancelled the finger.
     */
    fun returnToTray(callback: (Boolean, String) -> Unit): Boolean {
        if (!held) { callback(false, "No held pointer; check board and tray"); return false }
        // A STOP/safety abort takes priority over a queued corrective move.
        // An already-queued release cannot be undone safely.
        if (pending?.action == Action.RELEASE) return false
        pending = null
        return continueHeldDrag(pickupX, pickupY, 850L) { moved, msg ->
            if (!moved) callback(false, "Return failed: $msg")
            else if (!releaseHeldDrag { released, detail ->
                callback(released, "Tray release: $detail")
            }) callback(false, "Could not queue tray release")
        }
    }

    /** Reliable fallback: ONE Android gesture from pickup to the board and back.
     * There is no continueStroke/segment 2; the single path releases in the tray.
     * The short low-amplitude sweep near the board-entry point leaves time for
     * MediaProjection to measure the game's predicted placement ghost.
     * A return-to-tray is attempted, NOT guaranteed if the game snaps early.
     */
    fun probeWithReturn(fromX: Float, fromY: Float, entryX: Float, entryY: Float,
                        callback: (Boolean, String) -> Unit): Boolean {
        if (busy || held) return false
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(entryX, entryY)
            // Long tiny oscillation DWELLS over the board long enough to
            // capture several independent game-shadow frames. It stays in
            // approximately the same snap cell and never releases early.
            repeat(ProbeTiming.SWEEP_COUNT) { k ->
                lineTo(entryX + if (k % 2 == 0) ProbeTiming.SWEEP_PX
                          else -ProbeTiming.SWEEP_PX, entryY)
            }
            lineTo(entryX, entryY)
            lineTo(fromX, fromY)
        }
        return dispatchSingle(path, ProbeTiming.DURATION_MS,
            "Probe completed; returned toward tray", callback)
    }

    /** Two board dwell locations separated by exactly one grid cell, all in
     * ONE gesture. This allows the occluded-preview detector to distinguish a
     * stationary existing block from the game's moving placement shadow.
     * The gesture ends in the original tray, never on the board intentionally.
     */
    fun probeWithTwoStops(fromX:Float,fromY:Float,ax:Float,ay:Float,
                          bx:Float,by:Float,callback:(Boolean,String)->Unit):Boolean {
        if(busy || held)return false
        val path=Path().apply {
            moveTo(fromX,fromY)
            lineTo(ax,ay)
            for(k in 0 until DualProbe.SWEEPS_PER_STOP)
                lineTo(ax + if(k%2==0) DualProbe.SWEEP_PX else -DualProbe.SWEEP_PX,ay)
            lineTo(ax,ay)
            lineTo(bx,by)
            for(k in 0 until DualProbe.SWEEPS_PER_STOP)
                lineTo(bx + if(k%2==0) DualProbe.SWEEP_PX else -DualProbe.SWEEP_PX,by)
            lineTo(bx,by)
            lineTo(fromX,fromY)
        }
        return dispatchSingle(path,DualProbe.DURATION_MS,
            "Two-stop probe completed; returned toward tray",callback)
    }

    /** ONE unbroken Android stroke, with a small final dwell before natural
     * ACTION_UP. Unlike the experimental held mode, no follow-up gesture can
     * accidentally cancel the finger on segment 2.
     * Only call after a valid shadow-probe calibration exists.
     */
    fun calibratedSingleDrag(fromX: Float, fromY: Float, toX: Float, toY: Float,
                             callback: (Boolean, String) -> Unit): Boolean {
        if (busy || held) return false
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
            repeat(80) { k ->
                lineTo(toX + if (k % 2 == 0) 2f else -2f, toY)
            }
            lineTo(toX, toY)
        }
        return dispatchSingle(path, 1700L, "Calibrated single stroke finished and released", callback)
    }

    private fun dispatchSingle(path: Path, duration: Long, message: String,
                               callback: (Boolean, String) -> Unit): Boolean {
        busy = true
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration, false)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                busy = false
                callback(true, message)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                busy = false
                callback(false, "Android cancelled the one-stroke gesture; inspect board/tray")
            }
        }, null)
        if (!accepted) busy = false
        return accepted
    }

    /** Never wait for a UI or vision callback before requesting the next hold. */
    private fun continueImmediately(): Boolean {
        if (!held) return false
        val preceding = lastStroke ?: return false
        val command = pending
        pending = null
        val nextX = if (command?.action == Action.MOVE) command.x else fingerX
        val nextY = if (command?.action == Action.MOVE) command.y else fingerY
        val continueAfter = command?.action != Action.RELEASE
        val duration = command?.durationMs ?: 850L
        // Zero-distance paths are valid Android touch-and-hold strokes. Keep
        // both endpoints identical to avoid cumulative 0.2px finger drift.
        val path = Path().apply { moveTo(fingerX, fingerY); lineTo(nextX, nextY) }
        val stroke = try {
            preceding.continueStroke(path, 0L, duration, continueAfter)
        } catch (e: Exception) {
            fail("Cannot chain held segment: ${e.message}", command?.callback)
            return false
        }
        val ok = dispatchSegment(stroke, nextX, nextY, continueAfter, command, null)
        if (!ok) fail("Android rejected continuation segment ${segmentNumber + 1}", command?.callback)
        return ok
    }

    private fun dispatchSegment(
        stroke: GestureDescription.StrokeDescription,
        nextX: Float, nextY: Float, continueAfter: Boolean,
        command: Command?, firstCallback: ((Boolean, String) -> Unit)?
    ): Boolean {
        val segment = ++segmentNumber
        busy = true
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!held) return
                busy = false
                fingerX = nextX; fingerY = nextY
                lastStroke = if (continueAfter) stroke else null
                // Crucially, schedule the next Android stroke HERE, not from
                // the service's delayed UI/frame-processing callback.
                if (continueAfter) {
                    if (!continueImmediately()) return
                } else {
                    reset()
                }
                firstCallback?.invoke(true, "Initial travel completed; held pointer continues")
                command?.callback?.invoke(true,
                    if (continueAfter) "Segment $segment completed; finger remains held"
                    else "Segment $segment completed; pointer released")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (held) fail("Android cancelled continuous gesture segment $segment", command?.callback)
            }
        }, null)
        if (!accepted) { busy = false; return false }
        return true
    }
}
