package com.apfshfsview.core.fs.hfsplus

import com.apfshfsview.core.binary.BigEndian
import com.apfshfsview.core.fs.PhysicalExtent

data class HfsExtent(val startBlock: Long, val blockCount: Long)

data class HfsForkData(val logicalSize: Long, val totalBlocks: Long, val extents: List<HfsExtent>) {
    companion object {
        fun parse(bytes: ByteArray, offset: Int): HfsForkData = HfsForkData(
            logicalSize = BigEndian.u64(bytes, offset),
            totalBlocks = BigEndian.u32(bytes, offset + 12),
            extents = parseExtentRecord(bytes, offset + 16),
        )

        fun parseExtentRecord(bytes: ByteArray, offset: Int): List<HfsExtent> =
            (0 until 8).map {
                HfsExtent(BigEndian.u32(bytes, offset + it * 8), BigEndian.u32(bytes, offset + it * 8 + 4))
            }.filter { it.blockCount > 0 }
    }
}

fun List<HfsExtent>.toPhysicalExtents(blockSize: Int): List<PhysicalExtent> {
    var logicalOffset = 0L
    return map { extent ->
        PhysicalExtent(logicalOffset, extent.startBlock * blockSize, extent.blockCount * blockSize)
            .also { logicalOffset += it.length }
    }
}
