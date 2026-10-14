package com.example.rockblockbot

import kotlin.math.abs

/**
 * Pure, testable detector for the GAME'S translucent drag-landing preview.
 * We do not follow the solid raised block: it sits above the actual drop cells.
 * Only empty board tiles are considered, and the predicted footprint must fit.
 * Pixel coordinates are based on the same board-centre reference as the solver.
 */
object ShadowTracker {
    data class Target(val occupied: Long, val cells: List<Cell>, val row: Int, val col: Int,
                      val color: String)
    data class Match(val row: Int, val col: Int, val strength: Double,
                     val confidence: Double, val litCells: Int, val alternatives: Int)
    data class Scan(val match: Match?, val diagnostic: String)
    private const val REF_W = 709f
    private const val REF_H = 1536f

    fun boardX(col: Int, width: Int) = ((163f + 55f * col) * width / REF_W).toInt()
    fun boardY(row: Int, height: Int) = ((454f + 55f * row) * height / REF_H).toInt()
    fun pitchX(width: Int) = 55f * width / REF_W
    fun pitchY(height: Int) = 55f * height / REF_H

    /** Piece colors are distinctly saturated; the *translucent* preview is
     * moderately desaturated against beige stone. Opaque pieces are rejected.
     * Calibrated from the supplied RED 3x3 held-drag example. Other colors
     * are experimental until captured under an active drag.
     */
    fun isGhostPixel(pixel: Int, pieceColor: String): Boolean {
        val r = (pixel ushr 16) and 255
        val g = (pixel ushr 8) and 255
        val b = pixel and 255
        return when (pieceColor) {
            "RED" -> r > 165 && g in 85..202 && b in 76..200 && r-g > 24 && r-b > 24 && abs(g-b) < 70
            "PURPLE" -> b > 135 && g in 85..215 && r > 115 && b-g > 20 && r-g > 12 && b-r > -18
            "BLUE" -> b > 155 && r in 72..210 && g > 120 && b-r > 23 && b-g > 12
            "GREEN" -> g in 155..244 && r in 82..215 && b in 65..215 && g-r in 24..120 && g-b in 21..120
            "YELLOW" -> r > 165 && g > 126 && b in 62..185 && r-b > 32 && g-b > 26
            else -> false
        }
    }

    private fun cellScore(w: Int, h: Int, readPixel: (Int, Int) -> Int,
                          row: Int, col: Int, color: String): Double {
        val x = boardX(col,w)
        val y = boardY(row,h)
        val rx = (pitchX(w) * .16f).toInt().coerceAtLeast(3)
        val ry = (pitchY(h) * .16f).toInt().coerceAtLeast(3)
        var hits=0; var samples=0
        for (dy in listOf(-ry,0,ry)) for (dx in listOf(-rx,0,rx)) {
            val sx=x+dx;val sy=y+dy
            if (sx in 0 until w && sy in 0 until h) {
                samples++
                if (isGhostPixel(readPixel(sx,sy),color)) hits++
            }
        }
        return if (samples==0) 0.0 else hits.toDouble()/samples
    }

    /** Return null if the colored ghost is absent, ambiguous, or occluded.
     * Require a strong majority of tile interiors and a clear best footprint;
     * *never* use a best guess as permission to release the pointer.
     */
    fun locate(width: Int, height: Int, readPixel: (Int, Int) -> Int,
               target: Target): Match? = inspect(width,height,readPixel,target).match

    fun inspect(width: Int, height: Int, readPixel: (Int, Int) -> Int,
                target: Target): Scan {
        if (target.cells.isEmpty() || target.cells.size>9)
            return Scan(null,"Invalid target footprint")
        val heat=DoubleArray(64)
        for (row in 0..7) for (col in 0..7) {
            val index=row*8+col
            if ((target.occupied and (1L shl index))==0L)
                heat[index]=cellScore(width,height,readPixel,row,col,target.color)
        }
        data class Candidate(val r:Int,val c:Int,val mean:Double,val lit:Int,val minimum:Double)
        val candidates=ArrayList<Candidate>()
        for (r in 0..7) for (c in 0..7) {
            var sum=0.0; var lit=0; var minimum=1.0;var legal=true
            for (cell in target.cells) {
                val rr=r+cell.r;val cc=c+cell.c
                if (rr !in 0..7 || cc !in 0..7 || (target.occupied and (1L shl (rr*8+cc)))!=0L) {
                    legal=false;break
                }
                val s=heat[rr*8+cc]
                sum+=s
                if(s>=.55) lit++
                if(s<minimum)minimum=s
            }
            if (legal) candidates.add(Candidate(r,c,sum/target.cells.size,lit,minimum))
        }
        if (candidates.isEmpty())return Scan(null,"No legal empty board placement")
        val ranked=candidates.sortedWith(compareByDescending<Candidate>{it.mean}.thenByDescending{it.lit})
        val best=ranked.first()
        val required=if (target.cells.size<=3) target.cells.size else (target.cells.size*.75).toInt().coerceAtLeast(3)
        val second=ranked.drop(1).firstOrNull()
        val margin=best.mean-(second?.mean?:0.0)
        val stats="best r${best.r+1} c${best.c+1}, " +
            "colored cells ${best.lit}/${target.cells.size}, " +
            "strength ${"%.2f".format(java.util.Locale.US,best.mean)}, " +
            "margin ${"%.2f".format(java.util.Locale.US,margin)}"
        if (best.mean < .69 || best.lit < required || best.minimum < .23)
            return Scan(null,"Low/occluded shadow: $stats")
        // Adjacent positions can share most cells: never release when ambiguous.
        if (margin < .095)return Scan(null,"Ambiguous nearby shadows: $stats")
        return Scan(Match(best.r,best.c,best.mean,margin,best.lit,ranked.size),
                    "Tracked: $stats")
    }
}
