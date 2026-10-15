package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.FileBlockDevice
import com.apfshfsview.core.block.readBytes
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.VolumeFormat
import com.apfshfsview.core.partition.PartitionTypes
import com.apfshfsview.core.support.FileSystemFixtureContract
import com.apfshfsview.core.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun openFixtureVolume(device: BlockDevice): FileSystem =
    ApfsContainer.open(Fixtures.singlePartition(device, PartitionTypes.APFS))
        .volumes().single { it.info.name == "Fixture" }.fileSystem!!

class ApfsFixtureTest : FileSystemFixtureContract() {
    override val fixtureName = "apfs"

    override fun openFileSystem(device: BlockDevice): FileSystem = openFixtureVolume(device)

    @Test
    fun `volume is reported as APFS`() {
        assertEquals(VolumeFormat.APFS, fileSystem.info.format)
    }
}

class ApfsCaseSensitiveFixtureTest : FileSystemFixtureContract() {
    override val fixtureName = "apfs_case_sensitive"

    override fun openFileSystem(device: BlockDevice): FileSystem = openFixtureVolume(device)
}

class ApfsChecksumAndEncryptionTest {
    @Test
    fun `fletcher checksum validates a real block and rejects a modified one`() {
        Fixtures.assumePresent("apfs")
        FileBlockDevice(Fixtures.image("apfs")).use { device ->
            val partition = Fixtures.singlePartition(device, PartitionTypes.APFS)
            val block = partition.readBytes(0, 4096)
            assertTrue(Fletcher64.isValid(block))
            block[100] = (block[100] + 1).toByte()
            assertFalse(Fletcher64.isValid(block))
        }
    }

    @Test
    fun `encrypted volume is detected and not opened`() {
        Fixtures.assumePresent("apfs_encrypted")
        FileBlockDevice(Fixtures.image("apfs_encrypted")).use { device ->
            val volume = ApfsContainer.open(Fixtures.singlePartition(device, PartitionTypes.APFS)).volumes().single()
            assertEquals("Secret", volume.info.name)
            assertTrue(volume.info.isEncrypted)
            assertEquals(null, volume.fileSystem)
            assertEquals("unsupported_encryption", volume.failure?.code)
        }
    }
}
