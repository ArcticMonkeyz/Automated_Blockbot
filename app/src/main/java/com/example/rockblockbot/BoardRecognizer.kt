package com.example.rockblockbot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

/** Coordinates calibrated against 709x1536 game captures. Experimental: never assume perfect recognition. */
object BoardRecognizer {
    const val REF_WIDTH = 709f
    const val REF_HEIGHT = 1536f
    private const val PITCH = 42

    data class ObservedPiece(val rows: List<String>, val centerX: Float, val centerY: Float) {
        fun asPiece(): Piece = Piece(rows.flatMapIndexed { r, row ->
            row.mapIndexedNotNull { c, ch -> if (ch == '#') Cell(r, c) else null }
        })
        val signature get() = rows.joinToString("/")
    }
    data class Frame(val board: Long, val pieces: List<ObservedPiece?>,
                     val width: Int, val height: Int) {
        val recognized get() = pieces.count { it != null }
        val signature get() = board.toString() + ":" + pieces.joinToString("|") { it?.signature ?: "?" }
        fun boardText(): String = (0..7).joinToString("\n") { r ->
            (0..7).joinToString("") { c -> if ((board and (1L shl (r*8+c))) != 0L) "#" else "." }
        }
        fun report(): String = "Captured ${width}x${height} | recognition: ${recognized}/3 pieces\n" +
            "Board: ${java.lang.Long.bitCount(board)} occupied cells\n${boardText()}\n\n" +
            pieces.mapIndexed { i, p -> "Piece ${i+1}: ${p?.signature ?: "NOT RECOGNIZED"}" }.joinToString("\n")
    }
    private enum class TileColor { RED, GREEN, BLUE, PURPLE, YELLOW }
    private fun matches(pixel: Int, color: TileColor): Boolean {
        val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
        return when (color) {
            TileColor.RED -> r > 140 && r > g*1.55 && r > b*1.40 && g < 135
            TileColor.GREEN -> g > 155 && g > r*1.35 && g > b*1.18
            TileColor.BLUE -> b > 145 && b > g*1.16 && (r < 58 || (g > 185 && b > 225))
            TileColor.PURPLE -> b > 135 && r > 90 && r > g*1.60 && b > g*1.50
            TileColor.YELLOW -> r > 155 && g > 115 && b < 115 && r > b*1.60
        }
    }
    private fun isBlock(p: Int) = TileColor.entries.any { matches(p, it) }
    private fun frac(img: Bitmap, cx: Int, cy: Int, radius: Int, color: TileColor? = null): Double {
        var total = 0; var found = 0
        for (dy in -radius..radius step 3) for (dx in -radius..radius step 3) {
            val x = cx + dx; val y = cy + dy
            if (x in 0 until img.width && y in 0 until img.height) {
                total++
                if (if (color == null) isBlock(img.getPixel(x,y)) else matches(img.getPixel(x,y),color)) found++
            }
        }
        return if (total == 0) 0.0 else found.toDouble()/total
    }
    private data class Zone(val left: Int, val right: Int, val top: Int = 1045, val bottom: Int = 1320)
    private val zones = listOf(Zone(115,260), Zone(275,440), Zone(440,635))
    private fun detectPiece(img: Bitmap, zone: Zone): ObservedPiece? {
        val counts = IntArray(TileColor.entries.size)
        for (y in zone.top until zone.bottom step 2) for (x in zone.left until zone.right step 2) {
            val p = img.getPixel(x,y)
            TileColor.entries.forEachIndexed { i, color -> if (matches(p,color)) counts[i]++ }
        }
        val dominant = counts.indices.maxByOrNull { counts[it] } ?: return null
        if (counts[dominant] < 150) return null
        val color = TileColor.entries[dominant]
        var minX = zone.right; var maxX = zone.left; var minY = zone.bottom; var maxY = zone.top
        for (y in zone.top until zone.bottom) for (x in zone.left until zone.right) {
            if (!matches(img.getPixel(x,y),color)) continue
            minX = minOf(minX,x); maxX = maxOf(maxX,x)
            minY = minOf(minY,y); maxY = maxOf(maxY,y)
        }
        if (minX > maxX || minY > maxY) return null
        val columns = ((maxX-minX)/PITCH+1).coerceIn(1,5)
        val rows = ((maxY-minY)/PITCH+1).coerceIn(1,6)
        val raw = (0 until rows).map { r ->
            buildString {
                for (c in 0 until columns) {
                    append(if (frac(img,minX+19+c*PITCH,minY+19+r*PITCH,8,color) > .30) '#' else '.')
                }
            }
        }
        val nonEmpty = raw.filter { '#' in it }
        if (nonEmpty.isEmpty()) return null
        val first = nonEmpty.minOf { it.indexOf('#') }; val last = nonEmpty.maxOf { it.lastIndexOf('#') }
        val shape = nonEmpty.map { it.substring(first,last+1) }
        if (shape.sumOf { it.count { ch -> ch == '#' } } !in 1..9) return null
        return ObservedPiece(shape,(minX+maxX)/2f,(minY+maxY)/2f)
    }
    /** Returns null for other apps; preserves last known game frame. */
    fun analyze(bitmap: Bitmap): Frame? {
        val img = Bitmap.createScaledBitmap(bitmap,REF_WIDTH.toInt(),REF_HEIGHT.toInt(),true)
        try {
            val p = img.getPixel(350,300)
            if (!(Color.red(p)<160 && Color.blue(p)>Color.red(p)+45 && Color.blue(p)>Color.green(p)+14)) return null
            var board = 0L
            for (r in 0..7) for (c in 0..7) {
                if (frac(img,163+c*55,454+r*55,14)>.55) board = board or (1L shl (r*8+c))
            }
            val pieces = zones.map { detectPiece(img,it) }
            return Frame(board,pieces,bitmap.width,bitmap.height)
        } finally { if (img !== bitmap) img.recycle() }
    }
}
