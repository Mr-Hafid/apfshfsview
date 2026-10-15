package com.apfshfsview.reader.preview

import android.content.Context
import com.apfshfsview.core.fs.FileSystem
import java.io.File
import java.io.FileOutputStream

class PreviewCache(context: Context) {
    private val directory = File(context.cacheDir, "preview")

    fun export(fileSystem: FileSystem, volumeHandle: Long, nodeId: Long): String {
        directory.mkdirs()
        val target = File(directory, "${volumeHandle}_$nodeId.pdf")
        val buffer = ByteArray(BUFFER_SIZE)
        fileSystem.openReader(nodeId).use { reader ->
            FileOutputStream(target).use { output ->
                var position = 0L
                while (position < reader.size) {
                    val count = reader.read(position, buffer, 0, buffer.size)
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                    position += count
                }
            }
        }
        return target.absolutePath
    }

    fun clear() {
        directory.deleteRecursively()
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20
    }
}
