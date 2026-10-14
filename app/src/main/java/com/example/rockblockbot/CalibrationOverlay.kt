package com.example.rockblockbot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Visual-only, touch-through markers for one planned placement. */
class CalibrationOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var coords = floatArrayOf(0f, 0f, 0f, 0f)
    private var row = 0
    private var col = 0
    private var cells: List<Cell> = emptyList()
    private var detectedCenters: List<Pair<Float, Float>> = emptyList()
    private var sx = 1f
    private var sy = 1f

    fun setPlan(points: FloatArray, r: Int, c: Int, pieceCells: List<Cell>, scaleX: Float, scaleY: Float,
                centers: List<Pair<Float, Float>> = emptyList()) {
        coords = points.copyOf()
        row = r
        col = c
        cells = pieceCells.toList()
        detectedCenters = centers.toList()
        sx = scaleX
        sy = scaleY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Green cells are the desired board placement, NOT the finger destination.
        paint.style = Paint.Style.FILL
        paint.color = 0x7733EE66
        for (cell in cells) {
            val x = (163f + (col + cell.c)*55f)*sx
            val y = (454f + (row + cell.r)*55f)*sy
            canvas.drawRect(x-22f*sx,y-22f*sy,x+22f*sx,y+22f*sy,paint)
        }
        // Numbered circles mark INDIVIDUAL source tiles in the tray. Unlike
        // the green target squares, these are measured from image recognition.
        // A fallback / uncertain detection gets no numbered tile centers.
        for ((index, tile) in detectedCenters.withIndex()) {
            val px = tile.first * sx
            val py = tile.second * sy
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = 0xFF00DFFF.toInt()
            canvas.drawCircle(px, py, 13f * minOf(sx, sy), paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 15f * minOf(sx, sy)
            paint.isFakeBoldText = true
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawText((index + 1).toString(), px, py + 5f*sy, paint)
        }
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.LEFT
        // Blue ring: start position. Red crosshair: finger release position.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = 0xFF37B9FF.toInt()
        canvas.drawCircle(coords[0],coords[1],18f,paint)
        paint.color = 0xFFFF4444.toInt()
        canvas.drawCircle(coords[2],coords[3],22f,paint)
        canvas.drawLine(coords[2]-32f,coords[3],coords[2]+32f,coords[3],paint)
        canvas.drawLine(coords[2],coords[3]-32f,coords[2],coords[3]+32f,paint)
    }
}
