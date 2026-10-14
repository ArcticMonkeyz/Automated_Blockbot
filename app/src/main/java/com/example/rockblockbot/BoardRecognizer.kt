package com.example.rockblockbot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Reference game geometry: 709 x 1536. Tray pieces are independently scaled.
 * A pixel-fit alone can accidentally explain four tiles using three; this
 * detector first counts separable tile stripes and then fits a full grid.
 * Recognition is deliberately conservative when the image is ambiguous.
 */
object BoardRecognizer {
    const val REF_WIDTH = 709f
    const val REF_HEIGHT = 1536f

    data class ObservedPiece(
        val rows: List<String>,
        val gripX: Float, val gripY: Float,
        val gripRow: Int, val gripCol: Int,
        val pitch: Int, val fit: Double,
        val knownShape: Boolean,
        val stripesX: Int, val stripesY: Int
    ) {
        fun asPiece(): Piece = Piece(rows.flatMapIndexed { r, line ->
            line.mapIndexedNotNull { c, ch -> if (ch == '#') Cell(r, c) else null }
        })
        val signature get() = rows.joinToString("/")
        val count get() = rows.sumOf { row -> row.count { it == '#' } }
    }

    data class Frame(val board: Long, val pieces: List<ObservedPiece?>,
                     val width: Int, val height: Int) {
        val recognized get() = pieces.count { it != null }
        val signature get() = board.toString() + ":" +
            pieces.joinToString("|") { it?.signature ?: "?" }
        fun boardText(): String = (0..7).joinToString("\n") { r ->
            (0..7).joinToString("") { c ->
                if ((board and (1L shl (r*8+c))) != 0L) "#" else "."
            }
        }
        fun report(): String = "Captured ${width}x${height} | recognized $recognized/3 pieces\n" +
            "Board: ${java.lang.Long.bitCount(board)} occupied cells\n${boardText()}\n\n" +
            pieces.mapIndexed { i, p ->
                "Piece ${i+1}: " + (p?.let {
                    "${it.signature} (${it.count} blocks, pitch ${it.pitch}, " +
                    "fit ${"%.2f".format(java.util.Locale.US, it.fit)}, " +
                    "stripes ${it.stripesX}x${it.stripesY}, " +
                    (if (it.knownShape) "library match" else "unlisted shape") + ")"
                } ?: "NOT RECOGNIZED")
            }.joinToString("\n")
    }

    private enum class TileColor { RED, GREEN, BLUE, PURPLE, YELLOW }
    private fun matches(pixel: Int, color: TileColor): Boolean {
        val r=Color.red(pixel); val g=Color.green(pixel); val b=Color.blue(pixel)
        return when(color) {
            TileColor.RED -> r>140 && r>g*1.55 && r>b*1.40 && g<135
            TileColor.GREEN -> g>155 && g>r*1.35 && g>b*1.18
            TileColor.BLUE -> (b>212 && g>165 && b>g+28 && r<125) ||
                (b>155 && r<65 && b>g*1.22)
            TileColor.PURPLE -> b>135 && r>90 && r>g*1.60 && b>g*1.50
            TileColor.YELLOW -> r>155 && g>115 && b<115 && r>b*1.60
        }
    }
    private fun isBlock(pixel: Int) = TileColor.entries.any { matches(pixel,it) }
    private fun fraction(img: Bitmap, cx: Int, cy: Int, radius: Int,
                         color: TileColor? = null): Double {
        var total=0; var hits=0
        for (dy in -radius..radius step 3) for (dx in -radius..radius step 3) {
            val x=cx+dx; val y=cy+dy
            if (x in 0 until img.width && y in 0 until img.height) {
                total++
                if (if (color==null) isBlock(img.getPixel(x,y))
                    else matches(img.getPixel(x,y),color)) hits++
            }
        }
        return if (total==0) 0.0 else hits.toDouble()/total
    }
    private data class Zone(val left:Int,val right:Int,val top:Int=1045,val bottom:Int=1320)
    // Keep complete wide pieces in each slot. The old left crop began at
    // x=115 and cut off the first tile of five-wide pieces near x=90.
    private val zones=listOf(Zone(75,275),Zone(275,440),Zone(440,685))
    private val knownFootprints by lazy { ShapeLibrary.patterns.toSet() }

    /** Long strong-color bands separated by dark tile seams. */
    private fun bands(profile: IntArray): List<IntRange> {
        val peak=profile.maxOrNull() ?: 0
        if (peak<5) return emptyList()
        val threshold=peak*0.18
        val result=ArrayList<IntRange>()
        var start=-1
        for (i in 0..profile.size) {
            val strong=i<profile.size && profile[i]>threshold
            if (strong && start<0) start=i
            if (!strong && start>=0) {
                if (i-start>=10) result.add(start until i)
                start=-1
            }
        }
        return result
    }

    private fun reliable(bands:List<IntRange>, extent:Int):Boolean {
        if (bands.isEmpty() || bands.size>6) return false
        if (bands.any { it.last-it.first+1 !in 15..58 }) return false
        if (bands.size>1) {
            val centers=bands.map { (it.first+it.last)/2.0 }
            if (centers.zipWithNext().any { (a,b)-> b-a !in 27.0..53.0 }) return false
        }
        val coverage=(bands.last().last-bands.first().first+1).toDouble()/extent
        return coverage>=0.73
    }

    private data class Candidate(
        val rows:List<String>,val pitch:Int,val score:Double,val recall:Double,
        val gripX:Float,val gripY:Float,val gripRow:Int,val gripCol:Int,
        val known:Boolean
    )

    /** Dark vertical seams distinguish 5 small horizontal tiles from 4 larger
     * ones when the color mask spans across the seams (common with purple).
     * Only used as a tie-breaker for single-row, wide tray pieces.
     */
    private fun seamBonus(image:Bitmap,zone:Zone,minX:Int,minY:Int,
                          boxW:Int,boxH:Int,pitch:Int,cols:Int):Double {
        if(cols<2 || boxH>56 || boxW<boxH*2) return 0.0
        val yStart=zone.top+minY+minOf(8,boxH/5)
        val yEnd=zone.top+minY+boxH-minOf(8,boxH/5)
        if(yEnd<=yStart) return 0.0
        fun brightness(x:Int):Double {
            var sum=0.0;var n=0
            for(y in yStart until yEnd step 3) {
                if(x in 0 until image.width && y in 0 until image.height) {
                    val p=image.getPixel(x,y)
                    sum+=(Color.red(p)+Color.green(p)+Color.blue(p))/3.0
                    n++
                }
            }
            return if(n==0) 0.0 else sum/n
        }
        var bonus=0.0
        for(k in 1 until cols) {
            val x=zone.left+minX+k*pitch
            if(x-8<0 || x+8>=image.width) continue
            val center=(brightness(x-1)+brightness(x)+brightness(x+1))/3.0
            val sides=(brightness(x-7)+brightness(x+7))/2.0
            val contrast=((sides-center)/255.0).coerceAtLeast(0.0)
            bonus+=(contrast-0.035).coerceAtLeast(0.0)*0.29
        }
        return bonus.coerceAtMost(0.24)
    }

    private fun detectPiece(image:Bitmap,zone:Zone):ObservedPiece? {
        val colors=TileColor.entries
        val counts=IntArray(colors.size)
        for (y in zone.top until zone.bottom step 2) for (x in zone.left until zone.right step 2) {
            val pixel=image.getPixel(x,y)
            colors.forEachIndexed { i,color-> if(matches(pixel,color)) counts[i]++ }
        }
        val dominant=counts.indices.maxByOrNull { counts[it] } ?: return null
        if (counts[dominant]<150) return null
        val color=colors[dominant]
        val zoneW=zone.right-zone.left; val zoneH=zone.bottom-zone.top
        val mask=BooleanArray(zoneW*zoneH)
        var minX=zoneW;var maxX=-1;var minY=zoneH;var maxY=-1;var pixelCount=0
        for(y in 0 until zoneH) for(x in 0 until zoneW) {
            val hit=matches(image.getPixel(zone.left+x,zone.top+y),color)
            mask[y*zoneW+x]=hit
            if(hit) { pixelCount++; minX=minOf(minX,x);maxX=maxOf(maxX,x)
                minY=minOf(minY,y);maxY=maxOf(maxY,y) }
        }
        if(pixelCount<500 || maxX<minX) return null
        val boxW=maxX-minX+1;val boxH=maxY-minY+1
        val xProfile=IntArray(boxW);val yProfile=IntArray(boxH)
        for(y in minY..maxY) for(x in minX..maxX) if(mask[y*zoneW+x]) {
            xProfile[x-minX]++; yProfile[y-minY]++
        }
        val xb=bands(xProfile);val yb=bands(yProfile)
        val validX=reliable(xb,boxW);val validY=reliable(yb,boxH)

        // Fast, shape-preserving route: when both axes have clear tile seams,
        // their stripe centers are more reliable than a guessed tile pitch.
        if(validX && validY && xb.size<=5 && yb.size<=6) {
            val xs=xb.map { zone.left+minX+(it.first+it.last)/2 }
            val ys=yb.map { zone.top+minY+(it.first+it.last)/2 }
            val rows=ys.map { cy -> buildString {
                for(cx in xs) append(if(fraction(image,cx,cy,7,color)>.24) '#' else '.')
            } }
            val candidate=makeCandidate(rows,xs,ys,0,1.0)
            if(candidate!=null && candidate.rows.sumOf { it.count { ch->ch=='#' } } in 1..9) {
                // Require the chosen cells to account for nearly all of the
                // detected colored pixels. Prevents silently dropping a tile.
                var explained=0
                val offsetRow=candidateTop(rows)
                val offsetCol=candidateLeft(rows)
                for(y in minY..maxY) for(x in minX..maxX) if(mask[y*zoneW+x]) {
                    val matchesCell=yb.indices.any { r -> xb.indices.any { c ->
                        val rr=r-offsetRow
                        val cc=c-offsetCol
                        rr in candidate.rows.indices && cc in candidate.rows[rr].indices &&
                        candidate.rows[rr][cc]=='#' &&
                        x-minX in (xb[c].first-6)..(xb[c].last+6) &&
                        y-minY in (yb[r].first-6)..(yb[r].last+6)
                    } }
                    if(matchesCell) explained++
                }
                val coverage=explained.toDouble()/pixelCount
                if(coverage>=0.88) {
                    return ObservedPiece(candidate.rows,candidate.gripX,candidate.gripY,
                        candidate.gripRow,candidate.gripCol,candidate.pitch,coverage,
                        candidate.known,xb.size,yb.size)
                }
            }
        }

        // Fallback for L / zigzag shapes where projections merge across rows.
        val stride=zoneW+1
        val integral=IntArray(stride*(zoneH+1))
        for(y in 0 until zoneH) {
            var running=0
            for(x in 0 until zoneW) {
                if(mask[y*zoneW+x]) running++
                integral[(y+1)*stride+x+1]=integral[y*stride+x+1]+running
            }
        }
        fun rect(x0:Int,y0:Int,x1:Int,y1:Int):Int {
            val a=x0.coerceIn(0,zoneW);val b=x1.coerceIn(0,zoneW)
            val c=y0.coerceIn(0,zoneH);val d=y1.coerceIn(0,zoneH)
            if(a>=b || c>=d) return 0
            return integral[d*stride+b]-integral[c*stride+b]-
                integral[d*stride+a]+integral[c*stride+a]
        }
        var best:Candidate?=null
        for(pitch in 30..46) {
            val cols=((boxW.toDouble()/pitch).roundToInt()).coerceIn(1,5)
            val rows=((boxH.toDouble()/pitch).roundToInt()).coerceIn(1,6)
            if(validX && xb.size>1 && cols!=xb.size) continue
            if(validY && yb.size>1 && rows!=yb.size) continue
            val detected=ArrayList<String>(rows)
            var covered=0;var area=0
            for(r in 0 until rows) {
                val line=buildString {
                    for(c in 0 until cols) {
                        val x0=minX+c*pitch;val y0=minY+r*pitch
                        val cx=zone.left+x0+pitch/2;val cy=zone.top+y0+pitch/2
                        val yes=fraction(image,cx,cy,7,color)>.27
                        append(if(yes) '#' else '.')
                        if(yes) { covered+=rect(x0,y0,x0+pitch,y0+pitch);area+=pitch*pitch }
                    }
                }
                detected.add(line)
            }
            val nonEmpty=detected.filter { '#' in it }
            if(nonEmpty.isEmpty()) continue
            val left=nonEmpty.minOf { it.indexOf('#') }
            val right=nonEmpty.maxOf { it.lastIndexOf('#') }
            val trimmed=nonEmpty.map { it.substring(left,right+1) }
            val n=trimmed.sumOf { it.count { ch->ch=='#' } }
            if(n !in 1..9) continue
            val recall=covered.toDouble()/pixelCount
            val precision=covered.toDouble()/area.coerceAtLeast(1)
            val f1=2*recall*precision/(recall+precision).coerceAtLeast(0.00001)
            val pattern=trimmed.joinToString("/")
            val known=pattern in knownFootprints
            // The piece library is a weak tie-breaker, not a whitelist: some
            // observed shapes are missing from the extracted 41 footprints.
            val score=f1+(if(known) .012 else 0.0)+
                (if(rows==1) seamBonus(image,zone,minX,minY,boxW,boxH,pitch,cols) else 0.0)
            val firstRow=detected.indexOfFirst { '#' in it }
            val gripRow=trimmed.indexOfLast { '#' in it }
            val gripCol=trimmed[gripRow].indexOf('#')
            val gx=zone.left+minX+(left+gripCol+.5f)*pitch
            val gy=zone.top+minY+(firstRow+gripRow+.5f)*pitch
            val candidate=Candidate(trimmed,pitch,score,recall,gx,gy,gripRow,gripCol,known)
            if(best==null || candidate.score>best.score) best=candidate
        }
        val chosen=best ?: return null
        // Reject explanations that omit a substantial part of the colored
        // mask. Three detected blocks cannot explain four real blocks.
        if (chosen.known) {
            if(chosen.recall<0.82 || chosen.score<0.70) return null
        } else {
            // The extracted shape list is incomplete, but low-fit unknown
            // shapes are more likely to be a bad crop or an animation frame.
            if(chosen.recall<0.86 || chosen.score<0.80) return null
        }
        return ObservedPiece(chosen.rows,chosen.gripX,chosen.gripY,
            chosen.gripRow,chosen.gripCol,chosen.pitch,chosen.score.coerceAtMost(1.0),
            chosen.known,if(validX)xb.size else 0,if(validY)yb.size else 0)
    }

    private fun candidateTop(rows:List<String>)=rows.indexOfFirst { '#' in it }
    private fun candidateLeft(rows:List<String>)=rows.filter { '#' in it }.minOf { it.indexOf('#') }

    private fun makeCandidate(rows:List<String>,xs:List<Int>,ys:List<Int>,pitch:Int,score:Double):Candidate? {
        val top=rows.indexOfFirst { '#' in it };if(top<0)return null
        val bottom=rows.indexOfLast { '#' in it }
        val left=rows.filter { '#' in it }.minOf { it.indexOf('#') }
        val right=rows.filter { '#' in it }.maxOf { it.lastIndexOf('#') }
        val trimmed=rows.subList(top,bottom+1).map { it.substring(left,right+1) }
        val gripRow=trimmed.indexOfLast { '#' in it }
        val gripCol=trimmed[gripRow].indexOf('#')
        val shape=trimmed.joinToString("/")
        val inferredPitch=if(xs.size>1) (xs.last()-xs.first())/(xs.size-1)
            else if(ys.size>1) (ys.last()-ys.first())/(ys.size-1) else 40
        return Candidate(trimmed,if(pitch>0)pitch else inferredPitch,score,1.0,
            xs[left+gripCol].toFloat(),ys[top+gripRow].toFloat(),gripRow,gripCol,
            shape in knownFootprints)
    }

    /** Null means this is not a game frame; preserve the last valid report. */
    fun analyze(bitmap:Bitmap):Frame? {
        val image=Bitmap.createScaledBitmap(bitmap,REF_WIDTH.toInt(),REF_HEIGHT.toInt(),true)
        try {
            // Sample outside the floating control panel. Its dark background can
            // cover (350,300) in entire-screen capture.
            val p=image.getPixel(650,280)
            if(!(Color.red(p)<160 && Color.blue(p)>Color.red(p)+45 &&
                    Color.blue(p)>Color.green(p)+14)) return null
            var board=0L
            for(r in 0..7) for(c in 0..7) {
                if(fraction(image,163+c*55,454+r*55,14)>.55)
                    board=board or (1L shl (r*8+c))
            }
            return Frame(board,zones.map { detectPiece(image,it) },bitmap.width,bitmap.height)
        } finally { if(image!==bitmap) image.recycle() }
    }
}
