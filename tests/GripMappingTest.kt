import com.example.rockblockbot.*

fun main() {
    val width=1080; val height=2169
    val fingerX=499f; val fingerY=1244f
    for (col in 0..6) for (row in 0..6) {
        val cal=GripMapping.fromProbe(fingerX,fingerY,col,row,width,height)
        val back=GripMapping.releaseFor(col,row,cal,width,height)
        check(back.first==fingerX && back.second==fingerY)
        if (col<6 && row<6) {
            val next=GripMapping.releaseFor(col+1,row+1,cal,width,height)
            check(next.first > fingerX && next.second > fingerY)
            check(kotlin.math.abs(next.first-fingerX-ShadowTracker.pitchX(width))<1.5f)
            check(kotlin.math.abs(next.second-fingerY-ShadowTracker.pitchY(height))<1.5f)
        }
    }
    println("PASS: 49 observed ghost origins invert to correct finger endpoints")
}
