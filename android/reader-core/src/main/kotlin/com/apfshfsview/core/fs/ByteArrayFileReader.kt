package com.apfshfsview.core.fs

class ByteArrayFileReader(private val bytes: ByteArray) : FileReader {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), destination, offset, count)
        return count
    }
}
