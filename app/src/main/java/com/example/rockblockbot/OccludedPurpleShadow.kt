package com.example.rockblockbot

import kotlin.math.abs

/**
 * Conservative rescue detector for the four-wide purple strip when the raised
 * (opaque) piece covers all but the trailing end of the translucent landing preview.
 * The tight preview fringe visible in the Oct 14 two-position export measured
 * 0.22 at its exposed end; 0.28 was too strict. 0.19 is a guarded rescue
 * threshold, not an independent authorization to calibrate or place.
 *
 * No result from this detector may independently enable NEXT MOVE: two distinct
 * finger endpoints must yield matching one-cell displacement AND the piece must
 * return to the tray. Cells known occupied before the drag are never evidence.
 * This restriction prevents the purple piece already on the board from becoming
 * a false calibration target.
 */
object OccludedPurpleShadow {
    data class Scan(val match: ShadowTracker.Match?, val diagnostic: String)

    fun supported(target: ShadowTracker.Target): Boolean =
        target.color == "PURPLE" && target.cells.size == 4 &&
            target.cells.map { it.r }.distinct().size == 1 &&
            target.cells.map { it.c }.sorted() == listOf(0,1,2,3)

    private fun opaquePurple(pixel: Int): Boolean {
        val r = (pixel ushr 16) and 255
        val g = (pixel ushr 8) and 255
        val b = pixel and 255
        // Deliberately separate solid magenta from the pastel ghost classifier.
        return r >= 80 && b >= 125 && b-r > 15 && b-g > 65 && r-g > 43 && g < 100
    }

    fun inspect(width: Int, height: Int, readPixel: (Int,Int) -> Int,
                target: ShadowTracker.Target): Scan {
        if (!supported(target)) return Scan(null, "Occluded detector supports only purple horizontal 4-strip")
        val ghost=DoubleArray(64)
        val solid=DoubleArray(64)
        for(r in 0..7) for(c in 0..7) {
            if((target.occupied and (1L shl (r*8+c)))!=0L) continue
            val cx=ShadowTracker.boardX(c,width)
            val cy=ShadowTracker.boardY(r,height)
            val dx=ShadowTracker.pitchX(width)*.40f
            val dy=ShadowTracker.pitchY(height)*.40f
            var a=0; var g=0; var s=0
            for(iy in 0..8) for(ix in 0..8) {
                val x=(cx+(ix-4)*dx/4f).toInt()
                val y=(cy+(iy-4)*dy/4f).toInt()
                if(x !in 0 until width || y !in 0 until height) continue
                val pixel=readPixel(x,y);a++
                if(ShadowTracker.isGhostPixel(pixel,"PURPLE"))g++
                if(opaquePurple(pixel))s++
            }
            if(a>0){ ghost[r*8+c]=g.toDouble()/a;solid[r*8+c]=s.toDouble()/a }
        }
        data class Candidate(val r:Int,val c:Int,val score:Double,val ghosts:Int,val solids:Int)
        val candidates=ArrayList<Candidate>()
        for(r in 0..7) for(c in 0..4) {
            // Never infer a far-away shadow from just one pale pixel.
            if(target.probeRow!=null && abs(r-target.probeRow)>4)continue
            if(target.probeCol!=null && abs(c-target.probeCol)>4)continue
            val indices=(0..3).map{r*8+c+it}
            if(indices.any{(target.occupied and (1L shl it))!=0L})continue
            val g=indices.count{ghost[it]>=.19}
            val s=indices.count{solid[it]>=.36}
            val filled=indices.count{ghost[it]>=.19 || solid[it]>=.36}
            // Solid strip covers 3 cells and a separate exposed cell shows ghost.
            // No single-pixel or all-solid footprint can ever pass.
            if(g!=1 || s<3 || filled!=4)continue
            val ghostIndex=indices.indexOfFirst{ghost[it]>=.19}
            if(ghostIndex!=3 && ghostIndex!=0)continue // exposed end only
            val score=indices.sumOf { i -> maxOf(ghost[i],solid[i]) }/4.0
            candidates.add(Candidate(r,c,score,g,s))
        }
        val ranked=candidates.sortedByDescending{it.score}
        val best=ranked.firstOrNull() ?: return Scan(null,
            "No usable exposed preview fringe (needs 3 solid cells plus an exposed ghost end; near board edge, move probe left)")
        val gap=best.score-(ranked.getOrNull(1)?.score ?: 0.0)
        val stats="best r${best.r+1} c${best.c+1}, solids ${best.solids}/4, visible preview-end ${best.ghosts}, " +
            "strength ${"%.2f".format(java.util.Locale.US,best.score)}, margin ${"%.2f".format(java.util.Locale.US,gap)}"
        if(ranked.size>1 && gap<.10) return Scan(null,"Ambiguous composite: $stats")
        if(best.score<.46)return Scan(null,"Weak composite: $stats")
        return Scan(ShadowTracker.Match(best.r,best.c,best.score,gap,1,ranked.size,true),
            "PROVISIONAL occlusion composite: $stats (two-position confirmation required)")
    }
}
