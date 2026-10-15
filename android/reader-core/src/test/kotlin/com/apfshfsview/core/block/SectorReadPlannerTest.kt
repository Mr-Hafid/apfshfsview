package com.apfshfsview.core.block

import com.apfshfsview.core.fs.DeviceRangeUnsupportedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SectorReadPlannerTest {
    @Test
    fun `unaligned read is planned with skip and copy sizes`() {
        val reads = SectorReadPlanner.plan(byteOffset = 700, length = 1000, sectorSize = 512, maxTransferBytes = 1024)
        assertEquals(listOf(SectorRead(1, 2, 188, 836), SectorRead(3, 1, 0, 164)), reads)
    }

    @Test
    fun `read beyond the 32 bit sector limit is rejected`() {
        val limitByte = SectorReadPlanner.MAX_ADDRESSABLE_SECTORS * 512
        assertFailsWith<DeviceRangeUnsupportedException> {
            SectorReadPlanner.plan(limitByte - 256, 512, 512, 65536)
        }
    }

    @Test
    fun `read ending exactly at the limit is allowed`() {
        val limitByte = SectorReadPlanner.MAX_ADDRESSABLE_SECTORS * 512
        assertEquals(1, SectorReadPlanner.plan(limitByte - 512, 512, 512, 65536).size)
    }
}
