import com.example.rockblockbot.ProbeTiming

fun main() {
    val p = ProbeTiming.observationWindow(242f,1818f,583f,1244f)
    check(p.fromMs in 1000L..1500L) { "Board entry should be near 1.2s: $p" }
    check(p.toMs in 2700L..3200L) { "Board exit should be near 3s: $p" }
    check(p.toMs - p.fromMs >= 1400L) { "Need several camera frames: $p" }
    check(!p.includes(500L) && p.includes(1900L) && !p.includes(3700L))
    val q = ProbeTiming.observationWindow(512f,1804f,499f,1244f)
    check(q.fromMs < q.toMs && q.includes(2100L)) { "Green board entry: $q" }
    println("PASS: purple/green probe windows span board dwell and exclude travel: $p, $q")
}
