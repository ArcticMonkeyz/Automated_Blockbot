package com.example.rockblockbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/** Optional gesture helper, not connected to solver in this prototype. */
class TouchService : AccessibilityService() {
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
 override fun onInterrupt() {}
 fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float): Boolean {
  val path = Path().apply { moveTo(fromX,fromY); lineTo(toX,toY) }
  val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,450)).build()
  return dispatchGesture(gesture,null,null)
 }
}
