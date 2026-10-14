import com.example.rockblockbot.*
fun main() {
    val w=DualProbe.windows(240f,1800f,500f,1000f,584f,1000f)
    check(w.first.toMs>w.first.fromMs && w.second.toMs>w.second.fromMs)
    check(w.first.toMs<w.second.fromMs && w.first.toMs-w.first.fromMs>800L)
    check(w.second.toMs-w.second.fromMs>800L)
    fun m(r:Int,c:Int,part:Boolean=true)=ShadowTracker.Match(r,c,.64,.3,4,1,part)
    val yes=DualProbe.validate(listOf(m(1,3),m(1,3)),listOf(m(1,4),m(1,4)),0,1)
    check(yes.accepted) {"Valid displacements rejected: $yes"}
    check(!DualProbe.validate(listOf(m(1,3),m(1,3)),listOf(m(1,3),m(1,3)),0,1).accepted)
    check(!DualProbe.validate(listOf(m(1,3)),listOf(m(1,4),m(1,4)),0,1).accepted)
    check(!DualProbe.validate(listOf(m(1,3),m(1,3)),listOf(m(3,4),m(3,4)),0,1).accepted)
    check(!DualProbe.validate(listOf(m(1,3),m(1,3)),listOf(m(1,4,false),m(1,4,false)),0,1).accepted)
    println("PASS: distinct probe windows, consistency and safety guards: $w")
}
