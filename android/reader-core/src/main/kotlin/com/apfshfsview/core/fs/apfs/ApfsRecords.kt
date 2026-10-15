package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.NodeKind

object ApfsRecordTypes {
    const val INODE = 3
    const val XATTR = 4
    const val FILE_EXTENT = 8
    const val DIR_REC = 9
}

object ApfsKeys {
    private const val OBJECT_ID_MASK = 0x0FFFFFFFFFFFFFFFL

    fun objectId(block: ByteArray, keyOffset: Int): Long = LittleEndian.u64(block, keyOffset) and OBJECT_ID_MASK

    fun recordType(block: ByteArray, keyOffset: Int): Int = (LittleEndian.u64(block, keyOffset) ushr 60).toInt()

    fun compareHeader(block: ByteArray, keyOffset: Int, objectId: Long, recordType: Int): Int {
        val recordObjectId = objectId(block, keyOffset)
        if (recordObjectId != objectId) return recordObjectId.compareTo(objectId)
        return recordType(block, keyOffset).compareTo(recordType)
    }

    fun matches(entry: ApfsBTreeEntry, objectId: Long, recordType: Int): Boolean =
        compareHeader(entry.block, entry.keyOffset, objectId, recordType) == 0
}

class ApfsInode(
    val objectId: Long,
    val parentId: Long,
    val privateId: Long,
    val createdAtNanos: Long,
    val modifiedAtNanos: Long,
    val childOrLinkCount: Int,
    private val bsdFlags: Long,
    private val mode: Int,
    val name: String?,
    val dataStreamSize: Long?,
) {
    val kind: NodeKind
        get() = when (mode and FILE_TYPE_MASK) {
            DIRECTORY_MODE -> NodeKind.DIRECTORY
            SYMLINK_MODE -> NodeKind.SYMLINK
            else -> NodeKind.FILE
        }

    val isCompressed: Boolean get() = bsdFlags and COMPRESSED_FLAG != 0L

    companion object {
        private const val FILE_TYPE_MASK = 0xF000
        private const val DIRECTORY_MODE = 0x4000
        private const val SYMLINK_MODE = 0xA000
        private const val COMPRESSED_FLAG = 0x20L
        private const val FIELD_NAME = 4
        private const val FIELD_DATA_STREAM = 8
        private const val EXTENDED_FIELDS_OFFSET = 92

        fun parse(entry: ApfsBTreeEntry): ApfsInode {
            val block = entry.block
            val value = entry.valueOffset
            val fields = extendedFields(block, value + EXTENDED_FIELDS_OFFSET, value + entry.valueLength)
            return ApfsInode(
                objectId = ApfsKeys.objectId(block, entry.keyOffset),
                parentId = LittleEndian.u64(block, value),
                privateId = LittleEndian.u64(block, value + 8),
                createdAtNanos = LittleEndian.u64(block, value + 16),
                modifiedAtNanos = LittleEndian.u64(block, value + 24),
                childOrLinkCount = LittleEndian.u32(block, value + 56).toInt(),
                bsdFlags = LittleEndian.u32(block, value + 68),
                mode = LittleEndian.u16(block, value + 80),
                name = fields[FIELD_NAME]?.let { (offset, size) -> nullTerminatedUtf8(block, offset, size) },
                dataStreamSize = fields[FIELD_DATA_STREAM]?.let { (offset, _) -> LittleEndian.u64(block, offset) },
            )
        }

        private fun extendedFields(block: ByteArray, start: Int, end: Int): Map<Int, Pair<Int, Int>> {
            if (start + 4 > end) return emptyMap()
            val count = LittleEndian.u16(block, start)
            var dataOffset = start + 4 + count * 4
            val fields = HashMap<Int, Pair<Int, Int>>()
            for (index in 0 until count) {
                val descriptor = start + 4 + index * 4
                val type = LittleEndian.u8(block, descriptor)
                val size = LittleEndian.u16(block, descriptor + 2)
                if (dataOffset + size > end) break
                fields[type] = dataOffset to size
                dataOffset += (size + 7) and 7.inv()
            }
            return fields
        }
    }
}

class ApfsDirectoryRecord(val name: String, val fileId: Long) {
    companion object {
        fun parse(entry: ApfsBTreeEntry): ApfsDirectoryRecord {
            val block = entry.block
            val hashedNameLength = LittleEndian.u32(block, entry.keyOffset + 8).toInt() and 0x3FF
            val (nameStart, nameLength) = if (12 + hashedNameLength == entry.keyLength) {
                entry.keyOffset + 12 to hashedNameLength
            } else {
                entry.keyOffset + 10 to LittleEndian.u16(block, entry.keyOffset + 8)
            }
            return ApfsDirectoryRecord(
                name = nullTerminatedUtf8(block, nameStart, nameLength),
                fileId = LittleEndian.u64(block, entry.valueOffset),
            )
        }
    }
}

sealed interface ApfsXattrValue {
    class Embedded(val bytes: ByteArray) : ApfsXattrValue
    class Stream(val objectId: Long, val size: Long) : ApfsXattrValue
}

object ApfsXattrs {
    private const val DATA_STREAM_FLAG = 0x1

    fun name(entry: ApfsBTreeEntry): String =
        nullTerminatedUtf8(entry.block, entry.keyOffset + 10, LittleEndian.u16(entry.block, entry.keyOffset + 8))

    fun value(entry: ApfsBTreeEntry): ApfsXattrValue {
        val block = entry.block
        val flags = LittleEndian.u16(block, entry.valueOffset)
        val dataOffset = entry.valueOffset + 4
        if (flags and DATA_STREAM_FLAG != 0) {
            return ApfsXattrValue.Stream(LittleEndian.u64(block, dataOffset), LittleEndian.u64(block, dataOffset + 8))
        }
        val length = LittleEndian.u16(block, entry.valueOffset + 2)
        return ApfsXattrValue.Embedded(block.copyOfRange(dataOffset, dataOffset + length))
    }
}

fun nullTerminatedUtf8(block: ByteArray, offset: Int, maxLength: Int): String {
    val end = (offset until offset + maxLength).firstOrNull { block[it].toInt() == 0 } ?: (offset + maxLength)
    return String(block, offset, end - offset, Charsets.UTF_8)
}
