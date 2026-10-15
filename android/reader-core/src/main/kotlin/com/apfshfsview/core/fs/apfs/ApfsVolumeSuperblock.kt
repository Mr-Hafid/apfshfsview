package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.CorruptStructureException

class ApfsVolumeSuperblock(block: ByteArray) {
    init {
        if (LittleEndian.u32(block, 32) != MAGIC) throw CorruptStructureException("APFS volume superblock expected")
    }

    val allocatedBlocks: Long = LittleEndian.u64(block, 88)
    val objectMapAddress: Long = LittleEndian.u64(block, 128)
    val rootTreeOid: Long = LittleEndian.u64(block, 136)
    private val fileSystemFlags: Long = LittleEndian.u64(block, 264)
    val name: String = readName(block)
    private val role: Int = LittleEndian.u16(block, 964)

    val isEncrypted: Boolean get() = fileSystemFlags and UNENCRYPTED_FLAG == 0L
    val roleLabel: String? get() = ROLE_LABELS[role]

    private fun readName(block: ByteArray): String {
        val end = (NAME_OFFSET until NAME_OFFSET + NAME_LENGTH).firstOrNull { block[it].toInt() == 0 } ?: (NAME_OFFSET + NAME_LENGTH)
        return String(block, NAME_OFFSET, end - NAME_OFFSET, Charsets.UTF_8)
    }

    private companion object {
        const val MAGIC = 0x42535041L
        const val UNENCRYPTED_FLAG = 0x1L
        const val NAME_OFFSET = 704
        const val NAME_LENGTH = 256
        val ROLE_LABELS = mapOf(
            0x1 to "System",
            0x2 to "User",
            0x4 to "Recovery",
            0x8 to "VM",
            0x10 to "Preboot",
            0x20 to "Installer",
            0x40 to "Data",
            0x80 to "Baseband",
            0xC0 to "Update",
            0x100 to "xART",
            0x140 to "Hardware",
            0x180 to "Backup",
        )
    }
}
