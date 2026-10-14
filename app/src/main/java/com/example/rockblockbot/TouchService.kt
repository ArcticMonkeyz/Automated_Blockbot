package com.example.rockblockbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/** Dispatches exactly one user-requested drag. Never performs background loops. */
class TouchService : AccessibilityService() {
    companion object { @Volatile var current: TouchService? = null; private set }
    override fun onServiceConnected() { super.onServiceConnected(); current = this }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, callback: (Boolean) -> Unit): Boolean {
        val path = Path().apply { moveTo(fromX,fromY); lineTo(toX,toY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path,0,650)).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { callback(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { callback(false) }
        }, null)
    }
}
