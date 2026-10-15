package com.apfshfsview.core.fs.decmpfs

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.CorruptStructureException

class DecmpfsHeader(
    val compressionType: Int,
    val uncompressedSize: Long,
    val payload: ByteArray,
) {
    val isSupported: Boolean get() = compressionType in SUPPORTED_TYPES

    companion object {
        const val ATTRIBUTE_NAME = "com.apple.decmpfs"
        const val RESOURCE_FORK_ATTRIBUTE_NAME = "com.apple.ResourceFork"
        const val TYPE_UNCOMPRESSED_INLINE = 1
        const val TYPE_ZLIB_INLINE = 3
        const val TYPE_ZLIB_RESOURCE_FORK = 4
        private const val MAGIC = 0x636D7066L
        private const val HEADER_SIZE = 16
        private val SUPPORTED_TYPES = setOf(TYPE_UNCOMPRESSED_INLINE, TYPE_ZLIB_INLINE, TYPE_ZLIB_RESOURCE_FORK)

        fun parse(bytes: ByteArray): DecmpfsHeader {
            if (bytes.size < HEADER_SIZE || LittleEndian.u32(bytes, 0) != MAGIC) {
                throw CorruptStructureException("Invalid decmpfs header")
            }
            return DecmpfsHeader(
                compressionType = LittleEndian.u32(bytes, 4).toInt(),
                uncompressedSize = LittleEndian.u64(bytes, 8),
                payload = bytes.copyOfRange(HEADER_SIZE, bytes.size),
            )
        }
    }
}
