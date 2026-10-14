package com.example.rockblockbot

import kotlin.math.abs

/** Conservative, Android-independent guards around the restored single-stop
 * shadow calibration. These deliberately do NOT require the finger to be over
 * the board: in this game, the held piece is drawn well above the touch point.
 */
object CalibrationGuard {
    data class Reading(val match: ShadowTracker.Match, val atMs: Long)
    data class Agreement(val match: ShadowTracker.Match, val count: Int,
                         val competing: Int, val spanMs: Long)

    /** Two different frames at least 200 ms apart, with an unambiguous winner.
     * Stationary frames are NOT a second spatial probe; this is intentionally
     * a limited confidence check for the previously working one-stroke path.
     */
    fun agreement(samples: List<Reading>): Agreement? {
        val ranked = samples.groupBy { it.match.row to it.match.col }
            .values.sortedByDescending { it.size }
        val best = ranked.firstOrNull() ?: return null
        val competitor = ranked.getOrNull(1)?.size ?: 0
        val span = (best.maxOf { it.atMs } - best.minOf { it.atMs })
        if (best.size < 2 || best.size <= competitor || span < 200L) return null
        val strongest = best.maxByOrNull { it.match.strength + it.match.confidence }!!.match
        return Agreement(strongest, best.size, competitor, span)
    }

    fun fits(board: Long, footprint: List<Cell>, row: Int, col: Int): Boolean {
        if (footprint.isEmpty() || footprint.size > 9) return false
        for (cell in footprint) {
            val r = row + cell.r
            val c = col + cell.c
            if (r !in 0..7 || c !in 0..7 || (board and (1L shl (r * 8 + c))) != 0L)
                return false
        }
        return true
    }

    /** Reject implausible or off-footprint measurements before persisting them.
     * The four-cell purple diagnostic had a 3-row difference, so that is NOT
     * treated as a failure; distant, likely unrelated color patches are.
     */
    fun validProbe(board: Long, footprint: List<Cell>, plannedRow: Int, plannedCol: Int,
                   observedRow: Int, observedCol: Int, fingerX: Float, fingerY: Float,
                   width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0 || !fingerX.isFinite() || !fingerY.isFinite()) return false
        if (!fits(board, footprint, plannedRow, plannedCol) ||
            !fits(board, footprint, observedRow, observedCol)) return false
        if (abs(observedRow - plannedRow) > 4 || abs(observedCol - plannedCol) > 4) return false
        if (abs(observedRow - plannedRow) + abs(observedCol - plannedCol) > 5) return false
        if (fingerX !in 12f..(width-12f) || fingerY !in 12f..(height-12f)) return false
        val offset = GripMapping.fromProbe(fingerX, fingerY, observedCol, observedRow, width, height)
        val pitchX = ShadowTracker.pitchX(width)
        val pitchY = ShadowTracker.pitchY(height)
        // Finger can be several rows BELOW the visible drop shadow. These
        // loose limits reject wildly unrelated detections without assuming a
        // touch coordinate must fall inside the visual board rectangle.
        return offset.x in (-5.5f * pitchX)..(5.5f * pitchX) &&
            offset.y in (-5f * pitchY)..(9f * pitchY)
    }

    /** Apply ONLY adjustments made AFTER a learned grip was measured, thereby
     * avoiding double-applying the global and shape bias already present at
     * the probe. Large unvalidated edits require another calibration.
     */
    fun release(board: Long, footprint: List<Cell>, row: Int, col: Int,
                grip: GripMapping.Offset, manualDeltaX: Int, manualDeltaY: Int,
                width: Int, height: Int): Pair<Float,Float>? {
        if (width <= 0 || height <= 0 || !grip.x.isFinite() || !grip.y.isFinite()) return null
        if (!fits(board,footprint,row,col)) return null
        if (abs(manualDeltaX.toFloat()) > 1.25f * ShadowTracker.pitchX(width) ||
            abs(manualDeltaY.toFloat()) > 1.25f * ShadowTracker.pitchY(height)) return null
        if (grip.x !in (-5.5f * ShadowTracker.pitchX(width))..(5.5f * ShadowTracker.pitchX(width)) ||
            grip.y !in (-5f * ShadowTracker.pitchY(height))..(9f * ShadowTracker.pitchY(height))) return null
        val (baseX,baseY) = GripMapping.releaseFor(col,row,grip,width,height)
        val x = baseX + manualDeltaX
        val y = baseY + manualDeltaY
        return if (x.isFinite() && y.isFinite() && x in 12f..(width-12f) &&
            y in 12f..(height-12f)) x to y else null
    }
}
