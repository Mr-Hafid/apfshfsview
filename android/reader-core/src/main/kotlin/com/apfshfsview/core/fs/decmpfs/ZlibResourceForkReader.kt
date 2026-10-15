package com.apfshfsview.core.fs.decmpfs

import com.apfshfsview.core.binary.BigEndian
import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.FileReader
import com.apfshfsview.core.fs.readFully

class ZlibResourceForkReader(
    private val resourceFork: FileReader,
    override val size: Long,
) : FileReader {
    private val chunkOffsets: LongArray
    private val chunkLengths: IntArray
    private var cachedChunkIndex = -1
    private var cachedChunk = ByteArray(0)

    init {
        val dataOffset = BigEndian.u32(resourceFork.readFully(0, 4), 0)
        val tableBase = dataOffset + 4
        val chunkCount = LittleEndian.u32(resourceFork.readFully(tableBase, 4), 0).toInt()
        val expectedChunks = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        if (chunkCount < expectedChunks || chunkCount > expectedChunks + 1) {
            throw CorruptStructureException("Resource fork has $chunkCount chunks, expected $expectedChunks")
        }
        val table = resourceFork.readFully(tableBase + 4, chunkCount * 8)
        chunkOffsets = LongArray(chunkCount) { tableBase + LittleEndian.u32(table, it * 8) }
        chunkLengths = IntArray(chunkCount) { LittleEndian.u32(table, it * 8 + 4).toInt() }
    }

    override fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        val readable = minOf(length.toLong(), size - position).toInt()
        var copied = 0
        while (copied < readable) {
            val current = position + copied
            val chunkIndex = (current / CHUNK_SIZE).toInt()
            val chunk = chunk(chunkIndex)
            val offsetInChunk = (current - chunkIndex.toLong() * CHUNK_SIZE).toInt()
            val count = minOf(readable - copied, chunk.size - offsetInChunk)
            System.arraycopy(chunk, offsetInChunk, destination, offset + copied, count)
            copied += count
        }
        return readable
    }

    @Synchronized
    private fun chunk(index: Int): ByteArray {
        if (index == cachedChunkIndex) return cachedChunk
        val uncompressedLength = minOf(CHUNK_SIZE.toLong(), size - index.toLong() * CHUNK_SIZE).toInt()
        val compressed = resourceFork.readFully(chunkOffsets[index], chunkLengths[index])
        val decoded = if (compressed.isNotEmpty() && (compressed[0].toInt() and 0xFF) == RAW_CHUNK_MARKER) {
            compressed.copyOfRange(1, 1 + uncompressedLength)
        } else {
            Decmpfs.inflate(compressed, 0, compressed.size, uncompressedLength)
        }
        cachedChunkIndex = index
        cachedChunk = decoded
        return decoded
    }

    override fun close() = resourceFork.close()

    private companion object {
        const val CHUNK_SIZE = 65536
        const val RAW_CHUNK_MARKER = 0xFF
    }
}
