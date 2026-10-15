package com.apfshfsview.core.block

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

class FileBlockDevice(file: File, override val sectorSize: Int = 512) : BlockDevice, Closeable {
    private val randomAccessFile = RandomAccessFile(file, "r")

    override val sizeBytes: Long = randomAccessFile.length()

    @Synchronized
    override fun read(byteOffset: Long, destination: ByteArray, destinationOffset: Int, length: Int) {
        checkReadRange(byteOffset, length, sizeBytes)
        randomAccessFile.seek(byteOffset)
        randomAccessFile.readFully(destination, destinationOffset, length)
    }

    override fun close() = randomAccessFile.close()
}
