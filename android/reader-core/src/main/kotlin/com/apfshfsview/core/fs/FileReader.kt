package com.apfshfsview.core.fs

import java.io.Closeable

interface FileReader : Closeable {
    val size: Long

    fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int

    override fun close() {}
}

fun FileReader.readFully(position: Long, length: Int): ByteArray {
    val result = ByteArray(length)
    var copied = 0
    while (copied < length) {
        val count = read(position + copied, result, copied, length - copied)
        if (count <= 0) throw CorruptStructureException("Unexpected end of data at ${position + copied}")
        copied += count
    }
    return result
}

fun FileReader.readUpTo(position: Long, length: Int): ByteArray {
    val bounded = maxOf(0L, minOf(length.toLong(), size - position)).toInt()
    return readFully(position, bounded)
}
