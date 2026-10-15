package com.apfshfsview.reader.preview

import android.media.MediaDataSource
import com.apfshfsview.core.fs.FileReader

class FileReaderMediaDataSource(private val reader: FileReader) : MediaDataSource() {
    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= reader.size) return -1
        if (size == 0) return 0
        return reader.read(position, buffer, offset, size)
    }

    override fun getSize(): Long = reader.size

    override fun close() = reader.close()
}
