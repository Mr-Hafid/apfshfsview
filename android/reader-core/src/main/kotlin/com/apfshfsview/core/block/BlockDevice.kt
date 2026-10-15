package com.apfshfsview.core.block

import java.io.IOException

interface BlockDevice {
    val sectorSize: Int
    val sizeBytes: Long

    fun read(byteOffset: Long, destination: ByteArray, destinationOffset: Int, length: Int)
}

fun BlockDevice.readBytes(byteOffset: Long, length: Int): ByteArray =
    ByteArray(length).also { read(byteOffset, it, 0, length) }

fun checkReadRange(byteOffset: Long, length: Int, sizeBytes: Long) {
    if (byteOffset < 0 || length < 0 || byteOffset + length > sizeBytes) {
        throw IOException("Read out of range: offset=$byteOffset length=$length size=$sizeBytes")
    }
}
