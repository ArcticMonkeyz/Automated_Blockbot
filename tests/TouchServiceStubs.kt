package android.accessibilityservice

import android.view.accessibility.AccessibilityEvent
import java.util.ArrayDeque

object FakeGestureDriver {
    val pending = ArrayDeque<AccessibilityService.GestureResultCallback>()
    var dispatched = 0
    fun completeNext() {
        check(pending.isNotEmpty()) { "No Android segment waiting" }
        pending.removeFirst().onCompleted(GestureDescription())
    }
    fun cancelNext() {
        check(pending.isNotEmpty()) { "No Android segment waiting" }
        pending.removeFirst().onCancelled(GestureDescription())
    }
    fun reset() { pending.clear(); dispatched = 0 }
}

class GestureDescription {
    class StrokeDescription {
        constructor(path:android.graphics.Path, startTime:Long, duration:Long, willContinue:Boolean)
        fun continueStroke(path:android.graphics.Path, startTime:Long, duration:Long, willContinue:Boolean) =
            StrokeDescription(path, startTime, duration, willContinue)
    }
    class Builder {
        fun addStroke(stroke:StrokeDescription) = this
        fun build() = GestureDescription()
    }
}
open class AccessibilityService {
    open fun onServiceConnected(){}
    open fun onDestroy(){}
    open fun onAccessibilityEvent(event:AccessibilityEvent?){}
    open fun onInterrupt(){}
    open class GestureResultCallback {
        open fun onCompleted(gestureDescription:GestureDescription?){}
        open fun onCancelled(gestureDescription:GestureDescription?){}
    }
    fun dispatchGesture(gesture:GestureDescription, callback:GestureResultCallback?, handler:Any?):Boolean {
        FakeGestureDriver.dispatched++
        if (callback != null) FakeGestureDriver.pending.addLast(callback)
        return true
    }
}
