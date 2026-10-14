package com.example.rockblockbot

import kotlin.math.roundToInt

/** Pure pixel calibration: a game-board tile in full-display coordinates. */
object PlacementGeometry {
    private const val gridPitchRef = 55f
    private const val widthRef = 709f
    private const val heightRef = 1536f

    fun cellWidthPx(displayWidth: Int): Int =
        (gridPitchRef * displayWidth / widthRef).roundToInt()

    fun cellHeightPx(displayHeight: Int): Int =
        (gridPitchRef * displayHeight / heightRef).roundToInt()

    /** Empirical, footprint-specific compensation for game-side snap behavior.
     * These affect only the release endpoint, never the solver's target or the
     * observed pickup position. Store in board-cell units for resolution scaling.
     *
     * v3.0: horizontal #### landed as preview with global X=84/Y=484.
     * v3.1: ###/#.. landed one cell right/down, hence (-1,-1).
     * v3.4: horizontal ##### preview was correct but landed one column right,
     * hence (-1,0). Do NOT apply that shift to #### or unrelated shapes.
     */
    fun shapeCorrectionCells(signature: String): Pair<Int, Int> = when (signature) {
        "###/#.." -> -1 to -1
        "#####" -> -1 to 0
        else -> 0 to 0
    }
}
