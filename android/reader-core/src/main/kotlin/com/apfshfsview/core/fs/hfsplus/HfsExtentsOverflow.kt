package com.apfshfsview.core.fs.hfsplus

import com.apfshfsview.core.binary.BigEndian

class HfsExtentsOverflow(private val tree: HfsBTree) {
    fun extents(fileId: Long, forkType: Int, startBlock: Long): List<HfsExtent> =
        tree.recordsFrom { node, index -> compare(node, index, fileId, forkType, startBlock) }
            .takeWhile { position -> fileIdOf(position) == fileId && forkTypeOf(position) == forkType }
            .flatMap { position -> HfsForkData.parseExtentRecord(position.node.bytes, position.node.dataOffset(position.index)) }
            .toList()

    private fun fileIdOf(position: HfsRecordPosition): Long =
        BigEndian.u32(position.node.bytes, position.node.recordOffset(position.index) + 4)

    private fun forkTypeOf(position: HfsRecordPosition): Int =
        BigEndian.u8(position.node.bytes, position.node.recordOffset(position.index) + 2)

    private fun compare(node: HfsBTreeNode, index: Int, fileId: Long, forkType: Int, startBlock: Long): Int {
        val keyOffset = node.recordOffset(index)
        val recordFileId = BigEndian.u32(node.bytes, keyOffset + 4)
        if (recordFileId != fileId) return recordFileId.compareTo(fileId)
        val recordForkType = BigEndian.u8(node.bytes, keyOffset + 2)
        if (recordForkType != forkType) return recordForkType.compareTo(forkType)
        return BigEndian.u32(node.bytes, keyOffset + 8).compareTo(startBlock)
    }
}
