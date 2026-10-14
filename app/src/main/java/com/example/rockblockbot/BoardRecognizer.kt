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
        val stripesX: Int, val stripesY: Int,
        val tileColor: String = ""
    ) {
        fun asPiece(): Piece = Piece(rows.flatMapIndexed { r, line ->
            line.mapIndexedNotNull { c, ch -> if (ch == '#') Cell(r, c) else null }
        })
        val signature get() = rows.joinToString("/")
        val count get() = rows.sumOf { row -> row.count { it == '#' } }
    }

    data class Frame(val board: Long, val pieces: List<ObservedPiece?>,
                     val width: Int, val height: Int,
                     val trayEvidence: List<Boolean> = List(3) { false }) {
        val missingVisiblePiece: Boolean get() = pieces.indices.any { i ->
            pieces[i] == null && trayEvidence.getOrElse(i) { false }
        }
        val recognized get() = pieces.count { it != null }
        val signature get() = board.toString() + ":" +
            pieces.joinToString("|") { it?.let { p -> "${p.tileColor}:${p.signature}" } ?: "?" }
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
                    "stripes ${it.stripesX}x${it.stripesY}, color ${it.tileColor}, direct geometry)"
                } ?: if (trayEvidence.getOrElse(i) { false })
                    "NOT RECOGNIZED (colored piece still visible)" else "NOT RECOGNIZED (empty/uncertain)")
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
    // Seed regions sit INSIDE the three tray slots. Unlike the old hard
    // cropping boundaries, these only identify the piece color. A five-tile
    // middle strip can physically extend across x=440 and must remain whole.
    private val seeds = listOf(Zone(80,245),Zone(290,425),Zone(470,645))

    private fun dominantSeedColor(image: Bitmap, zone: Zone): TileColor? {
        val counts = IntArray(TileColor.entries.size)
        for(y in zone.top until zone.bottom step 2) for(x in zone.left until zone.right step 2) {
            val p = image.getPixel(x,y)
            TileColor.entries.forEachIndexed { i,color -> if(matches(p,color)) counts[i]++ }
        }
        val i = counts.indices.maxByOrNull { counts[it] } ?: return null
        return TileColor.entries[i].takeIf { counts[i] >= 150 }
    }

    /** Overlapping search windows are SAFE when neighbors have different
     * colors: the mask uses this slot's seed color only. When adjacent pieces
     * share a color, do not merge them. In that case retain the old boundary,
     * rejecting clipped shapes instead of inventing a shorter piece.
     */
    private fun extendedZone(slot: Int, colors: List<TileColor?>): Zone {
        val c=colors[slot]
        val sameLeft=slot>0 && c!=null && c==colors[slot-1]
        val sameRight=slot<2 && c!=null && c==colors[slot+1]
        return when(slot) {
            0 -> Zone(65, if(sameRight) 275 else 325)
            1 -> Zone(if(sameLeft) 275 else 215, if(sameRight) 440 else 520)
            else -> Zone(if(sameLeft) 440 else 395, 690)
        }
    }
    private val knownFootprints by lazy { ShapeLibrary.patterns.toSet() }

    private val fixedZones=listOf(Zone(75,275),Zone(275,440),Zone(440,685))

    /** A search region only needs widening if there is a real continuation of
     * the SAME-colored object beyond its old edge. Low-count antialias fringes
     * from different-color neighbors are not a continuation. For same-color
     * neighbors the expanded zone is disabled by extendedZone().
     */
    private fun needsExpansion(image: Bitmap, fixed: Zone, wide: Zone,
                               color: TileColor): Boolean {
        // The water background shares BLUE color channels with actual tiles.
        // Never expand blue from off-slot background evidence alone.
        if(color == TileColor.BLUE) return false
        var outsideHits=0
        var strongColumns=0
        for(x in wide.left until wide.right) {
            if(x in fixed.left until fixed.right) continue
            var hits=0
            for(y in 1080 until 1285 step 2) if(matches(image.getPixel(x,y),color)) hits++
            if(hits>=7) strongColumns++
            outsideHits+=hits
        }
        // These are half-resolution samples. A real clipped tile edge spans
        // many neighboring columns; spurious border coloration is sparse.
        return strongColumns>=7 && outsideHits>=85
    }

    /** Independent colored-object test for each tray slot. Recognition may
     * reject a valid piece because a seam is obscured; that must never be
     * treated as an empty slot by RESUME. This tests colored pixels, NOT
     * whether a library footprint matches or a piece shape was solved.
     */
    private fun trayHasPieceEvidence(image: Bitmap, zone: Zone): Boolean {
        var hits = 0
        for (y in 1080 until 1295 step 3) for (x in zone.left until zone.right step 3) {
            val p = image.getPixel(x, y)
            if (matches(p, TileColor.RED) || matches(p, TileColor.GREEN) ||
                matches(p, TileColor.PURPLE) || matches(p, TileColor.YELLOW) ||
                matches(p, TileColor.BLUE)) {
                hits++
                if (hits >= 75) return true
            }
        }
        return false
    }

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

    /** Measure vertical seam valleys directly, independently of tile height.
     * Captures can stretch the vertical dimension of a tray piece, so using
     * bounding width / bounding height can mistake #### for ###.
     */
    private fun horizontalSeamCount(
        image: Bitmap, zone: Zone, minX: Int, minY: Int,
        boxW: Int, core: IntRange
    ): Int? {
        val first = zone.top + minY + core.first
        val last = zone.top + minY + core.last
        if (last-first < 23) return null
        val y0 = first + (last-first) / 4
        val y1 = last - (last-first) / 4
        fun luminance(x: Int): Double {
            if (x !in 0 until image.width) return 0.0
            var sum = 0.0; var n = 0
            for (y in y0..y1 step 3) {
                val p = image.getPixel(x, y)
                sum += (Color.red(p) * 0.30 + Color.green(p) * 0.59 + Color.blue(p) * 0.11)
                n++
            }
            return if (n>0) sum/n else 0.0
        }
        var bestCount = 0
        var bestContrast = 0.0
        for (n in 2..6) {
            val pitch = boxW.toDouble()/n
            if (pitch !in 29.0..49.0) continue
            var sum = 0.0
            var minimum = Double.POSITIVE_INFINITY
            for (k in 1 until n) {
                val predictedX = (zone.left + minX + k*pitch).roundToInt()
                // The colored-pixel bounding edge may be offset from the
                // true seam by a few pixels. Find the local darkest line.
                var strongest = 0.0
                for (x in predictedX-3..predictedX+3) {
                    if (x-7 < 0 || x+7 >= image.width) continue
                    val center = (luminance(x-1)+luminance(x)+luminance(x+1))/3.0
                    val sides = (luminance(x-7)+luminance(x+7))/2.0
                    strongest = maxOf(strongest, sides-center)
                }
                sum += strongest
                minimum = minOf(minimum, strongest)
            }
            // Real tile edges create dark vertical valleys at EVERY seam.
            // False subdivisions of a solid tile generally do not.
            if (minimum < 12.0) continue
            val average = sum / (n-1)
            if (average > bestContrast) {
                bestContrast = average
                bestCount = n
            }
        }
        return bestCount.takeIf { it>0 }
    }

    /** Read one-row strips from their colored center and real seam geometry.
     * Do not infer a strip count solely from its visual tile height.
     */
    private fun detectStraightStrip(
        image: Bitmap, zone: Zone, color: TileColor,
        minX: Int, minY: Int, boxW: Int,
        yProfile: IntArray, pixelCount: Int
    ): ObservedPiece? {
        fun coreBand(profile: IntArray): IntRange? {
            val peak = profile.maxOrNull() ?: return null
            if (peak < 14) return null
            val threshold = peak * 0.63
            var bestStart=-1; var bestEnd=-1; var start=-1
            for(i in 0..profile.size) {
                val strong=i<profile.size && profile[i]>=threshold
                if(strong && start<0) start=i
                if(!strong && start>=0) {
                    if(i-start>bestEnd-bestStart) {bestStart=start;bestEnd=i}
                    start=-1
                }
            }
            if(bestStart<0 || bestEnd-bestStart !in 24..61) return null
            val explained=(bestStart until bestEnd).sumOf {profile[it]}
            if(explained.toDouble()/pixelCount<0.86) return null
            return bestStart until bestEnd
        }
        val core=coreBand(yProfile) ?: return null
        val thickness=core.count()
        if(boxW<thickness*1.75) return null
        val fromSeams=horizontalSeamCount(image,zone,minX,minY,boxW,core)
        val count=fromSeams ?: (boxW.toDouble()/thickness).roundToInt()
        if(count !in 2..6) return null
        val pitch=boxW.toDouble()/count
        if(pitch !in 29.0..49.0) return null
        // If the seams are not independently visible, require agreement
        // between height and width. Otherwise prefer actual seam geometry.
        if(fromSeams==null && abs(pitch-thickness)>thickness*.13) return null
        val lanePixels=IntArray(count)
        for(v in core) for(u in 0 until boxW) {
            val pixel=image.getPixel(zone.left+minX+u,zone.top+minY+v)
            if(matches(pixel,color)) {
                val lane=(u/pitch).toInt().coerceIn(0,count-1)
                lanePixels[lane]++
            }
        }
        val minLane=thickness*pitch*.40
        if(lanePixels.any {it<minLane}) return null
        val rows=listOf("#".repeat(count))
        return ObservedPiece(rows,
            (zone.left+minX+pitch/2).toFloat(),
            (zone.top+minY+(core.first+core.last)/2.0).toFloat(),
            0,0,pitch.roundToInt(),
            (lanePixels.sum().toDouble()/pixelCount).coerceIn(0.0,1.0),
            rows.first() in knownFootprints,count,1,color.name)
    }

    private fun detectPiece(image:Bitmap,zone:Zone,color:TileColor?,pruneFringe:Boolean):ObservedPiece? {
        // The seed selects a color. The wider zone measures its FULL extent.
        // Different-color neighboring pieces can touch without merging.
        if(color==null) return null
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
        // Any substantial matching-color pixels exactly at the search window's
        // vertical sides mean a shape may extend OUTSIDE our observation.
        // Never turn a physically clipped five-strip into a four-strip move.
        val boundaryHits = (0 until zoneH).count { y ->
            mask[y*zoneW] || mask[y*zoneW + zoneW-1]
        }
        if(boundaryHits>=12 && color != TileColor.BLUE) return null
        // The game sea/sky sometimes satisfies the BLUE classifier. Remove
        // disconnected blue speckles and sky patches before measuring the
        // physical block mask. Individual blue tiles may be separate components
        // (due to their seams), so keep all substantial components, not just
        // the biggest one.
        if (color == TileColor.BLUE) {
            val labels = IntArray(mask.size) { -1 }
            val areas = ArrayList<Int>()
            val queue = IntArray(mask.size)
            for (i in mask.indices) {
                if (!mask[i] || labels[i] >= 0) continue
                val id = areas.size
                var head=0;var tail=1;queue[0]=i;labels[i]=id
                while (head<tail) {
                    val q=queue[head++]
                    val cy=q/zoneW; val cx=q%zoneW
                    for (dy in -1..1) for(dx in -1..1) {
                        if (dy==0 && dx==0) continue
                        val nx=cx+dx;val ny=cy+dy
                        if(nx !in 0 until zoneW || ny !in 0 until zoneH) continue
                        val ni=ny*zoneW+nx
                        if(mask[ni] && labels[ni]<0) {labels[ni]=id;queue[tail++]=ni}
                    }
                }
                areas.add(tail)
            }
            val largest=areas.maxOrNull() ?: 0
            val minArea=maxOf(120,(largest*0.20).toInt())
            minX=zoneW;maxX=-1;minY=zoneH;maxY=-1;pixelCount=0
            for (i in mask.indices) {
                if (!mask[i]) continue
                if (areas[labels[i]]<minArea) {mask[i]=false;continue}
                val x=i%zoneW;val y=i/zoneW
                pixelCount++;minX=minOf(minX,x);maxX=maxOf(maxX,x)
                minY=minOf(minY,y);maxY=maxOf(maxY,y)
            }
            if(pixelCount<500 || maxX<minX) return null
        }
        if(pruneFringe) {
        // Neighboring different-color tiles may produce thin antialiasing
        // fringes that match this color (e.g. 1-2 green pixels per column
        // across the yellow piece). Expanded scan zones must not treat that
        // one-pixel halo as a fifth green tile. Keep columns that have enough
        // genuine tile-color coverage; do not alter occupied center pixels.
        run {
            val profile=IntArray(zoneW)
            for(y in 0 until zoneH) for(x in 0 until zoneW) {
                if(mask[y*zoneW+x]) profile[x]++
            }
            val peak=profile.maxOrNull() ?: 0
            val minimum=maxOf(8, (peak*0.20).toInt())
            val first=(minX..maxX).firstOrNull { profile[it]>=minimum }
            val last=(maxX downTo minX).firstOrNull { profile[it]>=minimum }
            if(first!=null && last!=null && first<=last) {
                for(y in 0 until zoneH) for(x in 0 until zoneW) {
                    if(mask[y*zoneW+x] && x !in first..last) {
                        mask[y*zoneW+x]=false
                        pixelCount--
                    }
                }
                minX=first;maxX=last
            }
        }
        if(pixelCount<500 || maxX<minX) return null
        }
        // A handful of cyan pixels in the sea/sky background can be matched as
        // BLUE at the edge of the right tray zone. In a captured device frame
        // two such pixels inflated an 80x122 zigzag into a 178x159 mask and
        // caused recognition to reject the real remaining piece.
        // Trim tiny extreme tails (0.2% each side) before estimating tile pitch.
        // This is color/geometry-only, not a shape library lookup.
        fun trimAxis(horizontal: Boolean, low: Int, high: Int): Pair<Int,Int> {
            val profile = IntArray(if(horizontal)zoneW else zoneH)
            for (y in 0 until zoneH) for (x in 0 until zoneW) {
                if (mask[y*zoneW+x]) profile[if(horizontal)x else y]++
            }
            val allowance = (pixelCount * 0.002).toInt().coerceAtLeast(3)
            var count=0; var start=low
            for (i in low..high) {
                count+=profile[i]
                if (count>allowance) {start=i;break}
            }
            count=0;var end=high
            for (i in high downTo low) {
                count+=profile[i]
                if (count>allowance) {end=i;break}
            }
            return start to end
        }
        val (trimMinX,trimMaxX)=trimAxis(true,minX,maxX)
        val (trimMinY,trimMaxY)=trimAxis(false,minY,maxY)
        // Remove outliers from both the bounding box and the later coverage
        // denominator; otherwise two stray pixels can poison a valid shape.
        if(trimMinX<=trimMaxX && trimMinY<=trimMaxY) {
            minX=trimMinX;maxX=trimMaxX;minY=trimMinY;maxY=trimMaxY
            pixelCount=0
            for(y in 0 until zoneH) for(x in 0 until zoneW) {
                if (mask[y*zoneW+x] && (x !in minX..maxX || y !in minY..maxY)) {
                    mask[y*zoneW+x]=false
                }
                if (mask[y*zoneW+x]) pixelCount++
            }
        }
        if(pixelCount<500) return null
        val boxW=maxX-minX+1;val boxH=maxY-minY+1
        val xProfile=IntArray(boxW);val yProfile=IntArray(boxH)
        for(y in minY..maxY) for(x in minX..maxX) if(mask[y*zoneW+x]) {
            xProfile[x-minX]++; yProfile[y-minY]++
        }
        val xb=bands(xProfile);val yb=bands(yProfile)
        val validX=reliable(xb,boxW);val validY=reliable(yb,boxH)
        // Detect straight pieces before the fallback pixel-fit. This must run
        // even when visual seams merge and bands() sees only one wide stripe.
        detectStraightStrip(image, zone, color, minX, minY, boxW,
            yProfile, pixelCount)?.let { return it }

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
                        candidate.known,xb.size,yb.size,color.name)
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
            val cols=((boxW.toDouble()/pitch).roundToInt()).coerceIn(1,6)
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
            // Do not explain a four-tile strip as three tiles by leaving a
            // large sliver of the actual colored extent outside the grid.
            // Check BOTH bounding axes before trusting a fallback pitch.
            if(abs(cols*pitch-boxW) > maxOf(10.0,pitch*.26)) continue
            if(abs(rows*pitch-boxH) > maxOf(12.0,pitch*.38)) continue
            val recall=covered.toDouble()/pixelCount
            val precision=covered.toDouble()/area.coerceAtLeast(1)
            val f1=2*recall*precision/(recall+precision).coerceAtLeast(0.00001)
            // A small candidate pitch can subdivide a real tile into two
            // phantom blocks. For example a 3x2 L at ~40px pitch can be
            // incorrectly explained as a 4x3 9-block rectangle at 30px.
            // Require high cell-area precision before accepting dense grids.
            if (n >= 7 && (precision < 0.84 || recall < 0.84)) continue
            if (n <= 6 && (precision < 0.70 || recall < 0.76)) continue
            val pattern=trimmed.joinToString("/")
            val known=pattern in knownFootprints
            // The piece library is a weak tie-breaker, not a whitelist: some
            // observed shapes are missing from the extracted 41 footprints.
            val geometryPenalty = 0.15 * abs(cols*pitch-boxW).toDouble()/boxW +
                0.03 * abs(rows*pitch-boxH).toDouble()/boxH
            val score=f1-geometryPenalty+
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
        if(chosen.recall<0.80 || chosen.score<0.77) return null
        // Any confident straight strip was already handled by the independent
        // color-core detector above; the fallback handles irregular pieces.
        return ObservedPiece(chosen.rows,chosen.gripX,chosen.gripY,
            chosen.gripRow,chosen.gripCol,chosen.pitch,chosen.score.coerceAtMost(1.0),
            chosen.known,if(validX)xb.size else 0,if(validY)yb.size else 0,color.name)
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
            val colors=seeds.map { dominantSeedColor(image,it) }
            val pieces=colors.indices.map { i ->
                val fixed=fixedZones[i]
                val wide=extendedZone(i,colors)
                val useWide=colors[i]?.let { needsExpansion(image,fixed,wide,it) } ?: false
                val zone=if(useWide) wide else fixed
                // A malformed continuation must be rejected rather than
                // returning a plausible but shorter fixed-zone fallback.
                detectPiece(image,zone,colors[i],useWide)
            }
            val evidence=seeds.map { trayHasPieceEvidence(image,it) }
            return Frame(board,pieces,bitmap.width,bitmap.height,evidence)
        } finally { if(image!==bitmap) image.recycle() }
    }
}
