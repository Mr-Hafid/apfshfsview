package com.apfshfsview.core.fs.fat

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.DosTime
import com.apfshfsview.core.fs.cluster.DirectoryBuffer

data class FatDirectoryEntry(
    val id: Long,
    val name: String,
    val isDirectory: Boolean,
    val firstCluster: Long,
    val size: Long,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
)

data class FatDirectoryListing(val entries: List<FatDirectoryEntry>, val volumeLabel: String?)

object FatDirectory {
    private const val ENTRY_SIZE = FatBootSector.DIRECTORY_ENTRY_SIZE
    private const val ATTRIBUTE_LONG_NAME = 0x0F
    private const val ATTRIBUTE_VOLUME_LABEL = 0x08
    private const val ATTRIBUTE_DIRECTORY = 0x10
    private const val DELETED_MARKER = 0xE5
    private const val LAST_LONG_NAME_FLAG = 0x40
    private const val LOWERCASE_BASE_FLAG = 0x08
    private const val LOWERCASE_EXTENSION_FLAG = 0x10
    private val LONG_NAME_CHAR_OFFSETS = listOf(1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)

    fun parse(buffer: DirectoryBuffer): FatDirectoryListing {
        val bytes = buffer.bytes
        val entries = mutableListOf<FatDirectoryEntry>()
        var volumeLabel: String? = null
        val longNameParts = sortedMapOf<Int, String>()
        var longNameChecksum = -1
        var offset = 0
        while (offset + ENTRY_SIZE <= bytes.size) {
            val first = LittleEndian.u8(bytes, offset)
            if (first == 0) break
            val attributes = LittleEndian.u8(bytes, offset + 11)
            when {
                first == DELETED_MARKER -> longNameParts.clear()
                attributes == ATTRIBUTE_LONG_NAME -> {
                    if (first and LAST_LONG_NAME_FLAG != 0) longNameParts.clear()
                    longNameParts[first and 0x1F] = longNamePart(bytes, offset)
                    longNameChecksum = LittleEndian.u8(bytes, offset + 13)
                }
                attributes and ATTRIBUTE_VOLUME_LABEL != 0 -> {
                    volumeLabel = String(bytes, offset, 11, Charsets.ISO_8859_1).trimEnd(' ')
                    longNameParts.clear()
                }
                else -> {
                    val shortName = shortName(bytes, offset)
                    val longName = longNameParts.values.joinToString("")
                        .takeIf { it.isNotEmpty() && longNameChecksum == checksum(bytes, offset) }
                    longNameParts.clear()
                    val name = longName ?: shortName
                    if (name != "." && name != "..") entries += entry(bytes, offset, buffer.absoluteOffset(offset), name, attributes)
                }
            }
            offset += ENTRY_SIZE
        }
        return FatDirectoryListing(entries, volumeLabel)
    }

    fun parseShortEntry(bytes: ByteArray, absoluteOffset: Long): FatDirectoryEntry =
        entry(bytes, 0, absoluteOffset, shortName(bytes, 0), LittleEndian.u8(bytes, 11))

    private fun entry(bytes: ByteArray, offset: Int, absoluteOffset: Long, name: String, attributes: Int) =
        FatDirectoryEntry(
            id = absoluteOffset,
            name = name,
            isDirectory = attributes and ATTRIBUTE_DIRECTORY != 0,
            firstCluster = (LittleEndian.u16(bytes, offset + 20).toLong() shl 16) or LittleEndian.u16(bytes, offset + 26).toLong(),
            size = LittleEndian.u32(bytes, offset + 28),
            createdAtMillis = DosTime.toEpochMillis(
                LittleEndian.u16(bytes, offset + 16),
                LittleEndian.u16(bytes, offset + 14),
                LittleEndian.u8(bytes, offset + 13) * 5,
            ),
            modifiedAtMillis = DosTime.toEpochMillis(LittleEndian.u16(bytes, offset + 24), LittleEndian.u16(bytes, offset + 22)),
        )

    private fun longNamePart(bytes: ByteArray, offset: Int): String {
        val builder = StringBuilder()
        for (charOffset in LONG_NAME_CHAR_OFFSETS) {
            val unit = LittleEndian.u16(bytes, offset + charOffset)
            if (unit == 0x0000 || unit == 0xFFFF) break
            builder.append(unit.toChar())
        }
        return builder.toString()
    }

    private fun shortName(bytes: ByteArray, offset: Int): String {
        val raw = bytes.copyOfRange(offset, offset + 11)
        if (LittleEndian.u8(raw, 0) == 0x05) raw[0] = DELETED_MARKER.toByte()
        val caseFlags = LittleEndian.u8(bytes, offset + 12)
        var base = String(raw, 0, 8, Charsets.ISO_8859_1).trimEnd(' ')
        var extension = String(raw, 8, 3, Charsets.ISO_8859_1).trimEnd(' ')
        if (caseFlags and LOWERCASE_BASE_FLAG != 0) base = base.lowercase()
        if (caseFlags and LOWERCASE_EXTENSION_FLAG != 0) extension = extension.lowercase()
        return if (extension.isEmpty()) base else "$base.$extension"
    }

    private fun checksum(bytes: ByteArray, offset: Int): Int {
        var sum = 0
        for (index in 0 until 11) sum = (((sum and 1) shl 7) + (sum ushr 1) + LittleEndian.u8(bytes, offset + index)) and 0xFF
        return sum
    }
}
