package com.apfshfsview.core.fs.hfsplus

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.VolumeFormat
import com.apfshfsview.core.partition.PartitionTypes
import com.apfshfsview.core.support.FileSystemFixtureContract
import com.apfshfsview.core.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HfsPlusFixtureTest : FileSystemFixtureContract() {
    override val fixtureName = "hfsplus"

    override fun openFileSystem(device: BlockDevice): FileSystem =
        HfsPlusFileSystem.open(Fixtures.singlePartition(device, PartitionTypes.HFS_PLUS))

    @Test
    fun `volume is reported as clean HFS+`() {
        assertEquals(VolumeFormat.HFS_PLUS, fileSystem.info.format)
        assertFalse(fileSystem.info.isUncleanlyUnmounted)
    }
}

class HfsxFixtureTest : FileSystemFixtureContract() {
    override val fixtureName = "hfsx"

    override fun openFileSystem(device: BlockDevice): FileSystem =
        HfsPlusFileSystem.open(Fixtures.singlePartition(device, PartitionTypes.HFS_PLUS))

    @Test
    fun `volume is reported as HFSX`() {
        assertEquals(VolumeFormat.HFSX, fileSystem.info.format)
    }
}
