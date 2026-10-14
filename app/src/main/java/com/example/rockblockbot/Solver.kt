package com.example.rockblockbot

/** Bit 0 is row 0 column 0. Coordinates of pieces are normalized to their top-left occupied cell. */
data class Cell(val r: Int, val c: Int)
data class Piece(val cells: List<Cell>) {
 init { require(cells.isNotEmpty()); require(cells.distinct().size == cells.size) }
 val normalized: List<Cell> = cells.map { Cell(it.r - cells.minOf { x -> x.r }, it.c - cells.minOf { x -> x.c }) }
 val height get() = normalized.maxOf { it.r } + 1
 val width get() = normalized.maxOf { it.c } + 1
}
data class Move(val pieceIndex: Int, val row: Int, val col: Int)
data class Plan(val moves: List<Move>, val score: Double, val complete: Boolean)

object Solver {
 private fun occupied(board: Long, r: Int, c: Int) = (board and (1L shl (r * 8 + c))) != 0L
 private fun placements(board: Long, p: Piece): List<Pair<Move, Long>> {
  val result = ArrayList<Pair<Move, Long>>()
  for (r in 0..(8 - p.height)) for (c in 0..(8 - p.width)) {
   var mask = 0L
   for (cell in p.normalized) mask = mask or (1L shl ((r + cell.r) * 8 + c + cell.c))
   if ((board and mask) == 0L) result.add(Move(-1, r, c) to clear(board or mask))
  }
  return result
 }
 private fun clear(board: Long): Long {
  var rows = 0L; var cols = 0L
  for (r in 0..7) {
   val mask = 255L shl (r * 8)
   if (board and mask == mask) rows = rows or mask
  }
  for (c in 0..7) {
   var mask = 0L
   for (r in 0..7) mask = mask or (1L shl (r * 8 + c))
   if (board and mask == mask) cols = cols or mask
  }
  return board and (rows or cols).inv()
 }
 private fun evaluate(board: Long): Double {
  val filled = java.lang.Long.bitCount(board)
  var adjacent = 0; var isolated = 0; var almost = 0
  for (r in 0..7) for (c in 0..7) if (!occupied(board,r,c)) {
   var neighbors = 0
   if (r > 0 && occupied(board,r-1,c)) neighbors++
   if (r < 7 && occupied(board,r+1,c)) neighbors++
   if (c > 0 && occupied(board,r,c-1)) neighbors++
   if (c < 7 && occupied(board,r,c+1)) neighbors++
   adjacent += neighbors
   if (neighbors == 4) isolated++
  }
  for (r in 0..7) if ((0..7).count { occupied(board,r,it) } >= 6) almost++
  for (c in 0..7) if ((0..7).count { occupied(board,it,c) } >= 6) almost++
  // Survival-first: low occupancy, fewer trapped cells, and a mild reward for near-complete lines.
  return -filled * 4.0 - isolated * 15.0 - adjacent * 0.25 + almost * 1.2
 }
 /** Deterministic board prediction for post-gesture verification. */
 fun placeAndClear(board: Long, piece: Piece, row: Int, col: Int): Long {
  require(row >= 0 && col >= 0 && row + piece.height <= 8 && col + piece.width <= 8)
  var mask = 0L
  for (cell in piece.normalized) mask = mask or (1L shl ((row+cell.r)*8+col+cell.c))
  require((board and mask) == 0L) { "Placement overlaps an occupied cell" }
  return clear(board or mask)
 }
 /** Searches all legal orders and placements of three fixed-orientation pieces. */
 fun solve(board: Long, pieces: List<Piece>): Plan {
  require(pieces.size == 3)
  var best = Plan(emptyList(), Double.NEGATIVE_INFINITY, false)
  fun visit(b: Long, used: Int, moves: List<Move>, cleared: Int) {
   if (used == 7) {
    val score = evaluate(b) + cleared * 3.0
    if (!best.complete || score > best.score) best = Plan(moves, score, true)
    return
   }
   if (moves.size > best.moves.size && !best.complete) best = Plan(moves, evaluate(b), false)
   for (i in 0..2) if (used and (1 shl i) == 0) {
    for ((move, next) in placements(b, pieces[i])) {
     val before = java.lang.Long.bitCount(b) + pieces[i].normalized.size
     val removed = before - java.lang.Long.bitCount(next)
     visit(next, used or (1 shl i), moves + move.copy(pieceIndex=i), cleared + removed)
    }
   }
  }
  visit(board,0,emptyList(),0)
  return best
 }
}
