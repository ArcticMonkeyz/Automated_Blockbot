import android.accessibilityservice.FakeGestureDriver as G
import com.example.rockblockbot.TouchService

fun main() {
    val t=TouchService()
    G.reset()
    var completed=false
    check(t.probeWithReturn(512f,1804f,499f,1244f) { ok,msg ->
        check(msg.contains("tray")); completed=ok
    })
    check(G.dispatched==1 && G.pending.size==1)
    check(t.isBusy)
    G.completeNext()
    check(completed && !t.isBusy && G.dispatched==1 && G.pending.isEmpty())

    var twoStopsDone=false
    check(t.probeWithTwoStops(512f,1804f,499f,1244f,581f,1244f) {ok,msg ->
        check(msg.contains("Two-stop"));twoStopsDone=ok
    })
    check(G.dispatched==2 && G.pending.size==1)
    G.completeNext()
    check(twoStopsDone && !t.isBusy && G.dispatched==2)

    var placed=false
    check(t.calibratedSingleDrag(512f,1804f,665f,1544f) { ok,msg ->
        check(msg.contains("released"));placed=ok
    })
    check(G.dispatched==3 && G.pending.size==1)
    G.completeNext()
    check(placed && !t.isBusy && G.dispatched==3 && G.pending.isEmpty())

    var cancelled=false
    check(t.probeWithReturn(512f,1804f,499f,1244f) { ok,msg ->
        cancelled=!ok && msg.contains("cancelled")
    })
    check(G.dispatched==4)
    G.cancelNext()
    check(cancelled && !t.isBusy)
    println("PASS: calibration probe, placement, cancellation each use one dispatch")
}
