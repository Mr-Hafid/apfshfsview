package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.CorruptStructureException

class ApfsBTreeEntry(
    val block: ByteArray,
    val keyOffset: Int,
    val keyLength: Int,
    val valueOffset: Int,
    val valueLength: Int,
)

class ApfsBTreeNode(val block: ByteArray, private val fixedKeySize: Int, private val fixedValueSize: Int) {
    private val flags = LittleEndian.u16(block, 32)
    val keyCount: Int = LittleEndian.u32(block, 36).toInt()
    private val tableOffset = HEADER_SIZE + LittleEndian.u16(block, 40)
    private val keyAreaStart = tableOffset + LittleEndian.u16(block, 42)
    val isLeaf: Boolean = flags and FLAG_LEAF != 0
    private val isRoot = flags and FLAG_ROOT != 0
    private val isFixed = flags and FLAG_FIXED != 0
    private val valueAreaEnd = block.size - if (isRoot) BTREE_INFO_SIZE else 0

    init {
        if (keyCount < 0 || tableOffset + keyCount * entrySize() > block.size) {
            throw CorruptStructureException("Invalid APFS B-tree node")
        }
    }

    private fun entrySize(): Int = if (isFixed) 4 else 8

    private fun tableEntry(index: Int): Int = tableOffset + index * entrySize()

    fun keyOffset(index: Int): Int = keyAreaStart + LittleEndian.u16(block, tableEntry(index))

    fun keyLength(index: Int): Int = if (isFixed) fixedKeySize else LittleEndian.u16(block, tableEntry(index) + 2)

    fun valueOffset(index: Int): Int =
        valueAreaEnd - LittleEndian.u16(block, tableEntry(index) + if (isFixed) 2 else 4)

    fun valueLength(index: Int): Int = when {
        !isFixed -> LittleEndian.u16(block, tableEntry(index) + 6)
        isLeaf -> fixedValueSize
        else -> CHILD_POINTER_SIZE
    }

    fun childOid(index: Int): Long = LittleEndian.u64(block, valueOffset(index))

    fun entry(index: Int): ApfsBTreeEntry =
        ApfsBTreeEntry(block, keyOffset(index), keyLength(index), valueOffset(index), valueLength(index))

    companion object {
        const val BTREE_INFO_SIZE = 40
        private const val HEADER_SIZE = 56
        private const val FLAG_ROOT = 0x1
        private const val FLAG_LEAF = 0x2
        private const val FLAG_FIXED = 0x4
        private const val CHILD_POINTER_SIZE = 8
    }
}

class ApfsBTree(
    private val container: ApfsContainer,
    rootAddress: Long,
    private val resolveChildAddress: (Long) -> Long,
) {
    private val fixedKeySize: Int
    private val fixedValueSize: Int
    private val root: ApfsBTreeNode

    init {
        val block = container.readObject(rootAddress)
        val infoOffset = block.size - ApfsBTreeNode.BTREE_INFO_SIZE
        fixedKeySize = LittleEndian.u32(block, infoOffset + 8).toInt()
        fixedValueSize = LittleEndian.u32(block, infoOffset + 12).toInt()
        root = ApfsBTreeNode(block, fixedKeySize, fixedValueSize)
    }

    fun entriesFrom(compareKeyToTarget: (ByteArray, Int, Int) -> Int): Sequence<ApfsBTreeEntry> =
        walk(root, compareKeyToTarget, 0)

    private fun walk(node: ApfsBTreeNode, compare: (ByteArray, Int, Int) -> Int, depth: Int): Sequence<ApfsBTreeEntry> =
        sequence {
            if (depth > MAX_DEPTH) throw CorruptStructureException("APFS B-tree is too deep")
            if (node.isLeaf) {
                for (index in 0 until node.keyCount) {
                    if (compare(node.block, node.keyOffset(index), node.keyLength(index)) >= 0) yield(node.entry(index))
                }
                return@sequence
            }
            var start = 0
            for (index in 0 until node.keyCount) {
                if (compare(node.block, node.keyOffset(index), node.keyLength(index)) <= 0) start = index else break
            }
            for (index in start until node.keyCount) {
                yieldAll(walk(child(node.childOid(index)), compare, depth + 1))
            }
        }

    private fun child(oid: Long): ApfsBTreeNode =
        ApfsBTreeNode(container.readObject(resolveChildAddress(oid)), fixedKeySize, fixedValueSize)

    private companion object {
        const val MAX_DEPTH = 16
    }
}
