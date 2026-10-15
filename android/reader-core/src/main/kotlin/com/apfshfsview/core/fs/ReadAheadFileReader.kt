package com.apfshfsview.core.fs

class ReadAheadFileReader(private val inner: FileReader, windowSize: Int) : FileReader {
    override val size: Long get() = inner.size

    private val window = ByteArray(windowSize)
    private var windowStart = -1L
    private var windowLength = 0

    override fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        if (length == 0) return 0
        if (windowStart < 0 || position < windowStart || position >= windowStart + windowLength) fill(position)
        if (windowLength == 0) return -1
        val offsetInWindow = (position - windowStart).toInt()
        val count = minOf(length, windowLength - offsetInWindow)
        System.arraycopy(window, offsetInWindow, destination, offset, count)
        return count
    }

    private fun fill(position: Long) {
        windowStart = position
        windowLength = 0
        val wanted = minOf(window.size.toLong(), size - position).toInt()
        while (windowLength < wanted) {
            val count = inner.read(position + windowLength, window, windowLength, wanted - windowLength)
            if (count <= 0) break
            windowLength += count
        }
    }

    override fun close() = inner.close()
}
