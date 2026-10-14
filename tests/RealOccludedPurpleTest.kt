import com.example.rockblockbot.*
import java.io.File
import javax.imageio.ImageIO

/** Regress against the Oct 14 v4.4.9 user's exported frame. */
fun main(args:Array<String>) {
    val img=ImageIO.read(File(args[0]))
    val piece=(0..3).map { Cell(0,it) }
    val occupied=listOf(0 to 0,0 to 1,0 to 2,0 to 3,0 to 4,2 to 3,2 to 4,5 to 2,5 to 5,7 to 0,7 to 7)
        .fold(0L){a,(r,c)->a or (1L shl (r*8+c))}
    val target=ShadowTracker.Target(occupied,piece,4,4,"PURPLE",4,2)
    val read={x:Int,y:Int->img.getRGB(x,y)}
    val result=OccludedPurpleShadow.inspect(img.width,img.height,read,target)
    println("October 14 occluded purple: $result")
    check(result.match?.row==1 && result.match.col==3) { "Expected row 2 col 4 from real frame: $result" }
    // Ignore an existing purple block in the board occupancy mask, and prove
    // a static colored block alone cannot be interpreted as an occluded preview.
    val withoutGhost=ImageIO.read(File(args[0]))
    val x=ShadowTracker.boardX(6,img.width)
    val y=ShadowTracker.boardY(1,img.height)
    val ivory=0xffe9e1d3.toInt()
    val dx=(ShadowTracker.pitchX(img.width)*.44f).toInt()
    val dy=(ShadowTracker.pitchY(img.height)*.44f).toInt()
    for(yy in y-dy..y+dy)for(xx in x-dx..x+dx)withoutGhost.setRGB(xx,yy,ivory)
    val absent=OccludedPurpleShadow.inspect(img.width,img.height,{a,b->withoutGhost.getRGB(a,b)},target)
    check(absent.match==null) { "False positive without trailing shadow: $absent" }
    val blank=OccludedPurpleShadow.inspect(img.width,img.height,{_,_->ivory},target)
    check(blank.match==null) { "False positive empty board: $blank" }
    println("PASS: real occluded four-strip recovered, shadow-less and blank rejected")
}
