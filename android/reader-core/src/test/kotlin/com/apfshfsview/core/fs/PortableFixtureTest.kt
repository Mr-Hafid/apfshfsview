package com.apfshfsview.core.fs

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.support.FileSystemFixtureContract
import com.apfshfsview.core.volume.VolumeProbe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun probeSingle(device: BlockDevice): FileSystem {
    val result = VolumeProbe.probe(device)
    assertTrue(result.unrecognizedPartitionTypes.isEmpty(), "Unexpected partitions: ${result.unrecognizedPartitionTypes}")
    return result.volumes.single().fileSystem!!
}

abstract class PortableFixtureContract(private val expectedFormat: VolumeFormat) : FileSystemFixtureContract() {
    override val expectedVolumeName = "FIXTURE"

    override fun openFileSystem(device: BlockDevice): FileSystem = probeSingle(device)

    @Test
    fun `volume reports its format and usage`() {
        assertEquals(expectedFormat, fileSystem.info.format)
        assertTrue(fileSystem.info.usedBytes in 1..fileSystem.info.totalBytes, "used=${fileSystem.info.usedBytes}")
    }
}

class Fat32MbrFixtureTest : PortableFixtureContract(VolumeFormat.FAT32) {
    override val fixtureName = "fat32_mbr"
}

class Fat32SuperfloppyFixtureTest : PortableFixtureContract(VolumeFormat.FAT32) {
    override val fixtureName = "fat32_superfloppy"
}

class Fat16MbrFixtureTest : PortableFixtureContract(VolumeFormat.FAT16) {
    override val fixtureName = "fat16_mbr"
}

class ExfatMbrFixtureTest : PortableFixtureContract(VolumeFormat.EXFAT) {
    override val fixtureName = "exfat_mbr"
    override val expectedVolumeName = "Fixture"
}

class ExfatGptFixtureTest : PortableFixtureContract(VolumeFormat.EXFAT) {
    override val fixtureName = "exfat_gpt"
    override val expectedVolumeName = "Fixture"
}
