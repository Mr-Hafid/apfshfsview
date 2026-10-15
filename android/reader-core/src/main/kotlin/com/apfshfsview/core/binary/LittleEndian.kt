package com.apfshfsview.core.binary

object LittleEndian {
    fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    fun u16(bytes: ByteArray, offset: Int): Int = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)

    fun u64(bytes: ByteArray, offset: Int): Long = u32(bytes, offset) or (u32(bytes, offset + 4) shl 32)
}
