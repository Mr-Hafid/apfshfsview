package com.apfshfsview.core.block

import com.apfshfsview.core.fs.DeviceRangeUnsupportedException

data class SectorRead(
    val firstSector: Long,
    val sectorCount: Int,
    val skipBytes: Int,
    val copyBytes: Int,
)

object SectorReadPlanner {
    const val MAX_ADDRESSABLE_SECTORS = 1L shl 32

    fun plan(byteOffset: Long, length: Int, sectorSize: Int, maxTransferBytes: Int): List<SectorRead> {
        if (length == 0) return emptyList()
        val maxSectorsPerRead = maxOf(1, maxTransferBytes / sectorSize)
        val firstSector = byteOffset / sectorSize
        val endSectorExclusive = (byteOffset + length + sectorSize - 1) / sectorSize
        if (endSectorExclusive > MAX_ADDRESSABLE_SECTORS) throw DeviceRangeUnsupportedException(byteOffset)
        val reads = mutableListOf<SectorRead>()
        var sector = firstSector
        var remaining = length
        var skip = (byteOffset - firstSector * sectorSize).toInt()
        while (sector < endSectorExclusive) {
            val sectorCount = minOf(endSectorExclusive - sector, maxSectorsPerRead.toLong()).toInt()
            val copyBytes = minOf(sectorCount * sectorSize - skip, remaining)
            reads += SectorRead(sector, sectorCount, skip, copyBytes)
            remaining -= copyBytes
            sector += sectorCount
            skip = 0
        }
        return reads
    }
}
