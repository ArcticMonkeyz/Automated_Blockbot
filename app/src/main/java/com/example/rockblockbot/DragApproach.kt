package com.example.rockblockbot

/** A FIRST-APPROACH coordinate, never a release authorization.
 *
 * The historic calibrated endpoint can be BELOW the game board. In that case
 * a short drag never leaves the tray and the game's placement shadow cannot
 * appear. Move the finger INTO the playable 8x8 rectangle before attempting
 * any closed-loop correction. The shadow still decides whether to release.
 */
object DragApproach {
    data class Point(val x: Float, val y: Float, val clamped: Boolean)
    fun boardEntry(rawX: Float, rawY: Float, displayWidth: Int, displayHeight: Int): Point {
        val sx = displayWidth / 709f
        val sy = displayHeight / 1536f
        val minX = (163f + 0.35f * 55f) * sx
        val maxX = (163f + 6.65f * 55f) * sx
        val minY = (454f + 0.40f * 55f) * sy
        val maxY = (454f + 6.60f * 55f) * sy
        val x = rawX.coerceIn(minX, maxX)
        val y = rawY.coerceIn(minY, maxY)
        return Point(x, y, x != rawX || y != rawY)
    }
}
