// Minimal test-only placeholders for compiling BoardRecognizer without Android SDK.
// Not included in the Android app source set.
package android.graphics

class Bitmap(val width: Int = 709, val height: Int = 1536) {
    fun getPixel(x: Int, y: Int): Int = 0
    fun recycle() {}
    companion object {
        fun createScaledBitmap(source: Bitmap, width: Int, height: Int, filter: Boolean) = Bitmap(width, height)
    }
}
object Color {
    fun red(pixel: Int) = (pixel ushr 16) and 255
    fun green(pixel: Int) = (pixel ushr 8) and 255
    fun blue(pixel: Int) = pixel and 255
}
