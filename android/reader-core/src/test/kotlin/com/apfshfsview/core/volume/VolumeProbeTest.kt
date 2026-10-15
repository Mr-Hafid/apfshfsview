package com.apfshfsview.core.volume

import com.apfshfsview.core.block.FileBlockDevice
import com.apfshfsview.core.fs.VolumeFormat
import com.apfshfsview.core.support.Fixtures
import com.apfshfsview.core.support.MemoryBlockDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VolumeProbeTest {
    private fun probeFixture(name: String): ProbeResult {
        Fixtures.assumePresent(name)
        return FileBlockDevice(Fixtures.image(name)).use { VolumeProbe.probe(it) }
    }

    @Test
    fun `hfs plus image yields one readable volume`() {
        val result = probeFixture("hfsplus")
        val volume = result.volumes.single()
        assertEquals("Fixture", volume.info.name)
        assertEquals(VolumeFormat.HFS_PLUS, volume.info.format)
        assertTrue(volume.fileSystem != null)
        assertTrue(result.unrecognizedPartitionTypes.isEmpty())
    }

    @Test
    fun `apfs image yields its volume with usage numbers`() {
        val volume = probeFixture("apfs").volumes.single()
        assertEquals(VolumeFormat.APFS, volume.info.format)
        assertTrue(volume.info.usedBytes in 1..volume.info.totalBytes)
    }

    @Test
    fun `encrypted apfs image is listed but not openable`() {
        val volume = probeFixture("apfs_encrypted").volumes.single()
        assertTrue(volume.info.isEncrypted)
        assertEquals(null, volume.fileSystem)
    }

    @Test
    fun `blank device reports no volumes and an explanation`() {
        val result = VolumeProbe.probe(MemoryBlockDevice(ByteArray(1024 * 1024)))
        assertTrue(result.volumes.isEmpty())
        assertEquals(1, result.unrecognizedPartitionTypes.size)
    }
}
