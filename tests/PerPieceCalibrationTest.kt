import com.example.rockblockbot.*

private fun boardOf(vararg cells: Pair<Int,Int>): Long = cells.fold(0L) { board,(row,col)->
    board or (1L shl (row*8+col))
}

fun main() {
    val pitchX=PlacementGeometry.cellWidthPx(1080)
    val pitchY=PlacementGeometry.cellHeightPx(2400)
    require(pitchX==84 && pitchY==86)
    val yellow=PieceCalibration.Adjustment()
    val purple=PieceCalibration.Adjustment()
    require(PieceCalibration.key("#/#/#/#/#","PURPLE") !=
            PieceCalibration.key("##/#./#.","YELLOW"))
    require(PlacementGeometry.shapeCorrectionCells("##/#./#.","YELLOW") == (0 to 0))
    require(PlacementGeometry.shapeCorrectionCells("#/#/#/#/#","PURPLE") == (-1 to 1))
    val yellowBias=PlacementGeometry.shapeCorrectionCells("##/#./#.","YELLOW")
    val purpleBias=PlacementGeometry.shapeCorrectionCells("#/#/#/#/#","PURPLE")
    val baseTall=PlacementGeometry.effectiveYOffsetPx(3,2,484,-240,0,pitchY)
    require(baseTall==244)
    require(PieceCalibration.effectiveX(84,yellowBias.first,pitchX,yellow)==84)
    require(PieceCalibration.effectiveY(baseTall,yellowBias.second,pitchY,yellow)==244)
    require(PieceCalibration.effectiveX(84,purpleBias.first,pitchX,purple)==0)
    require(PieceCalibration.effectiveY(baseTall,purpleBias.second,pitchY,purple)==330)
    val revised=PieceCalibration.changed(purple,-20,40)
    require(PieceCalibration.effectiveX(84,purpleBias.first,pitchX,revised)==-20)
    require(PieceCalibration.effectiveY(baseTall,purpleBias.second,pitchY,revised)==370)
    require(PieceCalibration.effectiveX(84,yellowBias.first,pitchX,yellow)==84)
    require(PieceCalibration.changed(revised,0,-20)==PieceCalibration.Adjustment(-20,20))

    // Screenshot scenario: a five-high piece landed col 8, rows 3-7,
    // instead of col 7, rows 4-8; completing a column cleared eight cells.
    // Indices are zero-based. Green corners/middle + the yellow L = 10 cells.
    val before=boardOf(
        1 to 0, 1 to 7, 5 to 2, 5 to 5, 7 to 0, 7 to 7,
        0 to 6, 0 to 7, 1 to 6, 2 to 6)
    require(java.lang.Long.bitCount(before)==10)
    val five=Piece((0..4).map { Cell(it,0) })
    val actual=Solver.placeAndClear(before,five,2,7)
    require(java.lang.Long.bitCount(actual)==7)
    val diagnosed=PlacementDiagnostics.findMatches(before,five,3,6,actual)
    require(diagnosed.size==1) { "Match count ${diagnosed.size}: $diagnosed" }
    require(diagnosed.single().dx==1 && diagnosed.single().dy == -1)
    require(diagnosed.single().clearedLines==1)
    require("cleared 1 line" in PlacementDiagnostics.diagnose(before,five,3,6,actual))

    // No-clear placement is also recognized as displacement.
    val other=Piece(listOf(Cell(0,0),Cell(0,1),Cell(1,0)))
    val simpleBefore=boardOf(7 to 7)
    val simpleAfter=Solver.placeAndClear(simpleBefore,other,2,3)
    val m=PlacementDiagnostics.findMatches(simpleBefore,other,1,2,simpleAfter)
    require(m.size==1 && m.single().dx==1 && m.single().dy==1)
    println("PASS: independent saved adjustments by color + shape")
    println("PASS: yellow verified preset unchanged; purple 5-high right/up error compensated")
    println("PASS: unexpected column clear correctly identified as +1 column / -1 row")
    println("PASS: normal displaced placement also diagnosed")
}
