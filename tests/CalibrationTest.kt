import com.example.rockblockbot.PlacementGeometry

fun main() {
    val col = PlacementGeometry.cellWidthPx(1080)
    val row = PlacementGeometry.cellHeightPx(2400)
    require(col == 84 && row == 86)

    val globalY = 484
    val tallDelta = PlacementGeometry.DEFAULT_TALL_DELTA_PX
    require(tallDelta == -240)
    // No v3.7 bypass: a tall piece must follow manual GLOBAL Y changes.
    val y3 = PlacementGeometry.effectiveYOffsetPx(3,2,globalY,tallDelta,0,row)
    val y5 = PlacementGeometry.effectiveYOffsetPx(5,1,globalY,tallDelta,0,row)
    require(y3 == 244 && y5 == 244)
    require(PlacementGeometry.effectiveYOffsetPx(3,2,globalY + 20,tallDelta,0,row) == y3 + 20)
    require(PlacementGeometry.effectiveYOffsetPx(5,1,globalY + 20,tallDelta,0,row) == y5 + 20)
    // Tall-only controls work without moving short/horizontal pieces.
    require(PlacementGeometry.effectiveYOffsetPx(3,2,globalY,tallDelta + 20,0,row) == y3 + 20)
    require(PlacementGeometry.effectiveYOffsetPx(1,5,globalY,tallDelta + 20,0,row) == globalY)
    require(PlacementGeometry.effectiveYOffsetPx(2,3,globalY,tallDelta + 20,0,row) == globalY)
    require(PlacementGeometry.effectiveYOffsetPx(3,2,globalY,tallDelta,-1,row) == y3 - row)
    require(PlacementGeometry.isTall(3,2) && PlacementGeometry.isTall(5,1))
    require(!PlacementGeometry.isTall(2,3) && !PlacementGeometry.isTall(1,5))
    // Previously tuned empirical X/Y corrections continue to apply.
    require(PlacementGeometry.shapeCorrectionCells("###/#..") == (-1 to -1))
    require(PlacementGeometry.shapeCorrectionCells("####") == (0 to 0))
    require(PlacementGeometry.shapeCorrectionCells("#####", "PURPLE") == (-1 to 0))
    require(PlacementGeometry.shapeCorrectionCells("#####", "YELLOW") == (0 to 0))
    val targetGrip = (454f + 2 * 55f) * 2400f / 1536f
    val oldDirectY = targetGrip + 0
    val oldGlobalY = targetGrip + globalY
    val newTallY = targetGrip + y3
    require(newTallY > oldDirectY && newTallY < oldGlobalY)
    println("PASS: tall finger endpoint is tunable with GLOBAL Y and TALL DELTA")
    println("PASS: default tall effective Y=$y3; horizontal effective Y=$globalY")
    println("PASS: short-piece shape-specific corrections unchanged")
}
