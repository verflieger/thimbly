package at.mtr.sewfar.data.photos

import at.mtr.sewfar.data.syncToStorage
import java.io.File
import java.io.InputStream

/** Photo files: `<filesDir>/photos/<photoId>.jpg` (SPEC 3.5). Files are immutable once written. */
class PhotoFiles(filesDir: File) {
    val dir: File = File(filesDir, "photos")

    fun file(photoId: String) = File(dir, "$photoId.jpg")

    /**
     * Processes the image and stores it under [photoId]; atomic and on storage when this returns, so a crash never
     * leaves a half-written or missing photo behind the row inserted next.
     */
    fun write(photoId: String, maxEdge: Int = ImageProcessor.MaxEdge, open: () -> InputStream) {
        if (dir.mkdirs()) dir.parentFile?.syncToStorage() // the very first photo also creates the folder
        val temp = File(dir, "$photoId.jpg.tmp")
        try {
            ImageProcessor.process(open, temp, maxEdge)
            temp.syncToStorage()
            if (!temp.renameTo(file(photoId))) throw PhotoImportException("Could not store photo")
            dir.syncToStorage()
        } finally {
            temp.delete()
        }
    }

    /** Also removes a temp file a crash left behind ([stored] lists it under the same ID). */
    fun delete(photoId: String) {
        file(photoId).delete()
        File(dir, "$photoId.jpg.tmp").delete()
    }

    /** IDs of all stored photo files, with their last-modified time. */
    fun stored(): Map<String, Long> = dir.listFiles().orEmpty()
        .filter { it.isFile }
        .associate { file -> file.name.substringBefore('.') to file.lastModified() }
}
