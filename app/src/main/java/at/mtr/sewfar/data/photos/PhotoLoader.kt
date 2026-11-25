package at.mtr.sewfar.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlin.math.max

/** Decodes stored photos for display, subsampled to the requested size, with a small memory cache. */
class PhotoLoader(private val files: PhotoFiles) {
    private val cache = object : LruCache<String, Bitmap>(CacheBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** Returns the photo with its long edge at least [minEdgePx] (or full size), or null if the file is missing. */
    fun load(photoId: String, minEdgePx: Int): Bitmap? {
        val key = "$photoId@$minEdgePx"
        cache.get(key)?.let { return it }
        val file = files.file(photoId)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minEdgePx) sample *= 2
        fun decode(s: Int) = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
        // A full-size fine photo is ~50 MB; with low memory the viewer shows it at half size instead of crashing.
        val bitmap = try { decode(sample) } catch (_: OutOfMemoryError) { decode(sample * 2) } ?: return null
        // Fine photos at full size would push every thumbnail out; normal ones (≤ 16 MB) are still cached.
        if (bitmap.allocationByteCount <= CacheBytes / 2) cache.put(key, bitmap)
        return bitmap
    }

    private companion object {
        const val CacheBytes = 32 * 1024 * 1024
    }
}
