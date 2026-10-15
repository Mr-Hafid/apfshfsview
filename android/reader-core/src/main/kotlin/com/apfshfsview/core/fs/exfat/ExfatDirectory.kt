package com.apfshfsview.core.fs.exfat

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.DosTime
import com.apfshfsview.core.fs.cluster.DirectoryBuffer

data class ExfatDirectoryEntry(
    val id: Long,
    val name: String,
    val isDirectory: Boolean,
    val firstCluster: Long,
    val isContiguous: Boolean,
    val validDataLength: Long,
    val dataLength: Long,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
)

data class ExfatAllocationBitmap(val firstCluster: Long, val length: Long)

data class ExfatDirectoryListing(
    val entries: List<ExfatDirectoryEntry>,
    val volumeLabel: String?,
    val allocationBitmap: ExfatAllocationBitmap?,
)

object ExfatDirectory {
    private const val ENTRY_SIZE = 32
    private const val TYPE_END = 0x00
    private const val IN_USE_FLAG = 0x80
    private const val TYPE_ALLOCATION_BITMAP = 0x81
    private const val TYPE_VOLUME_LABEL = 0x83
    private const val TYPE_FILE = 0x85
    private const val TYPE_STREAM_EXTENSION = 0xC0
    private const val TYPE_FILE_NAME = 0xC1
    private const val ATTRIBUTE_DIRECTORY = 0x10
    private const val NO_FAT_CHAIN_FLAG = 0x02
    private const val NAME_CHARACTERS_PER_ENTRY = 15

    fun parse(buffer: DirectoryBuffer): ExfatDirectoryListing {
        val bytes = buffer.bytes
        val entries = mutableListOf<ExfatDirectoryEntry>()
        var volumeLabel: String? = null
        var allocationBitmap: ExfatAllocationBitmap? = null
        var offset = 0
        while (offset + ENTRY_SIZE <= bytes.size) {
            val type = LittleEndian.u8(bytes, offset)
            if (type == TYPE_END) break
            when (type) {
                TYPE_ALLOCATION_BITMAP -> if (allocationBitmap == null) {
                    allocationBitmap = ExfatAllocationBitmap(
                        firstCluster = LittleEndian.u32(bytes, offset + 20),
                        length = LittleEndian.u64(bytes, offset + 24),
                    )
                }
                TYPE_VOLUME_LABEL -> {
                    val length = LittleEndian.u8(bytes, offset + 1).coerceAtMost(11)
                    volumeLabel = String(bytes, offset + 2, length * 2, Charsets.UTF_16LE)
                }
                TYPE_FILE -> {
                    val secondaryCount = LittleEndian.u8(bytes, offset + 1)
                    parseFileSet(bytes, offset, secondaryCount, buffer.absoluteOffset(offset))?.let { entries += it }
                    offset += secondaryCount * ENTRY_SIZE
                }
            }
            offset += ENTRY_SIZE
        }
        return ExfatDirectoryListing(entries, volumeLabel, allocationBitmap)
    }

    private fun parseFileSet(bytes: ByteArray, fileOffset: Int, secondaryCount: Int, id: Long): ExfatDirectoryEntry? {
        val streamOffset = fileOffset + ENTRY_SIZE
        if (secondaryCount < 2 || fileOffset + (secondaryCount + 1) * ENTRY_SIZE > bytes.size) return null
        if (LittleEndian.u8(bytes, streamOffset) != TYPE_STREAM_EXTENSION) return null
        val nameLength = LittleEndian.u8(bytes, streamOffset + 3)
        val name = StringBuilder()
        for (index in 2..secondaryCount) {
            val nameOffset = fileOffset + index * ENTRY_SIZE
            if (LittleEndian.u8(bytes, nameOffset) != TYPE_FILE_NAME) continue
            val remaining = nameLength - name.length
            if (remaining <= 0) break
            val count = minOf(NAME_CHARACTERS_PER_ENTRY, remaining)
            name.append(String(bytes, nameOffset + 2, count * 2, Charsets.UTF_16LE))
        }
        val attributes = LittleEndian.u16(bytes, fileOffset + 4)
        return ExfatDirectoryEntry(
            id = id,
            name = name.toString(),
            isDirectory = attributes and ATTRIBUTE_DIRECTORY != 0,
            firstCluster = LittleEndian.u32(bytes, streamOffset + 20),
            isContiguous = LittleEndian.u8(bytes, streamOffset + 1) and NO_FAT_CHAIN_FLAG != 0,
            validDataLength = LittleEndian.u64(bytes, streamOffset + 8),
            dataLength = LittleEndian.u64(bytes, streamOffset + 24),
            createdAtMillis = timestamp(bytes, fileOffset + 8, fileOffset + 20, fileOffset + 22),
            modifiedAtMillis = timestamp(bytes, fileOffset + 12, fileOffset + 21, fileOffset + 23),
        )
    }

    private fun timestamp(bytes: ByteArray, valueOffset: Int, hundredthsOffset: Int, utcOffsetOffset: Int): Long {
        val value = LittleEndian.u32(bytes, valueOffset)
        val utcOffsetByte = LittleEndian.u8(bytes, utcOffsetOffset)
        val utcOffsetMinutes = if (utcOffsetByte and 0x80 != 0) {
            val quarterHours = utcOffsetByte and 0x7F
            (if (quarterHours >= 0x40) quarterHours - 0x80 else quarterHours) * 15
        } else {
            null
        }
        return DosTime.toEpochMillis(
            date = (value ushr 16).toInt(),
            time = (value and 0xFFFF).toInt(),
            hundredths = LittleEndian.u8(bytes, hundredthsOffset),
            utcOffsetMinutes = utcOffsetMinutes,
        )
    }
}
