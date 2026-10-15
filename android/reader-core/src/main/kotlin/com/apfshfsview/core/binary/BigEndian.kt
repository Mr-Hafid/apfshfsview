package com.apfshfsview.core.binary

object BigEndian {
    fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    fun u16(bytes: ByteArray, offset: Int): Int = (u8(bytes, offset) shl 8) or u8(bytes, offset + 1)

    fun u32(bytes: ByteArray, offset: Int): Long =
        (u16(bytes, offset).toLong() shl 16) or u16(bytes, offset + 2).toLong()

    fun u64(bytes: ByteArray, offset: Int): Long = (u32(bytes, offset) shl 32) or u32(bytes, offset + 4)
}
