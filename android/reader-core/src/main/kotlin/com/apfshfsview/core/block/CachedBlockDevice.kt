package com.apfshfsview.core.block

class CachedBlockDevice(
    private val inner: BlockDevice,
    private val chunkSize: Int = 64 * 1024,
    maxCachedBytes: Long = 32L * 1024 * 1024,
) : BlockDevice {
    override val sectorSize: Int get() = inner.sectorSize
    override val sizeBytes: Long get() = inner.sizeBytes

    private val directReadThreshold = chunkSize * 4
    private val maxChunks = (maxCachedBytes / chunkSize).toInt()
    private val chunks = object : LinkedHashMap<Long, ByteArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>): Boolean = size > maxChunks
    }

    override fun read(byteOffset: Long, destination: ByteArray, destinationOffset: Int, length: Int) {
        checkReadRange(byteOffset, length, sizeBytes)
        if (length >= directReadThreshold) {
            inner.read(byteOffset, destination, destinationOffset, length)
            return
        }
        var copied = 0
        while (copied < length) {
            val position = byteOffset + copied
            val chunkIndex = position / chunkSize
            val chunk = chunk(chunkIndex)
            val offsetInChunk = (position - chunkIndex * chunkSize).toInt()
            val count = minOf(length - copied, chunk.size - offsetInChunk)
            System.arraycopy(chunk, offsetInChunk, destination, destinationOffset + copied, count)
            copied += count
        }
    }

    @Synchronized
    private fun chunk(chunkIndex: Long): ByteArray = chunks[chunkIndex] ?: run {
        val start = chunkIndex * chunkSize
        val size = minOf(chunkSize.toLong(), sizeBytes - start).toInt()
        ByteArray(size).also {
            inner.read(start, it, 0, size)
            chunks[chunkIndex] = it
        }
    }
}
