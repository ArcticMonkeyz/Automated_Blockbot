package com.example.rockblockbot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/** Initial screen-coordinate calibration from a 709x1536 reference screenshot.
 *  The image is scaled to the actual captured size. We deliberately report
 *  low confidence rather than touching the game when recognition is uncertain.
 */
object BoardRecognizer {
    private fun isBlock(pixel: Int): Boolean {
        val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
        val max = maxOf(r,g,b); val min = minOf(r,g,b)
        return (max-min > 55 && max > 105 &&
            ((g > r * 1.28 && g > b * 1.13) ||
             (b > r * 1.25 && b > g * 1.06) ||
             (r > b * 1.38 && g > b * 1.22 && r > 125)))
    }
    private fun frac(bitmap: Bitmap, cx: Double, cy: Double, radius: Int): Double {
        val x = (cx * bitmap.width).roundToInt()
        val y = (cy * bitmap.height).roundToInt()
        var found = 0; var count = 0
        for (dy in -radius..radius step 3) for (dx in -radius..radius step 3) {
            val px = x + dx; val py = y + dy
            if (px in 0 until bitmap.width && py in 0 until bitmap.height) {
                count++
                if (isBlock(bitmap.getPixel(px,py))) found++
            }
        }
        return if (count == 0) 0.0 else found.toDouble()/count
    }
    fun analyze(bitmap: Bitmap): String {
        // Coordinates are center-of-cell, calibrated from user-provided 709px-wide screenshots.
        val sx = bitmap.width.toDouble()/709.0
        val sy = bitmap.height.toDouble()/1536.0
        val radius = maxOf(3, (14*sx).roundToInt())
        val board = Array(8) { BooleanArray(8) }
        val conf = Array(8) { DoubleArray(8) }
        for (r in 0..7) for (c in 0..7) {
            val x = (163.0 + c*55.0)/709.0
            val y = (454.0 + r*55.0)/1536.0
            conf[r][c] = frac(bitmap,x,y,radius)
            board[r][c] = conf[r][c] > .55
        }
        val occupied = board.sumOf { row -> row.count { it } }
        val lines = board.joinToString("\n") { row -> row.joinToString("") { if (it) "#" else "." } }
        // Piece area changes when a piece is lifted; report diagnostic color pixel fractions.
        val zones = listOf(178.0, 353.0, 530.0).map { x ->
            (0..3).map { dx -> frac(bitmap,(x+(dx-1.5)*32)/709.0,1160.0/1536.0,maxOf(2,radius/2)) }
        }
        val mean = zones.map { it.average() }
        val confidence = if (bitmap.width.toDouble()/bitmap.height in 0.43..0.49 && occupied <= 48) "PRELIMINARY" else "LOW"
        return "Screen ${bitmap.width}×${bitmap.height} | confidence: $confidence\n" +
            "Detected occupied cells: $occupied\n$lines\n" +
            "Piece-zone color signals: ${mean.joinToString { "%.2f".format(it) }}\n" +
            "These detections are experimental. Do not enable automatic moves until calibrated against a live game board."
    }
}
