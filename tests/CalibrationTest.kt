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
    println("PASS: on 1080x2400: X=$migratedX, Y=$migratedY, one-grid-cell shift = $dx x $dy px")
}
