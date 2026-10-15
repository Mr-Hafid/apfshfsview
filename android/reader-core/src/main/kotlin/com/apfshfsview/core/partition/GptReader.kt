package com.apfshfsview.core.partition

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.readBytes
import com.apfshfsview.core.fs.CorruptStructureException

data class PartitionEntry(
    val typeGuid: String,
    val name: String,
    val startByte: Long,
    val lengthBytes: Long,
)

object PartitionTypes {
    const val APFS = "7C3457EF-0000-11AA-AA11-00306543ECAC"
    const val HFS_PLUS = "48465300-0000-11AA-AA11-00306543ECAC"
    const val EFI_SYSTEM = "C12A7328-F81F-11D2-BA4B-00A0C93EC93B"
    const val UNUSED = "00000000-0000-0000-0000-000000000000"
}

object GptReader {
    private const val SIGNATURE = "EFI PART"
    private const val HEADER_LENGTH = 92

    fun read(device: BlockDevice): List<PartitionEntry>? {
        for (sectorSize in listOf(device.sectorSize, 512, 4096).distinct()) {
            if (device.sizeBytes < sectorSize * 2L) continue
            val header = device.readBytes(sectorSize.toLong(), HEADER_LENGTH)
            if (String(header, 0, 8, Charsets.US_ASCII) != SIGNATURE) continue
            val entriesLba = LittleEndian.u64(header, 72)
            val entryCount = LittleEndian.u32(header, 80).toInt()
            val entrySize = LittleEndian.u32(header, 84).toInt()
            if (entryCount !in 1..1024 || entrySize !in 128..4096) {
                throw CorruptStructureException("Invalid GPT header")
            }
            val table = device.readBytes(entriesLba * sectorSize, entryCount * entrySize)
            return (0 until entryCount).mapNotNull { parseEntry(table, it * entrySize, sectorSize) }
        }
        return null
    }

    private fun parseEntry(table: ByteArray, offset: Int, sectorSize: Int): PartitionEntry? {
        val typeGuid = formatGuid(table, offset)
        if (typeGuid == PartitionTypes.UNUSED) return null
        val firstLba = LittleEndian.u64(table, offset + 32)
        val lastLba = LittleEndian.u64(table, offset + 40)
        val name = String(table, offset + 56, 72, Charsets.UTF_16LE).trimEnd('\u0000')
        return PartitionEntry(typeGuid, name, firstLba * sectorSize, (lastLba - firstLba + 1) * sectorSize)
    }

    fun formatGuid(bytes: ByteArray, offset: Int): String {
        val tail = (8 until 16).joinToString("") { "%02X".format(bytes[offset + it].toInt() and 0xFF) }
        return "%08X-%04X-%04X-%s-%s".format(
            LittleEndian.u32(bytes, offset),
            LittleEndian.u16(bytes, offset + 4),
            LittleEndian.u16(bytes, offset + 6),
            tail.substring(0, 4),
            tail.substring(4),
        )
    }
}
