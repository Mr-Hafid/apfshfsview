package com.apfshfsview.core.block

import com.apfshfsview.core.support.MemoryBlockDevice
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CachedBlockDeviceTest {
    private val content = Random(1).nextBytes(1024 * 1024)

    @Test
    fun `small reads are served from cache after the first read`() {
        val inner = MemoryBlockDevice(content)
        val cached = CachedBlockDevice(inner, chunkSize = 4096, maxCachedBytes = 64 * 1024)
        assertContentEquals(content.copyOfRange(100, 200), cached.readBytes(100, 100))
        assertContentEquals(content.copyOfRange(300, 400), cached.readBytes(300, 100))
        assertEquals(1, inner.readCount)
    }

    @Test
    fun `reads spanning chunks return correct bytes`() {
        val cached = CachedBlockDevice(MemoryBlockDevice(content), chunkSize = 4096)
        assertContentEquals(content.copyOfRange(4000, 9000), cached.readBytes(4000, 5000))
    }

    @Test
    fun `large reads bypass the cache`() {
        val inner = MemoryBlockDevice(content)
        val cached = CachedBlockDevice(inner, chunkSize = 4096)
        assertContentEquals(content.copyOfRange(0, 65536), cached.readBytes(0, 65536))
        assertEquals(1, inner.readCount)
    }
}
