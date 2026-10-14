package com.example.rockblockbot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Game-specific recognition calibrated to a 709 x 1536 reference screenshot.
 * The game changes the size of the pieces shown in the tray. A fixed 42px
 * spacing misses cells in smaller rendered pieces (for example ###/#..).
 * This version fits each piece's own grid pitch and checks colored-pixel
 * coverage before accepting a shape.
 */
object BoardRecognizer {
    const val REF_WIDTH = 709f
    const val REF_HEIGHT = 1536f

    data class ObservedPiece(
        val rows: List<String>,
        val gripX: Float, val gripY: Float,
        val gripRow: Int, val gripCol: Int,
        val pitch: Int, val fit: Double
    ) {
        fun asPiece(): Piece = Piece(rows.flatMapIndexed { r, line ->
            line.mapIndexedNotNull { c, ch -> if (ch == '#') Cell(r, c) else null }
        })
        val signature: String get() = rows.joinToString("/")
        val count: Int get() = rows.sumOf { line -> line.count { it == '#' } }
    }

    data class Frame(val board: Long, val pieces: List<ObservedPiece?>,
                     val width: Int, val height: Int) {
        val recognized: Int get() = pieces.count { it != null }
        val signature: String get() = board.toString() + ":" +
            pieces.joinToString("|") { it?.signature ?: "?" }
        fun boardText(): String = (0..7).joinToString("\n") { r ->
            (0..7).joinToString("") { c ->
                if ((board and (1L shl (r * 8 + c))) != 0L) "#" else "."
            }
        }
        fun report(): String = "Captured ${width}x${height} | recognized ${recognized}/3 pieces\n" +
            "Board: ${java.lang.Long.bitCount(board)} occupied cells\n${boardText()}\n\n" +
            pieces.mapIndexed { i, p ->
                "Piece ${i + 1}: " + (p?.let {
                    "${it.signature} (${it.count} cells, pitch ${it.pitch}px, fit ${"%.2f".format(java.util.Locale.US, it.fit)})"
                } ?: "NOT RECOGNIZED")
            }.joinToString("\n")
    }

    private enum class TileColor { RED, GREEN, BLUE, PURPLE, YELLOW }
    private fun matches(pixel: Int, color: TileColor): Boolean {
        val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
        return when (color) {
            TileColor.RED -> r > 140 && r > g * 1.55 && r > b * 1.40 && g < 135
            TileColor.GREEN -> g > 155 && g > r * 1.35 && g > b * 1.18
            TileColor.BLUE -> (b > 212 && g > 165 && b > g + 28 && r < 125) ||
                (b > 155 && r < 65 && b > g * 1.22)
            TileColor.PURPLE -> b > 135 && r > 90 && r > g * 1.60 && b > g * 1.50
            TileColor.YELLOW -> r > 155 && g > 115 && b < 115 && r > b * 1.60
        }
    }
    private fun isBlock(pixel: Int): Boolean = TileColor.entries.any { matches(pixel, it) }
    private fun fraction(image: Bitmap, x: Int, y: Int, radius: Int, color: TileColor? = null): Double {
        var total = 0; var hits = 0
        for (dy in -radius..radius step 3) for (dx in -radius..radius step 3) {
            val px = x + dx; val py = y + dy
            if (px in 0 until image.width && py in 0 until image.height) {
                total++
                if (if (color == null) isBlock(image.getPixel(px, py))
                    else matches(image.getPixel(px, py), color)) hits++
            }
        }
        return if (total == 0) 0.0 else hits.toDouble() / total
    }

    private data class Zone(val left: Int, val right: Int,
                            val top: Int = 1045, val bottom: Int = 1320)
    private val zones = listOf(Zone(115, 260), Zone(275, 440), Zone(440, 635))

    private data class Candidate(val rows: List<String>, val pitch: Int, val score: Double,
                                 val gripX: Float, val gripY: Float,
                                 val gripRow: Int, val gripCol: Int)

    private fun detectPiece(image: Bitmap, zone: Zone): ObservedPiece? {
        val colors = TileColor.entries
        val counts = IntArray(colors.size)
        for (y in zone.top until zone.bottom step 2) for (x in zone.left until zone.right step 2) {
            val pixel = image.getPixel(x, y)
            colors.forEachIndexed { i, color -> if (matches(pixel, color)) counts[i]++ }
        }
        val dominantIndex = counts.indices.maxByOrNull { counts[it] } ?: return null
        if (counts[dominantIndex] < 150) return null
        val color = colors[dominantIndex]

        val zoneW = zone.right - zone.left
        val zoneH = zone.bottom - zone.top
        val mask = BooleanArray(zoneW * zoneH)
        var minX = zoneW; var maxX = -1; var minY = zoneH; var maxY = -1
        var pixelCount = 0
        for (y in 0 until zoneH) for (x in 0 until zoneW) {
            val yes = matches(image.getPixel(zone.left + x, zone.top + y), color)
            mask[y * zoneW + x] = yes
            if (yes) {
                pixelCount++
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
        }
        if (pixelCount < 500 || minX > maxX) return null

        // Summed-area table: O(1) counts of matching pixels per proposed tile.
        val stride = zoneW + 1
        val integral = IntArray(stride * (zoneH + 1))
        for (y in 0 until zoneH) {
            var running = 0
            for (x in 0 until zoneW) {
                if (mask[y * zoneW + x]) running++
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + running
            }
        }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int): Int {
            val a = x0.coerceIn(0, zoneW); val b = x1.coerceIn(0, zoneW)
            val c = y0.coerceIn(0, zoneH); val d = y1.coerceIn(0, zoneH)
            if (a >= b || c >= d) return 0
            return integral[d * stride + b] - integral[c * stride + b] -
                integral[d * stride + a] + integral[c * stride + a]
        }

        var best: Candidate? = null
        // Actual tray tiles have appeared between roughly 33 and 42 reference
        // pixels in screenshots. Search rather than assume a single pitch.
        for (pitch in 31..45) {
            val cols = ceil((maxX - minX + 1).toDouble() / pitch).toInt().coerceIn(1, 5)
            val rows = ceil((maxY - minY + 1).toDouble() / pitch).toInt().coerceIn(1, 6)
            val detected = ArrayList<String>(rows)
            var covered = 0
            var predictedArea = 0
            for (r in 0 until rows) {
                val line = buildString {
                    for (c in 0 until cols) {
                        val x0 = minX + c * pitch; val y0 = minY + r * pitch
                        val cx = zone.left + x0 + pitch / 2
                        val cy = zone.top + y0 + pitch / 2
                        val occupied = fraction(image, cx, cy, 7, color) > 0.30
                        append(if (occupied) '#' else '.')
                        if (occupied) {
                            covered += rect(x0, y0, x0 + pitch, y0 + pitch)
                            predictedArea += pitch * pitch
                        }
                    }
                }
                detected.add(line)
            }
            val nonEmpty = detected.filter { '#' in it }
            if (nonEmpty.isEmpty()) continue
            val left = nonEmpty.minOf { it.indexOf('#') }
            val right = nonEmpty.maxOf { it.lastIndexOf('#') }
            val trimmed = nonEmpty.map { it.substring(left, right + 1) }
            val n = trimmed.sumOf { row -> row.count { it == '#' } }
            if (n !in 1..9) continue
            val recall = covered.toDouble() / pixelCount
            val precision = covered.toDouble() / predictedArea.coerceAtLeast(1)
            val score = 2.0 * recall * precision / (recall + precision).coerceAtLeast(0.0001)
            if (score <= (best?.score ?: 0.0)) continue
            // Grip the bottommost occupied row, choosing the LEFTMOST tile in
            // that row. The touch and target use the SAME tile as the anchor.
            val gripRow = trimmed.indexOfLast { '#' in it }
            val gripCol = trimmed[gripRow].indexOf('#')
            val x = zone.left + minX + (left + gripCol + 0.5f) * pitch
            val y = zone.top + minY + (detected.indexOfFirst { '#' in it } + gripRow + 0.5f) * pitch
            best = Candidate(trimmed, pitch, score, x, y, gripRow, gripCol)
        }
        val chosen = best ?: return null
        // Low-fidelity or overlay-contaminated masks are not safe for gestures.
        if (chosen.score < 0.78) return null
        return ObservedPiece(chosen.rows, chosen.gripX, chosen.gripY,
            chosen.gripRow, chosen.gripCol, chosen.pitch, chosen.score)
    }

    /** Null means this is not a game frame; preserve the last valid report. */
    fun analyze(bitmap: Bitmap): Frame? {
        val image = Bitmap.createScaledBitmap(bitmap, REF_WIDTH.toInt(), REF_HEIGHT.toInt(), true)
        try {
            val p = image.getPixel(350, 300)
            if (!(Color.red(p) < 160 && Color.blue(p) > Color.red(p) + 45 &&
                    Color.blue(p) > Color.green(p) + 14)) return null
            var board = 0L
            for (r in 0..7) for (c in 0..7) {
                if (fraction(image, 163 + c * 55, 454 + r * 55, 14) > 0.55) {
                    board = board or (1L shl (r * 8 + c))
                }
            }
            val pieces = zones.map { detectPiece(image, it) }
            return Frame(board, pieces, bitmap.width, bitmap.height)
        } finally { if (image !== bitmap) image.recycle() }
    }
}
