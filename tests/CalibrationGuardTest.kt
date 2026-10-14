import com.example.rockblockbot.*

private fun boardOf(vararg spots: Pair<Int,Int>): Long =
    spots.fold(0L) { board,(r,c)->board or (1L shl (r*8+c)) }

private fun sample(r: Int,c: Int,at: Long): CalibrationGuard.Reading =
    CalibrationGuard.Reading(ShadowTracker.Match(r,c,.82,.21,4,10),at)

fun main() {
    val single=listOf(Cell(0,0))
    val purple=(0..3).map {Cell(0,it)}
    check(CalibrationGuard.agreement(listOf(sample(2,3,10),sample(2,3,55)))==null)
    check(CalibrationGuard.agreement(listOf(sample(2,3,100),sample(2,3,405)))?.count==2)
    check(CalibrationGuard.agreement(listOf(sample(2,3,100),sample(2,3,405),
        sample(1,3,460),sample(1,3,770)))==null)
    check(!CalibrationGuard.fits(boardOf(2 to 3),purple,2,3))
    check(!CalibrationGuard.fits(0L,purple,2,5))
    check(CalibrationGuard.fits(0L,purple,2,4))
    // Actual purple-probe scale, including valid below-board touch position.
    check(CalibrationGuard.validProbe(0L,purple,4,4,2,4,499f,1244f,1080,2169))
    check(!CalibrationGuard.validProbe(0L,purple,4,4,0,0,499f,1244f,1080,2169))
    val measured=GripMapping.fromProbe(499f,1244f,4,2,1080,2169)
    val original=CalibrationGuard.release(0L,purple,4,4,measured,0,0,1080,2169)!!
    // Manual override added AFTER calibration moves the actual release.
    val adjusted=CalibrationGuard.release(0L,purple,4,4,measured,20,-20,1080,2169)!!
    check(adjusted.first==original.first+20f && adjusted.second==original.second-20f)
    check(original.second>ShadowTracker.boardY(7,2169)) // allowed: touch below board
    check(CalibrationGuard.release(0L,purple,4,4,measured,300,0,1080,2169)==null)
    check(CalibrationGuard.release(0L,purple,4,4,GripMapping.Offset(0f,1200f),0,0,1080,2169)==null)
    check(CalibrationGuard.release(boardOf(4 to 4),purple,4,4,measured,0,0,1080,2169)==null)
    check(CalibrationGuard.release(0L,single,7,7,GripMapping.Offset(900f,0f),0,0,1080,2169)==null)
    println("PASS: temporal agreement, legal footprint, plausible measured grip")
    println("PASS: manual changes shift only calibrated release, without stacking initial offsets")
    println("PASS: unsafe/outside-screen targets rejected; valid below-board touch retained")
}
