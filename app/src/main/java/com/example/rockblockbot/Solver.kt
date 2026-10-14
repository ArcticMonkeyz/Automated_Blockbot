package com.example.rockblockbot

import kotlin.math.ln

/** Bit 0 is row 1, column 1. Rows and columns clear immediately after each placement. */
data class Cell(val r: Int, val c: Int)
data class Piece(val cells: List<Cell>) {
    init { require(cells.isNotEmpty()); require(cells.distinct().size == cells.size) }
    val normalized: List<Cell> = cells.map {
        Cell(it.r - cells.minOf { x -> x.r }, it.c - cells.minOf { x -> x.c })
    }
    val height: Int get() = normalized.maxOf { it.r } + 1
    val width: Int get() = normalized.maxOf { it.c } + 1
}
data class Move(val pieceIndex: Int, val row: Int, val col: Int)
data class Plan(val moves: List<Move>, val score: Double, val complete: Boolean)

object Solver {
    private const val BEAM_WIDTH = 140
    private data class State(val board: Long, val used: Int, val moves: List<Move>,
                             val clearedLines: Int, val streak: Int, val bonus: Double)
    private data class Outcome(val board: Long, val lines: Int)

    private fun filled(board: Long, r: Int, c: Int) = board and (1L shl (r*8+c)) != 0L

    private fun placement(board: Long, p: Piece, r: Int, c: Int): Outcome? {
        var mask = 0L
        for (cell in p.normalized) mask = mask or (1L shl ((r+cell.r)*8+c+cell.c))
        if (board and mask != 0L) return null
        val placed = board or mask
        var removed = 0L
        var lines = 0
        for (row in 0..7) {
            val rowMask = 255L shl (row*8)
            if (placed and rowMask == rowMask) { removed = removed or rowMask; lines++ }
        }
        for (col in 0..7) {
            var colMask = 0L
            for (row in 0..7) colMask = colMask or (1L shl (row*8+col))
            if (placed and colMask == colMask) { removed = removed or colMask; lines++ }
        }
        return Outcome(placed and removed.inv(), lines)
    }

    fun placeAndClear(board: Long, piece: Piece, row: Int, col: Int): Long {
        require(row>=0 && col>=0 && row+piece.height<=8 && col+piece.width<=8)
        return requireNotNull(placement(board,piece,row,col)) { "Placement overlaps an occupied cell" }.board
    }

    private fun boardValue(board: Long): Double {
        val occupied = java.lang.Long.bitCount(board)
        var enclosed = 0
        var narrow = 0
        var openSquares = 0
        var nearLines = 0
        for (r in 0..7) for (c in 0..7) if (!filled(board,r,c)) {
            var freeNeighbors = 0
            if (r>0 && !filled(board,r-1,c)) freeNeighbors++
            if (r<7 && !filled(board,r+1,c)) freeNeighbors++
            if (c>0 && !filled(board,r,c-1)) freeNeighbors++
            if (c<7 && !filled(board,r,c+1)) freeNeighbors++
            if (freeNeighbors==0) enclosed++
            else if (freeNeighbors==1) narrow++
            if (r<7 && c<7 && !filled(board,r+1,c) &&
                !filled(board,r,c+1) && !filled(board,r+1,c+1)) openSquares++
        }
        for (r in 0..7) {
            val count = (0..7).count { filled(board,r,it) }
            if (count in 5..7) nearLines += count-4
        }
        for (c in 0..7) {
            val count = (0..7).count { filled(board,it,c) }
            if (count in 5..7) nearLines += count-4
        }
        return -occupied*3.8 - enclosed*18.0 - narrow*3.0 +
            openSquares*0.55 + nearLines*0.65
    }

    private fun finalValue(state: State): Double {
        val (families, options) = ShapeLibrary.mobility(state.board)
        // Shape-library flexibility: preserve possible placements for unseen pieces.
        // Treat every footprint equally until real probabilities can be measured.
        return boardValue(state.board) + state.bonus +
            families*1.15 + ln(1.0+options)*5.5
    }

    /**
     * Re-plan for 1, 2 or 3 remaining visible pieces. Their indices are original
     * screen slots (0..2), even after earlier pieces disappear.
     * Beam search is bounded to avoid freezing the phone on dense boards.
     */
    fun solveVisible(board: Long, available: List<Pair<Int, Piece>>): Plan {
        require(available.size in 1..3)
        require(available.map { it.first }.distinct().size == available.size)
        var beam = listOf(State(board,0,emptyList(),0,0,0.0))
        for (depth in available.indices) {
            val candidates = ArrayList<State>()
            for (state in beam) for (i in available.indices) {
                if (state.used and (1 shl i) != 0) continue
                val (slot,piece) = available[i]
                for (r in 0..(8-piece.height)) for (c in 0..(8-piece.width)) {
                    val outcome = placement(state.board,piece,r,c) ?: continue
                    val streak = if (outcome.lines>0) state.streak+1 else 0
                    val gain = outcome.lines*13.0 +
                        (if (outcome.lines>1) (outcome.lines-1)*8.0 else 0.0) +
                        (if (outcome.lines>0) state.streak*5.0 else 0.0)
                    candidates.add(State(outcome.board,state.used or (1 shl i),
                        state.moves+Move(slot,r,c),state.clearedLines+outcome.lines,
                        streak,state.bonus+gain))
                }
            }
            if (candidates.isEmpty()) return Plan(emptyList(),Double.NEGATIVE_INFINITY,false)
            // Distinct board+used states reduce redundant permutations.
            beam = candidates.sortedByDescending { boardValue(it.board)+it.bonus }
                .distinctBy { it.board to it.used }
                .take(BEAM_WIDTH)
        }
        val best = beam.maxByOrNull { finalValue(it) }!!
        return Plan(best.moves,finalValue(best),true)
    }

    /** Compatibility for callers supplying all three slots. */
    fun solve(board: Long, pieces: List<Piece>): Plan =
        solveVisible(board,pieces.mapIndexed { i,p -> i to p })
}
