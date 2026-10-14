package com.example.rockblockbot

/**
 * A saved, user-adjustable offset for ONE shape + color combination.
 * This is deliberately separate from the global calibration and tall delta.
 * New/unknown shapes start with zero additional offset; previous empirical
 * corrections stay in PlacementGeometry.shapeCorrectionCells().
 */
object PieceCalibration {
    data class Adjustment(val xPx: Int = 0, val yPx: Int = 0)

    fun key(signature: String, color: String): String =
        "v39_${color.uppercase()}_${signature}"

    fun effectiveX(globalX: Int, defaultBiasCells: Int, cellPx: Int, adjustment: Adjustment): Int =
        globalX + defaultBiasCells * cellPx + adjustment.xPx

    fun effectiveY(baseY: Int, defaultBiasCells: Int, cellPx: Int, adjustment: Adjustment): Int =
        baseY + defaultBiasCells * cellPx + adjustment.yPx

    fun changed(adjustment: Adjustment, dx: Int, dy: Int): Adjustment =
        Adjustment((adjustment.xPx + dx).coerceIn(-500, 500),
                   (adjustment.yPx + dy).coerceIn(-500, 500))
}
