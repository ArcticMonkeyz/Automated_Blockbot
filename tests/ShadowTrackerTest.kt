import com.example.rockblockbot.*
import javax.imageio.ImageIO
import java.io.File

/** Synthetic shadows plus the supplied real held-drag RED screenshot.
 * A missing or occluded ghost must NOT authorize an uncontrolled release.
 */
fun main(args:Array<String>) {
    val w=709;val h=1536
    val ivory=0xffe8e3d7.toInt()
    val colors=mapOf(
        "RED" to 0xffd49c9b.toInt(),
        "PURPLE" to 0xffbd91d3.toInt(),
        "BLUE" to 0xff8abddc.toInt(),
        "GREEN" to 0xffa0d89a.toInt(),
        "YELLOW" to 0xffe1d496.toInt())
    val footprints=listOf(listOf(Cell(0,0),Cell(0,1),Cell(0,2)),
        (0..2).map{Cell(it,0)},
        listOf(Cell(0,0),Cell(0,1),Cell(1,0),Cell(1,1)),
        listOf(Cell(0,0),Cell(0,1),Cell(0,2),Cell(1,1)),
        (0..4).map{Cell(it,0)})
    fun shade(pixels:IntArray, row:Int,col:Int,color:Int) {
        val cx=ShadowTracker.boardX(col,w);val cy=ShadowTracker.boardY(row,h)
        for(y in cy-15..cy+15)for(x in cx-15..cx+15)pixels[y*w+x]=color
    }
    for((name,color) in colors) for(shape in footprints) {
        val pixels=IntArray(w*h){ivory}
        for(tile in shape)shade(pixels,2+tile.r,3+tile.c,color)
        val target=ShadowTracker.Target(0L,shape,2,3,name)
        val match=ShadowTracker.locate(w,h,{x,y->pixels[y*w+x]},target)
        require(match?.row==2 && match.col==3) { "$name $shape => $match" }
        val gone=ShadowTracker.locate(w,h,{_,_->ivory},target)
        require(gone==null) { "False shadow: $name" }
        // Shift to an alternate legal origin: detector must report where it IS.
        val shifted=IntArray(w*h){ivory}
        for(tile in shape)shade(shifted,1+tile.r,2+tile.c,color)
        val result=ShadowTracker.locate(w,h,{x,y->shifted[y*w+x]},target)
        require(result?.row==1 && result.col==2) { "Shift missed: $name $shape => $result" }
    }
    val real=ImageIO.read(File(args[0]))
    val visible=ShadowTracker.Target(0L,
        (0..2).flatMap{r -> (0..2).map{c->Cell(r,c)} },0,2,"RED")
    val obscured=ShadowTracker.locate(real.width,real.height,{x,y->real.getRGB(x,y)},visible)
    println("Real red held-drag example: $obscured (partial/occluded shadow may be rejected)")
    println("PASS: all five piece colors across horizontal, vertical, square, T, five-high; null and shifted cases")
}
