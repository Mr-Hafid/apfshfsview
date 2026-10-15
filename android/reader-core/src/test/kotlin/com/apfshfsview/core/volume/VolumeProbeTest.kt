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

    @Test
    fun `mbr with a logical ntfs partition is named instead of shown as a raw type`() {
        val image = ByteArray(8 * 1024 * 1024)
        fun putEntry(sectorOffset: Int, index: Int, type: Int, firstSector: Long, sectorCount: Long) {
            val entry = sectorOffset + 446 + index * 16
            image[entry + 4] = type.toByte()
            for (byte in 0 until 4) {
                image[entry + 8 + byte] = (firstSector ushr (8 * byte)).toByte()
                image[entry + 12 + byte] = (sectorCount ushr (8 * byte)).toByte()
            }
        }
        fun signSector(sectorOffset: Int) {
            image[sectorOffset + 510] = 0x55
            image[sectorOffset + 511] = 0xAA.toByte()
        }
        putEntry(0, 0, 0x0F, 2048, 12000)
        signSector(0)
        val extendedOffset = 2048 * 512
        putEntry(extendedOffset, 0, 0x07, 2048, 8000)
        signSector(extendedOffset)
        val logicalOffset = extendedOffset + 2048 * 512
        "NTFS    ".toByteArray().copyInto(image, logicalOffset + 3)
        image[logicalOffset] = 0xEB.toByte()
        signSector(logicalOffset)

        val result = VolumeProbe.probe(MemoryBlockDevice(image))
        assertTrue(result.volumes.isEmpty())
        assertEquals(listOf("NTFS — belum didukung"), result.unrecognizedPartitionTypes)
    }
}
