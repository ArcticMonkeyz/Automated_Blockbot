import com.example.rockblockbot.*
fun main() {
 val w=709;val h=1536; val bg=0xffe8e3d7.toInt();val purple=0xffbd91d3.toInt()
 val shape=(0..3).map { Cell(0,it) }
 fun frame(r:Int,c:Int):IntArray {
  val a=IntArray(w*h){bg}
  for(tile in shape) {
   val x=ShadowTracker.boardX(c+tile.c,w);val y=ShadowTracker.boardY(r,h)
   for(yy in y-14..y+14)for(xx in x-14..x+14)a[yy*w+xx]=purple
  }
  return a
 }
 val far=frame(7,2)
 val near=ShadowTracker.Target(0L,shape,4,2,"PURPLE",4,2)
 check(ShadowTracker.locate(w,h,{x,y->far[y*w+x]},near)==null) {"Far-away ghost accepted"}
 val good=frame(4,2)
 check(ShadowTracker.locate(w,h,{x,y->good[y*w+x]},near)?.row==4) {"Probe-local ghost rejected"}
 val blank=IntArray(w*h){bg}
 check(ShadowTracker.locate(w,h,{x,y->blank[y*w+x]},near)==null)
 println("PASS: probe-aware horizontal purple shadow accepts nearby, rejects distant and absent")
}
