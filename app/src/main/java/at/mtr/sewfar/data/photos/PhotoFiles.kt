package at.mtr.sewfar.data.photos

import java.io.File
import java.io.InputStream

/** Photo files: `<filesDir>/photos/<photoId>.jpg` (SPEC 3.6). Files are immutable once written. */
class PhotoFiles(filesDir: File) {
    val dir: File = File(filesDir, "photos")

    fun file(photoId: String) = File(dir, "$photoId.jpg")

    /** Processes the image and stores it under [photoId]; atomic, so a crash never leaves a half-written photo. */
    fun write(photoId: String, open: () -> InputStream) {
        dir.mkdirs()
        val temp = File(dir, "$photoId.jpg.tmp")
        try {
            ImageProcessor.process(open, temp)
            if (!temp.renameTo(file(photoId))) throw PhotoImportException("Could not store photo")
        } finally {
            temp.delete()
        }
    }

    fun delete(photoId: String) {
        file(photoId).delete()
    }

    /** IDs of all stored photo files, with their last-modified time. */
    fun stored(): Map<String, Long> = dir.listFiles().orEmpty()
        .filter { it.isFile }
        .associate { file -> file.name.substringBefore('.') to file.lastModified() }
}
