package com.apfshfsview.core.fs.decmpfs

import com.apfshfsview.core.fs.ByteArrayFileReader
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.FileReader
import com.apfshfsview.core.fs.UnsupportedCompressionException
import java.util.zip.DataFormatException
import java.util.zip.Inflater

object Decmpfs {
    private const val MAX_INLINE_SIZE = 64L * 1024 * 1024
    private const val RAW_MARKER_MASK = 0x0F

    fun openReader(header: DecmpfsHeader, openResourceFork: () -> FileReader): FileReader =
        when (header.compressionType) {
            DecmpfsHeader.TYPE_UNCOMPRESSED_INLINE -> ByteArrayFileReader(inlineRaw(header, 0))
            DecmpfsHeader.TYPE_ZLIB_INLINE -> ByteArrayFileReader(inflateInline(header))
            DecmpfsHeader.TYPE_ZLIB_RESOURCE_FORK -> ZlibResourceForkReader(openResourceFork(), header.uncompressedSize)
            else -> throw UnsupportedCompressionException(header.compressionType)
        }

    private fun inflateInline(header: DecmpfsHeader): ByteArray {
        val payload = header.payload
        if (payload.isNotEmpty() && (payload[0].toInt() and RAW_MARKER_MASK) == RAW_MARKER_MASK) {
            return inlineRaw(header, 1)
        }
        return inflate(payload, 0, payload.size, expectedSize(header))
    }

    private fun inlineRaw(header: DecmpfsHeader, skip: Int): ByteArray {
        val size = expectedSize(header)
        if (header.payload.size - skip < size) throw CorruptStructureException("Inline decmpfs data is truncated")
        return header.payload.copyOfRange(skip, skip + size)
    }

    private fun expectedSize(header: DecmpfsHeader): Int {
        if (header.uncompressedSize !in 0..MAX_INLINE_SIZE) throw CorruptStructureException("Invalid inline size")
        return header.uncompressedSize.toInt()
    }

    fun inflate(input: ByteArray, offset: Int, length: Int, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(input, offset, length)
            val output = ByteArray(expectedSize)
            var produced = 0
            while (produced < expectedSize && !inflater.finished()) {
                val count = inflater.inflate(output, produced, expectedSize - produced)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                produced += count
            }
            if (produced != expectedSize) throw CorruptStructureException("zlib data produced $produced of $expectedSize bytes")
            return output
        } catch (error: DataFormatException) {
            throw CorruptStructureException("Invalid zlib data: ${error.message}")
        } finally {
            inflater.end()
        }
    }
}
