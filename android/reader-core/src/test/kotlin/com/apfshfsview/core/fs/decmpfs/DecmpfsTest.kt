package com.apfshfsview.core.fs.decmpfs

import com.apfshfsview.core.fs.ByteArrayFileReader
import com.apfshfsview.core.fs.UnsupportedCompressionException
import com.apfshfsview.core.fs.readFully
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecmpfsTest {
    private fun header(type: Int, size: Long, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(16 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x636D7066).putInt(type).putLong(size).put(payload).array()

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(bytes)
        deflater.finish()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return output.toByteArray()
    }

    private val noResourceFork: () -> ByteArrayFileReader = { error("resource fork not expected") }

    @Test
    fun `inline zlib data is inflated`() {
        val original = "hello ".repeat(500).toByteArray()
        val parsed = DecmpfsHeader.parse(header(3, original.size.toLong(), deflate(original)))
        assertTrue(parsed.isSupported)
        assertContentEquals(original, Decmpfs.openReader(parsed, noResourceFork).readFully(0, original.size))
    }

    @Test
    fun `inline data with raw marker is copied`() {
        val original = "tiny".toByteArray()
        val parsed = DecmpfsHeader.parse(header(3, original.size.toLong(), byteArrayOf(0x0F) + original))
        assertContentEquals(original, Decmpfs.openReader(parsed, noResourceFork).readFully(0, original.size))
    }

    @Test
    fun `resource fork chunks are inflated and seekable`() {
        val original = Random(3).nextBytes(150_000).also { it.fill(7, 0, 70_000) }
        val chunks = original.toList().chunked(65536).map { it.toByteArray() }
        val encodedChunks = chunks.mapIndexed { index, chunk -> if (index == 1) byteArrayOf(0xFF.toByte()) + chunk else deflate(chunk) }
        val tableSize = 4 + encodedChunks.size * 8
        val dataOffset = 0x100
        val table = ByteBuffer.allocate(tableSize).order(ByteOrder.LITTLE_ENDIAN).putInt(encodedChunks.size)
        var chunkOffset = tableSize
        encodedChunks.forEach { table.putInt(chunkOffset).putInt(it.size); chunkOffset += it.size }
        val fork = ByteArrayOutputStream()
        fork.write(ByteBuffer.allocate(dataOffset).putInt(dataOffset).array())
        fork.write(ByteBuffer.allocate(4).putInt(chunkOffset).array())
        fork.write(table.array())
        encodedChunks.forEach { fork.write(it) }
        val parsed = DecmpfsHeader.parse(header(4, original.size.toLong(), ByteArray(0)))
        val reader = Decmpfs.openReader(parsed) { ByteArrayFileReader(fork.toByteArray()) }
        assertContentEquals(original.copyOfRange(60_000, 140_000), reader.readFully(60_000, 80_000))
        assertContentEquals(original, reader.readFully(0, original.size))
    }

    @Test
    fun `lzfse type is reported as unsupported`() {
        val parsed = DecmpfsHeader.parse(header(11, 10, ByteArray(4)))
        assertFalse(parsed.isSupported)
        assertFailsWith<UnsupportedCompressionException> { Decmpfs.openReader(parsed, noResourceFork) }
    }
}
