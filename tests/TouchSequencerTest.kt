import android.accessibilityservice.FakeGestureDriver as G
import com.example.rockblockbot.TouchService

fun main() {
    val t=TouchService()
    val events=mutableListOf<String>()
    G.reset()
    check(t.beginHeldDrag(512f,1804f,500f,1120f){ok,msg -> events.add("start:$ok:$msg")})
    check(G.dispatched==1)
    G.completeNext()
    // This was the real v4.4 failure: a second held gesture was only queued
    // after returning through screen-capture and UI callbacks. Here Android's
    // next segment has already been dispatched before the owner callback.
    check(G.dispatched==2 && G.pending.size==1 && events.first().startsWith("start:true"))
    var moved=false
    check(t.continueHeldDrag(430f,1030f,750L){ok,msg -> moved=ok})
    G.completeNext() // idle completes; queued movement starts immediately
    check(G.dispatched==3 && !moved)
    G.completeNext() // movement completes; next hold starts BEFORE callback
    check(moved && G.dispatched==4 && t.isHolding)
    var released=false
    check(t.releaseHeldDrag {ok,msg -> released=ok})
    G.completeNext() // idle -> release segment
    G.completeNext() // release -> ended
    check(released && !t.isHolding && G.pending.isEmpty())
    G.reset()
    check(t.beginHeldDrag(512f,1804f,500f,1120f){ok,msg -> events.add("restart:$ok:$msg")})
    G.completeNext() // begin -> auto hold
    G.cancelNext()
    check(!t.isHolding && events.any { it.startsWith("restart:false") })
    println("PASS: first movement, immediate Android keepalive, queued move, release, cancellation")
}
