package com.example.rockblockbot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

/** Manual-input prototype. Automatic screen capture and image recognition are not implemented. */
class MainActivity : Activity() {
 private val cells = Array(64) { false }
 private val buttons = ArrayList<ToggleButton>()
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,16,16,16) }
  root.addView(TextView(this).apply { text = "Rock Block Solver — manual prototype"; textSize = 20f })
  root.addView(TextView(this).apply { text = "Tap occupied cells. Enter each piece as a grid using # for filled and . for empty, with / between rows. Example: ##/.#/.#" })
  val grid = GridLayout(this).apply { columnCount = 8; rowCount = 8 }
  for (i in 0..63) {
   val b = ToggleButton(this).apply {
    textOn = "■"; textOff = "·"; text = "·"; minWidth = 0; minHeight = 0
    setPadding(0,0,0,0); layoutParams = GridLayout.LayoutParams().apply { width = (resources.displayMetrics.widthPixels - 44)/8; height = (resources.displayMetrics.widthPixels - 44)/8 }
    setOnCheckedChangeListener { _, checked -> cells[i] = checked }
   }
   buttons.add(b); grid.addView(b)
  }
  root.addView(grid)
  val inputs = listOf("###", "##/.#", "####").mapIndexed { i, sample ->
   EditText(this).apply { setSingleLine(true); setText(sample); hint = "Piece ${i+1}"; root.addView(this) }
  }
  val output = TextView(this).apply { textSize = 17f; setPadding(0,16,0,0) }
  root.addView(Button(this).apply { text = "Find survival-first moves"; setOnClickListener {
   try {
    val pieces = inputs.map { parsePiece(it.text.toString()) }
    var board = 0L
    for (i in cells.indices) if (cells[i]) board = board or (1L shl i)
    val plan = Solver.solve(board, pieces)
    output.text = if (plan.moves.isEmpty()) "No legal placements." else
      "${if (plan.complete) "All 3 fit" else "Partial plan only"}\n" + plan.moves.mapIndexed { idx,m ->
       "${idx+1}. Piece ${m.pieceIndex+1} → row ${m.row+1}, column ${m.col+1} (top-left occupied square)"
      }.joinToString("\n")
   } catch (e: Exception) { output.text = "Invalid input: ${e.message}" }
  } })
  root.addView(output)
  root.addView(Button(this).apply { text = "Accessibility settings (future automation)"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
  val scroll = ScrollView(this); scroll.addView(root); setContentView(scroll)
 }
 private fun parsePiece(s: String): Piece {
  val rows = s.trim().split("/")
  require(rows.size in 1..8 && rows.all { it.length in 1..8 }) { "Use # and . rows separated by /" }
  val cells = rows.flatMapIndexed { r,row -> row.mapIndexedNotNull { c,ch -> when(ch) { '#' -> Cell(r,c); '.' -> null; else -> throw IllegalArgumentException("Only # and . allowed") } } }
  return Piece(cells)
 }
}
