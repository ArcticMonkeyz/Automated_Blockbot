package com.example.rockblockbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/** Serial Android gesture segments that keep ONE finger pressed while vision
 * checks the live game. Every segment continues the preceding StrokeDescription.
 * A segment completion is not a placement; only release commits the move.
 */
class TouchService : AccessibilityService() {
    companion object { @Volatile var current: TouchService? = null; private set }
    private var lastStroke: GestureDescription.StrokeDescription? = null
    private var fingerX=0f
    private var fingerY=0f
    private var pickupX=0f
    private var pickupY=0f
    private var busy=false
    val isHolding get() = lastStroke != null
    val isBusy get() = busy

    override fun onServiceConnected() { super.onServiceConnected(); current=this }
    override fun onDestroy() { lastStroke=null; if(current===this)current=null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun beginHeldDrag(fromX:Float,fromY:Float,toX:Float,toY:Float,
                      callback:(Boolean,String)->Unit):Boolean {
        if(busy || lastStroke!=null) return false
        pickupX=fromX;pickupY=fromY;fingerX=toX;fingerY=toY
        val path=Path().apply { moveTo(fromX,fromY);lineTo(toX,toY) }
        val stroke=GestureDescription.StrokeDescription(path,0,900,true)
        busy=true
        return dispatchSegment(stroke,true,callback)
    }

    /** Continue the held pointer. Use a slow move followed by a separate hold
     * segment before evaluating the shadow; don't measure midway through motion.
     */
    fun continueHeldDrag(toX:Float,toY:Float, durationMs:Long=950L,
                         callback:(Boolean,String)->Unit):Boolean {
        if(busy) return false
        val previous=lastStroke ?: return false
        val stationary=(toX==fingerX && toY==fingerY)
        val endX=if(stationary)toX+.2f else toX
        val endY=if(stationary)toY+.2f else toY
        val path=Path().apply {
            moveTo(fingerX,fingerY)
            lineTo(endX,endY)
        }
        val stroke=try { previous.continueStroke(path,0,durationMs,true) }
                   catch(e:Exception){ callback(false,"Continue failed: ${e.message}");return false }
        fingerX=endX;fingerY=endY;busy=true
        return dispatchSegment(stroke,true,callback)
    }

    fun releaseHeldDrag(callback:(Boolean,String)->Unit):Boolean {
        if(busy) return false
        val previous=lastStroke ?: return false
        val path=Path().apply { moveTo(fingerX,fingerY);lineTo(fingerX+.2f,fingerY+.2f) }
        val stroke=try { previous.continueStroke(path,0,220,false) }
                   catch(e:Exception){callback(false,"Release failed: ${e.message}");return false}
        busy=true
        return dispatchSegment(stroke,false,callback)
    }

    /** Safe-abort attempt: drag back to pickup, THEN release in the tray.
     * This is best-effort, not a guarantee if Android cancels the gesture.
     */
    fun returnToTray(callback:(Boolean,String)->Unit):Boolean {
        if(busy) return false
        if(lastStroke==null) { callback(true,"No finger held");return true }
        val accepted=continueHeldDrag(pickupX,pickupY,800L) { success,msg ->
            if(!success) callback(false,"Return travel failed: $msg")
            else {
                val ok=releaseHeldDrag { released,detail ->
                    callback(released,"Returned toward tray: $detail")
                }
                if(!ok) callback(false,"Return release could not start")
            }
        }
        return accepted
    }

    private fun dispatchSegment(stroke:GestureDescription.StrokeDescription, continuing:Boolean,
                                callback:(Boolean,String)->Unit):Boolean {
        val gesture=GestureDescription.Builder().addStroke(stroke).build()
        val accepted=dispatchGesture(gesture,object:GestureResultCallback(){
            override fun onCompleted(gestureDescription:GestureDescription?) {
                busy=false
                lastStroke=if(continuing)stroke else null
                callback(true,if(continuing)"Pointer held" else "Pointer released")
            }
            override fun onCancelled(gestureDescription:GestureDescription?) {
                busy=false;lastStroke=null
                callback(false,"Android cancelled a held-drag segment")
            }
        },null)
        if(!accepted){busy=false;lastStroke=null}
        return accepted
    }
}
