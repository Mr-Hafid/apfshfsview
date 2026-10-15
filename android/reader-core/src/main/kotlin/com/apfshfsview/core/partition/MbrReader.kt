package com.apfshfsview.core.partition

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.readBytes

data class MbrPartition(val typeCode: Int, val startByte: Long, val lengthBytes: Long)

object MbrReader {
    const val PROTECTIVE_GPT_TYPE = 0xEE
    private const val SECTOR_SIZE = 512
    private const val TABLE_OFFSET = 446
    private const val ENTRY_SIZE = 16
    private const val MAX_LOGICAL_PARTITIONS = 64
    private val EXTENDED_TYPES = setOf(0x05, 0x0F, 0x85)

    fun hasBootSignature(sector: ByteArray): Boolean =
        sector.size >= SECTOR_SIZE && LittleEndian.u16(sector, 510) == 0xAA55

    fun read(device: BlockDevice): List<MbrPartition>? {
        if (device.sizeBytes < SECTOR_SIZE) return null
        val sector = device.readBytes(0, SECTOR_SIZE)
        if (!hasBootSignature(sector)) return null
        val primaries = entries(sector, 0)
        if (primaries.isEmpty()) return null
        val result = mutableListOf<MbrPartition>()
        for (entry in primaries) {
            if (entry.typeCode in EXTENDED_TYPES) result += logicalPartitions(device, entry.startByte) else result += entry
        }
        return result
    }

    private fun entries(sector: ByteArray, baseByte: Long): List<MbrPartition> =
        (0 until 4).mapNotNull { index ->
            val offset = TABLE_OFFSET + index * ENTRY_SIZE
            val typeCode = LittleEndian.u8(sector, offset + 4)
            val firstSector = LittleEndian.u32(sector, offset + 8)
            val sectorCount = LittleEndian.u32(sector, offset + 12)
            if (typeCode == 0 || sectorCount == 0L) null
            else MbrPartition(typeCode, baseByte + firstSector * SECTOR_SIZE, sectorCount * SECTOR_SIZE)
        }

    private fun logicalPartitions(device: BlockDevice, extendedStart: Long): List<MbrPartition> {
        val result = mutableListOf<MbrPartition>()
        var tableStart = extendedStart
        repeat(MAX_LOGICAL_PARTITIONS) {
            if (tableStart + SECTOR_SIZE > device.sizeBytes) return result
            val sector = device.readBytes(tableStart, SECTOR_SIZE)
            if (!hasBootSignature(sector)) return result
            val links = entries(sector, 0)
            links.getOrNull(0)?.let { result += it.copy(startByte = tableStart + it.startByte) }
            val next = links.getOrNull(1)?.takeIf { it.typeCode in EXTENDED_TYPES } ?: return result
            tableStart = extendedStart + next.startByte
        }
        return result
    }
}
