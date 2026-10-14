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

    /**
     * The v3.7 direct-tall release forced effective Y to zero, ignoring the
     * user-adjustable Y offset. On-device, that lifted a three-row yellow L
     * above the board; the former +484px offset could leave a five-high piece
     * too low. v3.8 starts halfway between those failed extremes, but treats
     * that as a *trial* calibration, not a universal height formula.
     *
     * Both GLOBAL Y and TALL DELTA always contribute to the release Y.
     * Users can tune tall pieces independently without shifting horizontal
     * pieces. Any footprint-specific row correction is applied to both.
     */
    const val DEFAULT_TALL_DELTA_PX = -240

    fun isTall(pieceHeight: Int, pieceWidth: Int): Boolean =
        pieceHeight >= 3 && pieceHeight > pieceWidth

    fun effectiveYOffsetPx(pieceHeight: Int, pieceWidth: Int,
                           globalY: Int, tallDeltaPx: Int,
                           shapeBiasRows: Int, cellHeightPx: Int): Int =
        globalY + (if (isTall(pieceHeight, pieceWidth)) tallDeltaPx else 0) +
            shapeBiasRows * cellHeightPx

    // Kept as an API alias for existing callers/tests; now denotes a tall
    // calibration category, NOT a special direct-grip or Y=0 path.
    fun directTallAnchor(pieceHeight: Int, pieceWidth: Int): Boolean =
        isTall(pieceHeight, pieceWidth)

    /** Empirical, footprint-specific compensation for game-side snap behavior.
     * These affect only the release endpoint, never the solver's target or the
     * observed pickup position. Store in board-cell units for resolution scaling.
     *
     * v3.0: horizontal #### landed as preview with global X=84/Y=484.
     * v3.1: ###/#.. landed one cell right/down, hence (-1,-1).
     * v3.4: a PURPLE horizontal ##### landed one column right, hence (-1,0).
     * v3.6: a YELLOW five-strip placed correctly before that bias was applied,
     * so limit the five-wide shift to the observed PURPLE color only.
     */
    fun shapeCorrectionCells(signature: String, tileColor: String = ""): Pair<Int, Int> = when {
        signature == "###/#.." -> -1 to -1
        // Observed rightward misplacement was on a PURPLE five-strip. The
        // newly observed yellow five-strip (previously cropped to four) landed
        // at the correct origin WITHOUT that shift. Avoid extrapolating.
        signature == "#####" && tileColor == "PURPLE" -> -1 to 0
        else -> 0 to 0
    }
}
