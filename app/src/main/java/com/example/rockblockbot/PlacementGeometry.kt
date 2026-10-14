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

    /** Calibrated against a confirmed, post-release screenshot in v3.0.
     * Straight horizontal #### worked at global X=84,Y=484, but ###/#..
     * landed one cell right and one cell down at those same settings.
     * Correct ONLY the observed footprint, not every irregular piece.
     * Keep corrections in board-cell units across display resolutions.
     */
    fun shapeCorrectionCells(signature: String): Pair<Int, Int> = when (signature) {
        "###/#.." -> -1 to -1
        else -> 0 to 0
    }
}
