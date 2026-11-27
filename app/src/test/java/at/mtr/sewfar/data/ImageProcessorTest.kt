package at.mtr.sewfar.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import at.mtr.sewfar.data.photos.ImageProcessor
import at.mtr.sewfar.data.photos.PhotoImportException
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** SPEC 3.5: orientation applied, long edge at most 2048 px, no upscaling, EXIF (incl. location) removed, transparency white. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageProcessorTest {
    private val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "img-test").apply { mkdirs() }
    private val output = File(dir, "out.jpg")

    @After fun tearDown() { dir.deleteRecursively() }

    private fun input(width: Int, height: Int, exif: Map<String, String> = emptyMap()): File {
        val file = File(dir, "in.jpg")
        file.writeBytes(TestDb.jpeg(width, height))
        if (exif.isNotEmpty()) {
            ExifInterface(file.path).apply {
                exif.forEach { (tag, value) -> setAttribute(tag, value) }
                saveAttributes()
            }
        }
        return file
    }

    private fun process(file: File, maxEdge: Int = ImageProcessor.MaxEdge): Pair<Int, Int> {
        ImageProcessor.process({ file.inputStream() }, output, maxEdge)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.path, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    @Test
    fun transparencyBecomesWhite() {
        val png = File(dir, "in.png")
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT); setPixel(20, 20, Color.BLACK) }
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        process(png)
        val pixel = BitmapFactory.decodeFile(output.path).getPixel(0, 0)
        assertEquals(255.0, Color.red(pixel).toDouble(), 3.0)
        assertEquals(255.0, Color.blue(pixel).toDouble(), 3.0)
    }

    @Test
    fun downscalesLongEdgeTo2048() {
        assertEquals(2048 to 1536, process(input(4000, 3000)))
        assertEquals(1152 to 2048, process(input(2700, 4800)))
    }

    @Test
    fun fineKeepsUpTo4096() {
        assertEquals(4096 to 2048, process(input(5000, 2500), ImageProcessor.FineEdge))
    }

    @Test
    fun largePhotosAreDecodedWithinTheMemoryBudget() {
        assertEquals(1, ImageProcessor.sampleSize(4080, 3072, ImageProcessor.FineEdge)) // 12.5 MP: full size
        assertEquals(2, ImageProcessor.sampleSize(8160, 6120, ImageProcessor.FineEdge)) // 50 MP: half size
        assertEquals(2, ImageProcessor.sampleSize(8160, 6120, ImageProcessor.MaxEdge)) // normal: unchanged
    }

    @Test
    fun doesNotUpscaleSmallImages() {
        assertEquals(800 to 600, process(input(800, 600)))
    }

    @Test
    fun appliesExifRotation() {
        val rotated = input(400, 200, mapOf(ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_90.toString()))
        assertEquals(200 to 400, process(rotated))
    }

    @Test
    fun removesLocationAndOtherMetadata() {
        val tagged = input(
            300, 200,
            mapOf(
                ExifInterface.TAG_GPS_LATITUDE to "48/1,12/1,0/1",
                ExifInterface.TAG_GPS_LATITUDE_REF to "N",
                ExifInterface.TAG_GPS_LONGITUDE to "16/1,22/1,0/1",
                ExifInterface.TAG_GPS_LONGITUDE_REF to "E",
                ExifInterface.TAG_MAKE to "PhoneMaker",
            ),
        )
        // Precondition: the input really carries the tags.
        assertEquals("N", ExifInterface(tagged.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))

        process(tagged)

        val exif = ExifInterface(output.path)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test(expected = PhotoImportException::class)
    fun rejectsNonImages() {
        val file = File(dir, "notes.txt").apply { writeText("not an image") }
        ImageProcessor.process({ file.inputStream() }, output)
    }
}
