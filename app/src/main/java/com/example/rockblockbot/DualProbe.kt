package com.example.rockblockbot

import kotlin.math.abs
import kotlin.math.hypot

/** Two separated dwell positions in ONE unbroken accessibility gesture. */
object DualProbe {
    const val SWEEPS_PER_STOP = 120
    const val SWEEP_PX = 6f
    const val DURATION_MS = 5700L
    data class Windows(val first: ProbeTiming.Window, val second: ProbeTiming.Window)

    fun windows(fromX:Float,fromY:Float,ax:Float,ay:Float,bx:Float,by:Float):Windows {
        val into=hypot((ax-fromX).toDouble(),(ay-fromY).toDouble())
        val between=hypot((bx-ax).toDouble(),(by-ay).toDouble())
        val out=hypot((fromX-bx).toDouble(),(fromY-by).toDouble())
        // Each stop's path starts with a 6px excursion, oscillates 120
        // times and returns the 6px to center before transitioning.
        val sweep=2.0 * SWEEPS_PER_STOP * SWEEP_PX
        val total=into+between+out+2*sweep
        fun at(distance:Double)=((distance/total)*DURATION_MS).toLong()
        val a=ProbeTiming.Window(at(into)+145L,at(into+sweep)-145L)
        val b=ProbeTiming.Window(at(into+sweep+between)+145L,
            at(into+sweep+between+sweep)-145L)
        return Windows(a,b)
    }

    data class Verdict(val first: ShadowTracker.Match?, val second: ShadowTracker.Match?,
                       val accepted:Boolean, val reason:String)
    fun validate(a:List<ShadowTracker.Match>, b:List<ShadowTracker.Match>,
                 shiftRow:Int,shiftCol:Int):Verdict {
        // At least two frames at EACH of two physically different finger positions.
        if(abs(shiftRow)+abs(shiftCol)!=1)return Verdict(null,null,false,"Probe endpoints are not one cell apart")
        fun winner(samples:List<ShadowTracker.Match>):Pair<ShadowTracker.Match,Int>? {
            val grouped=samples.groupBy { it.row to it.col }.toList().sortedByDescending{it.second.size}
            val first=grouped.firstOrNull() ?: return null
            if(first.second.size<2 || first.second.size <= (grouped.getOrNull(1)?.second?.size ?: 0))return null
            val modes=first.second.map { it.fallback }.distinct()
            if(modes.size!=1)return null
            return first.second.maxByOrNull{it.strength}!! to first.second.size
        }
        val first=winner(a)
        val second=winner(b)
        if(first==null || second==null)
            return Verdict(first?.first,second?.first,false,"Need >=2 agreeing captures at BOTH distinct endpoints")
        val x=first.first;val y=second.first
        if(y.row-x.row!=shiftRow || y.col-x.col!=shiftCol)
            return Verdict(x,y,false,"Shadow did not follow the expected one-cell probe shift")
        if(x.fallback!=y.fallback)
            return Verdict(x,y,false,"Two positions used inconsistent recognition modes")
        return Verdict(x,y,true,"Two-position geometry verified (${first.second}+${second.second} agreeing frames)")
    }
}
