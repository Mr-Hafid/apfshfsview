package com.apfshfsview.reader.usb

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.SectorReadPlanner
import com.apfshfsview.core.block.checkReadRange
import com.apfshfsview.core.fs.DeviceNotRespondingException
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import java.io.IOException
import java.nio.ByteBuffer

class LibaumsBlockDevice(private val driver: BlockDeviceDriver) : BlockDevice {
    override val sectorSize: Int = driver.blockSize
    override val sizeBytes: Long = driver.blocks * driver.blockSize

    private val transferBuffer = ByteBuffer.allocate(MAX_TRANSFER_BYTES)

    @Synchronized
    override fun read(byteOffset: Long, destination: ByteArray, destinationOffset: Int, length: Int) {
        checkReadRange(byteOffset, length, sizeBytes)
        var written = 0
        for (plannedRead in SectorReadPlanner.plan(byteOffset, length, sectorSize, MAX_TRANSFER_BYTES)) {
            transferBuffer.clear()
            transferBuffer.limit(plannedRead.sectorCount * sectorSize)
            readWithRetry(plannedRead.firstSector)
            System.arraycopy(transferBuffer.array(), plannedRead.skipBytes, destination, destinationOffset + written, plannedRead.copyBytes)
            written += plannedRead.copyBytes
        }
    }

    private fun readWithRetry(sector: Long) {
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) {
            try {
                transferBuffer.position(0)
                driver.read(sector, transferBuffer)
                return
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw DeviceNotRespondingException("USB read failed at sector $sector: ${lastError?.message}")
    }

    private companion object {
        const val MAX_TRANSFER_BYTES = 64 * 1024
        const val MAX_ATTEMPTS = 3
    }
}
