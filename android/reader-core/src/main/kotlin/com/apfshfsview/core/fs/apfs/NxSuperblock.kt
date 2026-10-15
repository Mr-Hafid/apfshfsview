package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.binary.LittleEndian

class NxSuperblock(val block: ByteArray) {
    val transactionId: Long = LittleEndian.u64(block, 16)
    val blockSize: Int = LittleEndian.u32(block, 36).toInt()
    val blockCount: Long = LittleEndian.u64(block, 40)
    val checkpointDescriptorBlocks: Long = LittleEndian.u32(block, 104)
    val checkpointDescriptorBase: Long = LittleEndian.u64(block, 112)
    val objectMapAddress: Long = LittleEndian.u64(block, 160)
    val fileSystemOids: List<Long> =
        (0 until minOf(LittleEndian.u32(block, 180).toInt(), MAX_FILE_SYSTEMS))
            .map { LittleEndian.u64(block, 184 + it * 8) }
            .filter { it != 0L }

    companion object {
        const val OBJECT_TYPE = 0x1
        private const val MAGIC = 0x4253584EL
        private const val MAX_FILE_SYSTEMS = 100

        fun hasMagic(block: ByteArray): Boolean = block.size >= 40 && LittleEndian.u32(block, 32) == MAGIC

        fun blockSizeOf(header: ByteArray): Int = LittleEndian.u32(header, 36).toInt()
    }
}
