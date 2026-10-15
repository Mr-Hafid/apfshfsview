package com.apfshfsview.core.support

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.checkReadRange

class MemoryBlockDevice(private val bytes: ByteArray, override val sectorSize: Int = 512) : BlockDevice {
    override val sizeBytes: Long get() = bytes.size.toLong()
    var readCount = 0
        private set

    override fun read(byteOffset: Long, destination: ByteArray, destinationOffset: Int, length: Int) {
        checkReadRange(byteOffset, length, sizeBytes)
        readCount++
        System.arraycopy(bytes, byteOffset.toInt(), destination, destinationOffset, length)
    }
}
