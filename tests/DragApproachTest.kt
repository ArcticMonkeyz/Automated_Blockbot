import com.example.rockblockbot.DragApproach

fun main() {
    // Exact green 2x2 failed case: old endpoint (499,1762) was off-board,
    // so the game never showed a placement shadow.
    val green=DragApproach.boardEntry(499f,1762f,1080,2169)
    check(green.clamped)
    check(green.y in 630f..1190f) { "Green approach remains off board: $green" }
    check(green.x in 235f..850f) { "Green approach X off board: $green" }
    // A preview already inside the playable area is left unchanged.
    val centered=DragApproach.boardEntry(530f,850f,1080,2169)
    check(!centered.clamped && centered.x==530f && centered.y==850f)
    // An extreme off-board coordinate is constrained in both axes.
    val extreme=DragApproach.boardEntry(1200f,2000f,1080,2169)
    check(extreme.clamped && extreme.x<900 && extreme.y<1200)
    println("PASS: board entry approaches the board and never authorizes release")
}
