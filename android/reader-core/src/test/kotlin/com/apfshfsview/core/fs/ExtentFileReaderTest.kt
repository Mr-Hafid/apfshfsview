package com.apfshfsview.core.fs

import com.apfshfsview.core.support.MemoryBlockDevice
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ExtentFileReaderTest {
    private val device = MemoryBlockDevice(ByteArray(64) { (it + 1).toByte() })

    @Test
    fun `reads across extents with a gap and a sparse extent`() {
        val reader = ExtentFileReader(
            device,
            listOf(
                PhysicalExtent(0, 10, 4),
                PhysicalExtent(6, null, 2),
                PhysicalExtent(8, 40, 4),
            ),
            size = 14,
        )
        val buffer = ByteArray(14)
        assertEquals(14, reader.read(0, buffer, 0, 14))
        assertContentEquals(byteArrayOf(11, 12, 13, 14, 0, 0, 0, 0, 41, 42, 43, 44, 0, 0), buffer)
    }

    @Test
    fun `read past the end returns minus one and short read at the tail`() {
        val reader = ExtentFileReader(device, listOf(PhysicalExtent(0, 0, 8)), size = 5)
        val buffer = ByteArray(10)
        assertEquals(2, reader.read(3, buffer, 0, 10))
        assertEquals(-1, reader.read(5, buffer, 0, 10))
    }

    @Test
    fun `zero byte file reads nothing`() {
        val reader = ExtentFileReader(device, emptyList(), size = 0)
        assertEquals(-1, reader.read(0, ByteArray(4), 0, 4))
        assertContentEquals(ByteArray(0), reader.readUpTo(0, 4))
    }

    @Test
    fun `read ahead reader returns the same bytes as the inner reader`() {
        val inner = ExtentFileReader(device, listOf(PhysicalExtent(0, 0, 64)), size = 64)
        val readAhead = ReadAheadFileReader(inner, windowSize = 16)
        val expected = inner.readFully(0, 64)
        val actual = ByteArray(64)
        var position = 0
        while (position < 64) position += readAhead.read(position.toLong(), actual, position, 7)
        assertContentEquals(expected, actual)
    }
}
