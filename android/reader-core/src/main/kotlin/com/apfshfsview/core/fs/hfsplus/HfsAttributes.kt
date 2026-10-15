package com.apfshfsview.core.fs.hfsplus

import com.apfshfsview.core.binary.BigEndian

class HfsAttributes(private val tree: HfsBTree) {
    fun inlineAttribute(fileId: Long, name: String): ByteArray? =
        tree.recordsFrom { node, index -> compareToFileStart(node, index, fileId) }
            .takeWhile { fileIdOf(it) == fileId }
            .firstOrNull { attributeName(it) == name }
            ?.let(::inlineData)

    private fun fileIdOf(position: HfsRecordPosition): Long =
        BigEndian.u32(position.node.bytes, position.node.recordOffset(position.index) + 4)

    private fun attributeName(position: HfsRecordPosition): String {
        val keyOffset = position.node.recordOffset(position.index)
        val nameLength = BigEndian.u16(position.node.bytes, keyOffset + 12)
        return String(position.node.bytes, keyOffset + 14, nameLength * 2, Charsets.UTF_16BE)
    }

    private fun inlineData(position: HfsRecordPosition): ByteArray? {
        val bytes = position.node.bytes
        val dataOffset = position.node.dataOffset(position.index)
        if (BigEndian.u32(bytes, dataOffset) != INLINE_DATA_RECORD) return null
        val size = BigEndian.u32(bytes, dataOffset + 12).toInt()
        return bytes.copyOfRange(dataOffset + 16, dataOffset + 16 + size)
    }

    private fun compareToFileStart(node: HfsBTreeNode, index: Int, fileId: Long): Int {
        val keyOffset = node.recordOffset(index)
        val recordFileId = BigEndian.u32(node.bytes, keyOffset + 4)
        if (recordFileId != fileId) return recordFileId.compareTo(fileId)
        val isEmptyName = BigEndian.u16(node.bytes, keyOffset + 12) == 0
        return if (isEmptyName && BigEndian.u32(node.bytes, keyOffset + 8) == 0L) 0 else 1
    }

    private companion object {
        const val INLINE_DATA_RECORD = 0x10L
    }
}
