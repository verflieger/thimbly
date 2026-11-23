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
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private companion object {
        const val CacheBytes = 32 * 1024 * 1024
    }
}
