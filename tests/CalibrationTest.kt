import com.example.rockblockbot.PlacementGeometry

fun main() {
    val w=1080
    val h=2400
    // The observed v2.9 landing was one column left, one row above.
    // Offsets are measured in Android display px, not the 709x1536 image.
    val dx=PlacementGeometry.cellWidthPx(w)
    val dy=PlacementGeometry.cellHeightPx(h)
    require(dx == 84) { "Grid column in screen px: $dx" }
    require(dy == 86) { "Grid row in screen px: $dy" }
    val priorX=0
    val priorY=400
    val migratedX=priorX+dx
    val migratedY=priorY+dy
    require(migratedX == 84 && migratedY == 486)
    // Applying migration twice is prohibited by the persistent version flag.
    val usedX = migratedX
    val usedY = migratedY
    require(usedX == 84 && usedY == 486)
    // After a placement that lands delta=-1,-1, recommended delta cancels it.
    fun correct(current:Int, observedDelta:Int, cellPx:Int)=current-observedDelta*cellPx
    require(correct(0,-1,dx)==84)
    require(correct(400,-1,dy)==486)
    // v3.0 red horizontal #### placed correctly with the global calibration.
    // The observed green ###/#.. landed +1 column,+1 row at that setting.
    // Its correction must NOT alter any other shape's effective offsets.
    require(PlacementGeometry.shapeCorrectionCells("###/#..") == (-1 to -1))
    require(PlacementGeometry.shapeCorrectionCells("####") == (0 to 0))
    // v3.4: purple five-wide strip landed one column RIGHT of its correct
    // preview. Compensate on this footprint only, preserving the good ####.
    require(PlacementGeometry.shapeCorrectionCells("#####") == (-1 to 0))
    require(PlacementGeometry.shapeCorrectionCells("#/#/#/#/#") == (0 to 0))
    require(PlacementGeometry.shapeCorrectionCells("#./##/.#") == (0 to 0))
    val (greenBiasX,greenBiasY)=PlacementGeometry.shapeCorrectionCells("###/#..")
    val effectiveGreenX=84+greenBiasX*dx
    val effectiveGreenY=484+greenBiasY*dy
    require(effectiveGreenX==0 && effectiveGreenY==398)
    val (purpleBiasX,purpleBiasY)=PlacementGeometry.shapeCorrectionCells("#####")
    val effectivePurpleX=84+purpleBiasX*dx
    val effectivePurpleY=484+purpleBiasY*dy
    require(effectivePurpleX==0 && effectivePurpleY==484)
    println("PASS: #### global X=84 Y=484; ##### effective X=$effectivePurpleX Y=$effectivePurpleY; green L effective X=$effectiveGreenX Y=$effectiveGreenY")
}
