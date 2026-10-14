import javax.imageio.ImageIO
import java.io.File
import android.graphics.Bitmap
import com.example.rockblockbot.BoardRecognizer
fun main(paths: Array<String>) {
    require(paths.isNotEmpty()) { "Pass PNG/JPG screenshot paths as arguments." }
    for(path in paths) {
        val img=ImageIO.read(File(path)) ?: error("Cannot read image: $path")
        val frame=BoardRecognizer.analyze(Bitmap(img))
        println("$path -> " + (frame?.pieces?.mapIndexed{i,p ->
            "${i+1}: " + (p?.let { "${it.tileColor} ${it.signature} (${it.count}, ${it.detectionMethod})" } ?: "NOT RECOGNIZED")
        } ?: listOf("no game frame")))
    }
}
