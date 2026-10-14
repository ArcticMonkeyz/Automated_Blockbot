import com.example.rockblockbot.*
import java.io.File
import javax.imageio.ImageIO

/** The actual Oct 14 two-stop export: fringe at first dwell and clipped preview at second. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Pass the two Oct 14 diagnostic PNGs" }
    val occupied = listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4,
        2 to 3, 2 to 4, 5 to 2, 5 to 5, 7 to 0, 7 to 7)
        .fold(0L) { bits, (row,col) -> bits or (1L shl (row*8+col)) }
    val piece = (0..3).map { Cell(0,it) }
    fun inspect(path:String,probeCol:Int):OccludedPurpleShadow.Scan {
        val image=ImageIO.read(File(path))
        return OccludedPurpleShadow.inspect(image.width,image.height,
            {x,y -> image.getRGB(x,y)},
            ShadowTracker.Target(occupied,piece,4,4,"PURPLE",4,probeCol))
    }
    val first=inspect(args[0],2)
    val second=inspect(args[1],3)
    println("First actual dwell: $first")
    println("Second actual dwell: $second")
    check(first.match?.row==1 && first.match.col==3) {
        "Narrow purple fringe in first capture should be seen"
    }
    check(second.match==null) {
        "Do not trust the wholly solid piece at the right edge as a ghost"
    }
    val current = checkNotNull(first.match)
    val unsafe=DualProbe.validate(listOf(current,current),emptyList(),0,1)
    check(!unsafe.accepted)
    println("PASS: new fringe recovered, right-edge shadow rejected, two-stop gate stays closed")
}
