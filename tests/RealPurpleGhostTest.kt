import com.example.rockblockbot.*
import javax.imageio.ImageIO
import java.io.File

/** The user's actual v4.4.3 calibration screenshots: floating purple L partly hides its ghost. */
fun main(args:Array<String>) {
    val occupied = listOf(1 to 0,1 to 7,5 to 2,5 to 5,7 to 0,7 to 7)
        .fold(0L){a,(r,c)->a or (1L shl (r*8+c))}
    val piece = listOf(Cell(0,0),Cell(0,1),Cell(0,2),Cell(1,0))
    val target = ShadowTracker.Target(occupied,piece,1,4,"PURPLE")
    fun run(file:String):ShadowTracker.Scan {
        val img=ImageIO.read(File(file))
        return ShadowTracker.inspect(img.width,img.height,{x,y->img.getRGB(x,y)},target)
    }
    val real=run(args[0]); println("held purple: $real")
    check(real.match?.row==0 && real.match.col==5) { "Real partly occluded ghost not located: $real" }
    val pre=run(args[1]); println("before drag: $pre")
    check(pre.match == null) { "False ghost on baseline screenshot: $pre" }
    val done=run(args[2]); println("after return: $done")
    check(done.match == null) { "False ghost after return: $done" }
    println("PASS: real partly hidden purple shadow found; absent before and after drag")
}
