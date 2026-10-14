package com.example.rockblockbot

import android.graphics.Bitmap
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Diagnostic-only recognition. The reference is the 709x1536 Rock Block Saga
 * screenshot provided for calibration. Captured frames are resized to that
 * coordinate space; no gestures are performed by this version.
 */
object BoardRecognizer {
    private const val REF_WIDTH = 709
    private const val REF_HEIGHT = 1536
    private const val PIECE_PITCH = 42

    private enum class BlockColor { RED, GREEN, BLUE, PURPLE, YELLOW }

    private fun matches(pixel: Int, color: BlockColor): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return when (color) {
            BlockColor.RED -> r > 140 && r > g * 1.55 && r > b * 1.40 && g < 135
            BlockColor.GREEN -> g > 155 && g > r * 1.35 && g > b * 1.18
            // Require either dark blue or a bright cyan tile; reject teal water.
            BlockColor.BLUE -> b > 145 && b > g * 1.16 &&
                (r < 58 || (g > 185 && b > 225))
            BlockColor.PURPLE -> b > 135 && r > 90 && r > g * 1.60 && b > g * 1.50
            BlockColor.YELLOW -> r > 155 && g > 115 && b < 115 && r > b * 1.60
        }
    }

    private fun isBlock(pixel: Int): Boolean = BlockColor.entries.any { matches(pixel, it) }

    private fun isGameFrame(image: Bitmap): Boolean {
        // The game has a blue water background above the board. Reject this
        // app's white/gray screen and unrelated apps to preserve game results.
        val pixel = image.getPixel(350, 300)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r < 160 && b > r + 45 && b > g + 14
    }

    private fun fraction(image: Bitmap, cx: Int, cy: Int, radius: Int,
                         color: BlockColor? = null): Double {
        var count = 0
        var hits = 0
        for (dy in -radius..radius step 3) {
            for (dx in -radius..radius step 3) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until image.width || y !in 0 until image.height) continue
                count++
                val pixel = image.getPixel(x, y)
                if (if (color == null) isBlock(pixel) else matches(pixel, color)) hits++
            }
        }
        return if (count == 0) 0.0 else hits.toDouble() / count
    }

    private data class Zone(val left: Int, val right: Int, val top: Int = 1045,
                            val bottom: Int = 1320)
    private val zones = listOf(Zone(115, 260), Zone(275, 440), Zone(440, 635))

    private fun detectPiece(image: Bitmap, zone: Zone): List<String>? {
        val counts = IntArray(BlockColor.entries.size)
        // Find the most common saturated tile color within this piece slot.
        // Using the dominant color rejects most colored background noise.
        for (y in zone.top until zone.bottom step 2) {
            for (x in zone.left until zone.right step 2) {
                val pixel = image.getPixel(x, y)
                BlockColor.entries.forEachIndexed { index, color ->
                    if (matches(pixel, color)) counts[index]++
                }
            }
        }
        val dominantIndex = counts.indices.maxByOrNull { counts[it] } ?: return null
        if (counts[dominantIndex] < 150) return null
        val color = BlockColor.entries[dominantIndex]

        var minX = zone.right
        var maxX = zone.left
        var minY = zone.bottom
        var maxY = zone.top
        for (y in zone.top until zone.bottom) {
            for (x in zone.left until zone.right) {
                if (!matches(image.getPixel(x, y), color)) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (minX > maxX || minY > maxY) return null

        // Piece tiles are approximately 42px apart in the reference image.
        // Quantize samples relative to the top-left colored pixel.
        val columns = ((maxX - minX) / PIECE_PITCH + 1).coerceIn(1, 5)
        val rows = ((maxY - minY) / PIECE_PITCH + 1).coerceIn(1, 6)
        val shape = (0 until rows).map { row ->
            buildString {
                for (col in 0 until columns) {
                    val x = minX + 19 + col * PIECE_PITCH
                    val y = minY + 19 + row * PIECE_PITCH
                    append(if (fraction(image, x, y, 8, color) > 0.30) '#' else '.')
                }
            }
        }
        if (shape.sumOf { row -> row.count { it == '#' } } !in 1..9) return null
        // Ignore empty padding produced by shadow pixels near the boundaries.
        val nonEmpty = shape.filter { '#' in it }
        if (nonEmpty.isEmpty()) return null
        val first = nonEmpty.minOf { it.indexOf('#') }
        val last = nonEmpty.maxOf { it.lastIndexOf('#') }
        return nonEmpty.map { it.substring(first, last + 1) }
    }

    /** Null means this isn't a recognizable game frame: keep the last report. */
    fun analyze(bitmap: Bitmap): String? {
        val image = Bitmap.createScaledBitmap(bitmap, REF_WIDTH, REF_HEIGHT, true)
        try {
            if (!isGameFrame(image)) return null
            val board = Array(8) { BooleanArray(8) }
            for (row in 0..7) for (col in 0..7) {
                board[row][col] = fraction(image, 163 + col * 55, 454 + row * 55, 14) > 0.55
            }
            val occupied = board.sumOf { row -> row.count { it } }
            val boardText = board.joinToString("\n") { row ->
                row.joinToString("") { if (it) "#" else "." }
            }
            val pieces = zones.map { detectPiece(image, it) }
            val recognized = pieces.count { it != null }
            val status = if (recognized == 3 && occupied <= 48) "PRELIMINARY" else "PARTIAL"
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val piecesText = pieces.mapIndexed { index, piece ->
                "Piece ${index + 1}: " + (piece?.joinToString("/") ?: "NOT RECOGNIZED")
            }.joinToString("\n")
            return "Last game frame: $timestamp\n" +
                "Captured ${bitmap.width}x${bitmap.height} | confidence: $status\n" +
                "Board: $occupied occupied cells\n$boardText\n\n" +
                "Pieces recognized: $recognized/3\n$piecesText\n\n" +
                "Diagnostic only. Verify every shape before automatic moves."
        } finally {
            if (image !== bitmap) image.recycle()
        }
    }
}
