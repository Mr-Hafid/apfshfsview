package com.apfshfsview.core.fs.hfsplus

import com.apfshfsview.core.binary.BigEndian
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.FileReader
import com.apfshfsview.core.fs.readFully

class HfsBTreeNode(val bytes: ByteArray) {
    val forwardLink: Long = BigEndian.u32(bytes, 0)
    val kind: Int = bytes[8].toInt()
    val recordCount: Int = BigEndian.u16(bytes, 10)

    fun recordOffset(index: Int): Int {
        val offset = BigEndian.u16(bytes, bytes.size - 2 * (index + 1))
        if (offset < NODE_DESCRIPTOR_SIZE || offset >= bytes.size) {
            throw CorruptStructureException("Invalid HFS+ record offset $offset")
        }
        return offset
    }

    fun keyLength(index: Int): Int = BigEndian.u16(bytes, recordOffset(index))

    fun dataOffset(index: Int): Int {
        val offset = recordOffset(index) + 2 + keyLength(index)
        return offset + (offset and 1)
    }

    fun childPointer(index: Int): Long = BigEndian.u32(bytes, dataOffset(index))

    companion object {
        const val KIND_LEAF = -1
        const val KIND_INDEX = 0
        private const val NODE_DESCRIPTOR_SIZE = 14
    }
}

data class HfsRecordPosition(val node: HfsBTreeNode, val index: Int)

class HfsBTree(private val file: FileReader) {
    private val nodeSize: Int
    private val rootNode: Long
    private val treeDepth: Int

    init {
        if (file.size < HEADER_READ_LENGTH) {
            nodeSize = HEADER_READ_LENGTH
            rootNode = 0
            treeDepth = 0
        } else {
            val header = file.readFully(0, HEADER_READ_LENGTH)
            treeDepth = BigEndian.u16(header, HEADER_RECORD_OFFSET)
            rootNode = BigEndian.u32(header, HEADER_RECORD_OFFSET + 2)
            nodeSize = BigEndian.u16(header, HEADER_RECORD_OFFSET + 18)
            if (nodeSize < 512 || nodeSize and (nodeSize - 1) != 0) {
                throw CorruptStructureException("Invalid HFS+ node size $nodeSize")
            }
        }
    }

    private fun node(nodeNumber: Long): HfsBTreeNode =
        HfsBTreeNode(file.readFully(nodeNumber * nodeSize, nodeSize))

    fun recordsFrom(compareRecordToTarget: (HfsBTreeNode, Int) -> Int): Sequence<HfsRecordPosition> = sequence {
        if (treeDepth == 0 || rootNode == 0L) return@sequence
        var current = node(rootNode)
        var depth = 0
        while (current.kind == HfsBTreeNode.KIND_INDEX) {
            if (++depth > MAX_DEPTH) throw CorruptStructureException("HFS+ B-tree is too deep")
            var chosen = 0
            for (index in 0 until current.recordCount) {
                if (compareRecordToTarget(current, index) <= 0) chosen = index else break
            }
            current = node(current.childPointer(chosen))
        }
        if (current.kind != HfsBTreeNode.KIND_LEAF) throw CorruptStructureException("HFS+ leaf node expected")
        var startIndex = (0 until current.recordCount).firstOrNull { compareRecordToTarget(current, it) >= 0 }
            ?: current.recordCount
        var visitedNodes = 0
        while (true) {
            for (index in startIndex until current.recordCount) yield(HfsRecordPosition(current, index))
            if (current.forwardLink == 0L) break
            if (++visitedNodes > MAX_LEAF_WALK) throw CorruptStructureException("HFS+ leaf chain is too long")
            current = node(current.forwardLink)
            if (current.kind != HfsBTreeNode.KIND_LEAF) throw CorruptStructureException("HFS+ leaf chain is broken")
            startIndex = 0
        }
    }

    private companion object {
        const val HEADER_READ_LENGTH = 512
        const val HEADER_RECORD_OFFSET = 14
        const val MAX_DEPTH = 32
        const val MAX_LEAF_WALK = 50_000_000
    }
}
