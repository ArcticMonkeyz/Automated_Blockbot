package com.example.rockblockbot

/** Derived from an actual game placement ghost during a safe return probe.
 * Coordinates here are real display pixels, not game texture pixels.
 * No Android API calls, so the coordinate model is separately testable.
 */
object GripMapping {
    data class Offset(val x:Float,val y:Float)
    fun fromProbe(probeFingerX:Float, probeFingerY:Float,
                  observedCol:Int,observedRow:Int,
                  width:Int,height:Int):Offset = Offset(
        probeFingerX-ShadowTracker.boardX(observedCol,width),
        probeFingerY-ShadowTracker.boardY(observedRow,height)
    )
    fun releaseFor(targetCol:Int,targetRow:Int,cal:Offset,width:Int,height:Int):Pair<Float,Float> =
        Pair(ShadowTracker.boardX(targetCol,width)+cal.x,
             ShadowTracker.boardY(targetRow,height)+cal.y)
}
