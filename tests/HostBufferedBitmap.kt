package android.graphics
import java.awt.image.BufferedImage
class Bitmap(val img: BufferedImage) {
    val width get() = img.width
    val height get() = img.height
    fun getPixel(x: Int, y: Int) = img.getRGB(x, y)
    fun recycle() {}
    companion object {
        fun createScaledBitmap(source: Bitmap, width: Int, height: Int, filter: Boolean): Bitmap {
            val out=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB)
            val g=out.createGraphics()
            try { g.drawImage(source.img,0,0,width,height,null) } finally { g.dispose() }
            return Bitmap(out)
        }
    }
}
object Color {
    fun red(p:Int)=(p ushr 16) and 255
    fun green(p:Int)=(p ushr 8) and 255
    fun blue(p:Int)=p and 255
}
