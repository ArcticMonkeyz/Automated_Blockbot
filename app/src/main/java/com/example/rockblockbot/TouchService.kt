package com.example.rockblockbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One manually requested two-stage drag. The first stroke keeps the pointer down;
 * the second continues it at the destination for a short hold, then releases it.
 * No retries or autonomous move loops.
 */
class TouchService : AccessibilityService() {
    companion object { @Volatile var current: TouchService? = null; private set }

    override fun onServiceConnected() { super.onServiceConnected(); current = this }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** callback(true, detail) only when Android reports BOTH gesture stages completed. */
    fun dragWithHold(
        fromX: Float, fromY: Float, toX: Float, toY: Float,
        callback: (Boolean, String) -> Unit
    ): Boolean {
        val delivered = AtomicBoolean(false)
        fun report(ok: Boolean, detail: String) {
            if (delivered.compareAndSet(false, true)) callback(ok, detail)
        }

        // Android AccessibilityService supports continued strokes on API 26+.
        val travel = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val firstStroke = GestureDescription.StrokeDescription(travel, 0, 850, true)
        val first = GestureDescription.Builder().addStroke(firstStroke).build()
        return dispatchGesture(first, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                // A nonzero but subpixel path avoids a zero-length path rejection.
                // This continuation maintains the same finger contact until it ends.
                val hold = Path().apply {
                    moveTo(toX, toY)
                    lineTo(toX + 0.5f, toY + 0.5f)
                }
                val finalStroke = try {
                    firstStroke.continueStroke(hold, 0, 350, false)
                } catch (e: Exception) {
                    report(false, "Could not create hold: ${e.message}")
                    return
                }
                val second = GestureDescription.Builder().addStroke(finalStroke).build()
                val accepted = dispatchGesture(second, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        report(true, "Android completed drag and 350 ms hold/release")
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        report(false, "Android cancelled hold/release stage")
                    }
                }, null)
                if (!accepted) report(false, "Android rejected hold/release stage")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                report(false, "Android cancelled drag stage")
            }
        }, null)
    }
}
