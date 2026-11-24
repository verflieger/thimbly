package at.mtr.sewfar.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class PhotoImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Photo pipeline (SPEC 3.5): decode, apply EXIF orientation, downscale so the long edge is at most [MaxEdge] px,
 * re-encode as JPEG ([Quality]). Re-encoding writes no EXIF, so location and all other metadata are dropped.
 */
object ImageProcessor {
    const val MaxEdge = 2048
    const val Quality = 85

    /** Reads the image via [open] (called up to three times) and writes the processed JPEG to [target]. */
    fun process(open: () -> InputStream, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoImportException("Not a decodable image")

        val orientation = try {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

        // Power-of-two subsampling keeps memory low; the exact scale follows below.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MaxEdge) sample *= 2
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw PhotoImportException("Decoding failed")

        val scale = min(1f, MaxEdge.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            applyOrientation(orientation)
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        try {
            target.outputStream().use { out ->
                if (!result.compress(Bitmap.CompressFormat.JPEG, Quality, out)) throw PhotoImportException("Encoding failed")
            }
        } finally {
            if (result !== decoded) result.recycle()
            decoded.recycle()
        }
    }

    private fun Matrix.applyOrientation(orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }
}
