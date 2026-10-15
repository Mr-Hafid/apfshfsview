package com.apfshfsview.core.fs.hfsplus

import com.apfshfsview.core.binary.BigEndian
import com.apfshfsview.core.fs.CorruptStructureException

sealed interface HfsCatalogRecord

data class HfsCatalogFolder(
    val folderId: Long,
    val valence: Long,
    val createDate: Long,
    val contentModDate: Long,
) : HfsCatalogRecord

data class HfsCatalogFile(
    val fileId: Long,
    val createDate: Long,
    val contentModDate: Long,
    val fileMode: Int,
    val ownerFlags: Int,
    val fileType: Long,
    val fileCreator: Long,
    val special: Long,
    val dataFork: HfsForkData,
    val resourceFork: HfsForkData,
) : HfsCatalogRecord {
    val isHardLink: Boolean get() = fileType == HARD_LINK_TYPE && fileCreator == HARD_LINK_CREATOR
    val isDirectoryLink: Boolean get() = fileType == DIRECTORY_LINK_TYPE && fileCreator == DIRECTORY_LINK_CREATOR
    val isSymlink: Boolean get() = fileMode and FILE_TYPE_MASK == SYMLINK_MODE
    val isCompressed: Boolean get() = ownerFlags and COMPRESSED_FLAG != 0

    private companion object {
        const val HARD_LINK_TYPE = 0x686C6E6BL
        const val HARD_LINK_CREATOR = 0x6866732BL
        const val DIRECTORY_LINK_TYPE = 0x66647270L
        const val DIRECTORY_LINK_CREATOR = 0x4D414353L
        const val FILE_TYPE_MASK = 0xF000
        const val SYMLINK_MODE = 0xA000
        const val COMPRESSED_FLAG = 0x20
    }
}

data class HfsCatalogThread(val parentId: Long, val name: String) : HfsCatalogRecord

data class HfsCatalogEntry(val parentId: Long, val name: String, val record: HfsCatalogRecord)

class HfsCatalog(private val tree: HfsBTree, private val isCaseInsensitive: Boolean) {
    fun children(folderId: Long): List<HfsCatalogEntry> =
        tree.recordsFrom { node, index -> compareToFolderStart(node, index, folderId) }
            .map(::parseEntry)
            .takeWhile { it.parentId == folderId }
            .filter { it.record !is HfsCatalogThread }
            .toList()

    fun thread(nodeId: Long): HfsCatalogThread? =
        tree.recordsFrom { node, index -> compareToFolderStart(node, index, nodeId) }
            .firstOrNull()
            ?.let(::parseEntry)
            ?.takeIf { it.parentId == nodeId && it.name.isEmpty() }
            ?.record as? HfsCatalogThread

    fun find(parentId: Long, name: String): HfsCatalogEntry? =
        tree.recordsFrom { node, index -> compareToKey(node, index, parentId, name) }
            .firstOrNull()
            ?.let(::parseEntry)
            ?.takeIf { it.parentId == parentId && compareNames(it.name, name) == 0 }

    private fun compareToFolderStart(node: HfsBTreeNode, index: Int, folderId: Long): Int {
        val keyOffset = node.recordOffset(index)
        val recordParentId = BigEndian.u32(node.bytes, keyOffset + 2)
        if (recordParentId != folderId) return recordParentId.compareTo(folderId)
        return if (BigEndian.u16(node.bytes, keyOffset + 6) == 0) 0 else 1
    }

    private fun compareToKey(node: HfsBTreeNode, index: Int, parentId: Long, name: String): Int {
        val keyOffset = node.recordOffset(index)
        val recordParentId = BigEndian.u32(node.bytes, keyOffset + 2)
        if (recordParentId != parentId) return recordParentId.compareTo(parentId)
        return compareNames(keyName(node.bytes, keyOffset), name)
    }

    private fun compareNames(left: String, right: String): Int {
        val length = minOf(left.length, right.length)
        for (position in 0 until length) {
            val difference = foldCharacter(left[position]).compareTo(foldCharacter(right[position]))
            if (difference != 0) return difference
        }
        return left.length.compareTo(right.length)
    }

    private fun foldCharacter(character: Char): Char = when {
        !isCaseInsensitive -> character
        character == '\u0000' -> '\uFFFF'
        character in 'A'..'Z' -> character + ('a' - 'A')
        else -> character
    }

    private fun keyName(bytes: ByteArray, keyOffset: Int): String =
        String(bytes, keyOffset + 8, BigEndian.u16(bytes, keyOffset + 6) * 2, Charsets.UTF_16BE)

    private fun parseEntry(position: HfsRecordPosition): HfsCatalogEntry {
        val bytes = position.node.bytes
        val keyOffset = position.node.recordOffset(position.index)
        val data = position.node.dataOffset(position.index)
        val record = when (BigEndian.u16(bytes, data)) {
            RECORD_FOLDER -> HfsCatalogFolder(
                folderId = BigEndian.u32(bytes, data + 8),
                valence = BigEndian.u32(bytes, data + 4),
                createDate = BigEndian.u32(bytes, data + 12),
                contentModDate = BigEndian.u32(bytes, data + 16),
            )
            RECORD_FILE -> HfsCatalogFile(
                fileId = BigEndian.u32(bytes, data + 8),
                createDate = BigEndian.u32(bytes, data + 12),
                contentModDate = BigEndian.u32(bytes, data + 16),
                fileMode = BigEndian.u16(bytes, data + 42),
                ownerFlags = BigEndian.u8(bytes, data + 41),
                fileType = BigEndian.u32(bytes, data + 48),
                fileCreator = BigEndian.u32(bytes, data + 52),
                special = BigEndian.u32(bytes, data + 44),
                dataFork = HfsForkData.parse(bytes, data + 88),
                resourceFork = HfsForkData.parse(bytes, data + 168),
            )
            RECORD_FOLDER_THREAD, RECORD_FILE_THREAD -> HfsCatalogThread(
                parentId = BigEndian.u32(bytes, data + 4),
                name = String(bytes, data + 10, BigEndian.u16(bytes, data + 8) * 2, Charsets.UTF_16BE),
            )
            else -> throw CorruptStructureException("Unknown HFS+ catalog record type")
        }
        return HfsCatalogEntry(BigEndian.u32(bytes, keyOffset + 2), keyName(bytes, keyOffset), record)
    }

    private companion object {
        const val RECORD_FOLDER = 1
        const val RECORD_FILE = 2
        const val RECORD_FOLDER_THREAD = 3
        const val RECORD_FILE_THREAD = 4
    }
}
