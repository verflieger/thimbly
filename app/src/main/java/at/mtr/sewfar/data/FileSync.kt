package at.mtr.sewfar.data

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Forces a file's data, or a directory's entries (new files, renames), to storage. Without it, a crash or power loss
 * seconds after a write can leave the file empty or missing while the database row naming it survives (SPEC 3.5, 8.2).
 */
fun File.syncToStorage() = FileChannel.open(toPath(), StandardOpenOption.READ).use { it.force(true) }
