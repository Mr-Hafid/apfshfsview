package com.apfshfsview.core.fs.hfsplus

import com.apfshfsview.core.binary.BigEndian
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.readBytes
import com.apfshfsview.core.fs.CorruptStructureException

class HfsPlusVolumeHeader(
    val signature: Int,
    val attributes: Long,
    val blockSize: Int,
    val totalBlocks: Long,
    val freeBlocks: Long,
    val extentsFile: HfsForkData,
    val catalogFile: HfsForkData,
    val attributesFile: HfsForkData,
) {
    val isHfsx: Boolean get() = signature == SIGNATURE_HFSX
    val isCleanlyUnmounted: Boolean get() = attributes and UNMOUNTED_BIT != 0L

    companion object {
        const val OFFSET = 1024L
        private const val LENGTH = 512
        private const val SIGNATURE_HFS_PLUS = 0x482B
        private const val SIGNATURE_HFSX = 0x4858
        private const val UNMOUNTED_BIT = 1L shl 8

        fun matches(device: BlockDevice): Boolean =
            device.sizeBytes >= OFFSET + LENGTH &&
                BigEndian.u16(device.readBytes(OFFSET, 2), 0) in setOf(SIGNATURE_HFS_PLUS, SIGNATURE_HFSX)

        fun read(device: BlockDevice): HfsPlusVolumeHeader {
            val bytes = device.readBytes(OFFSET, LENGTH)
            val signature = BigEndian.u16(bytes, 0)
            if (signature != SIGNATURE_HFS_PLUS && signature != SIGNATURE_HFSX) {
                throw CorruptStructureException("HFS+ signature not found")
            }
            val blockSize = BigEndian.u32(bytes, 40).toInt()
            if (blockSize < 512 || blockSize and (blockSize - 1) != 0) {
                throw CorruptStructureException("Invalid HFS+ block size $blockSize")
            }
            return HfsPlusVolumeHeader(
                signature = signature,
                attributes = BigEndian.u32(bytes, 4),
                blockSize = blockSize,
                totalBlocks = BigEndian.u32(bytes, 44),
                freeBlocks = BigEndian.u32(bytes, 48),
                extentsFile = HfsForkData.parse(bytes, 192),
                catalogFile = HfsForkData.parse(bytes, 272),
                attributesFile = HfsForkData.parse(bytes, 352),
            )
        }
    }
}
