package at.mtr.sewfar.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class PhotoImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Photo pipeline (SPEC 3.5): decode, apply EXIF orientation, downscale so the long edge is at most [MaxEdge] px
 * ([FineEdge] with the "Fine photos" setting),
 * flatten transparency onto white, re-encode as JPEG ([Quality]). Re-encoding writes no EXIF, so location and all other metadata are dropped.
 */
object ImageProcessor {
    const val MaxEdge = 2048
    const val FineEdge = 4096
    const val Quality = 85

    /** Reads the image via [open] (called up to three times) and writes the processed JPEG to [target]. */
    fun process(open: () -> InputStream, target: File, maxEdge: Int = MaxEdge) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoImportException("Not a decodable image")

        val orientation = try {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw PhotoImportException("Decoding failed")

        val scale = min(1f, maxEdge.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            applyOrientation(orientation)
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // JPEG has no alpha: transparent areas would turn black (e.g. a line drawing on a transparent PNG).
        val opaque = if (!result.hasAlpha()) result else Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(result, 0f, 0f, null) }
        }
        try {
            target.outputStream().use { out ->
                if (!opaque.compress(Bitmap.CompressFormat.JPEG, Quality, out)) throw PhotoImportException("Encoding failed")
            }
        } finally {
            if (opaque !== result) opaque.recycle()
            if (result !== decoded) result.recycle()
            decoded.recycle()
        }
    }

    /**
     * Power-of-two subsampling that keeps the decoded long edge at least [maxEdge] (the exact scale follows), but
     * halves again while the decode would exceed [MaxDecodePixels]: a 50 MP photo at [FineEdge] decodes at
     * 4080 px (~50 MB) instead of full size (~200 MB).
     */
    internal fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
        while (width.toLong() / sample * (height / sample) > MaxDecodePixels) sample *= 2
        return sample
    }

    private const val MaxDecodePixels = 24_000_000L

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
