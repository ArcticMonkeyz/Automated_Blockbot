package com.example.rockblockbot

import kotlin.math.hypot

/** Distance-proportional timing for an uninterrupted Android Path gesture.
 * GestureDescription spreads the stroke duration across path length.
 * Only scan after arriving at the board and before leaving the board, NOT
 * during transit or on return where the shadow disappears or moves rapidly.
 * These are predicted windows; on-device timing needs validation.
 */
object ProbeTiming {
    const val SWEEP_COUNT = 120
    const val SWEEP_PX = 6f
    const val DURATION_MS = 4200L
    data class Window(val fromMs: Long, val toMs: Long) {
        fun includes(elapsedMs: Long) = elapsedMs in fromMs..toMs
    }
    fun observationWindow(fromX: Float, fromY: Float, boardX: Float, boardY: Float): Window {
        val travel = hypot((boardX-fromX).toDouble(),(boardY-fromY).toDouble())
        val sweep = 2.0 * SWEEP_COUNT * SWEEP_PX
        val total = 2.0 * travel + sweep
        if (total == 0.0) return Window(0L, 0L)
        val arrive = (DURATION_MS * travel / total).toLong()
        val leave = (DURATION_MS * (travel+sweep) / total).toLong()
        // Give Unity time to render the placement preview after the drag
        // approaches the grid, and exclude the exit transition.
        return Window((arrive + 180L).coerceAtMost(DURATION_MS),
            (leave - 170L).coerceAtLeast(0L))
    }
}
