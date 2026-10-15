package com.apfshfsview.core.block

class PartitionBlockDevice(
    private val inner: BlockDevice,
    private val startByte: Long,
    override val sizeBytes: Long,
) : BlockDevice {
    override val sectorSize: Int get() = inner.sectorSize

    override fun read(byteOffset: Long, destination: ByteArray, destinationOffset: Int, length: Int) {
        checkReadRange(byteOffset, length, sizeBytes)
        inner.read(startByte + byteOffset, destination, destinationOffset, length)
    }
}
