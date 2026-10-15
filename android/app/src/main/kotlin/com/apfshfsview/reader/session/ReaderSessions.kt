package com.apfshfsview.reader.session

import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.reader.usb.OpenedUsbDevice

class VolumeClosedException : IllegalStateException("Volume is no longer open")

object ReaderSessions {
    private class OpenedVolume(val deviceId: Int, val fileSystem: FileSystem)

    private val devices = HashMap<Int, OpenedUsbDevice>()
    private val volumes = HashMap<Long, OpenedVolume>()
    private var nextVolumeHandle = 1L

    @Synchronized
    fun registerDevice(device: OpenedUsbDevice) {
        closeDevice(device.deviceId)
        devices[device.deviceId] = device
    }

    @Synchronized
    fun registerVolume(deviceId: Int, fileSystem: FileSystem): Long {
        val handle = nextVolumeHandle++
        volumes[handle] = OpenedVolume(deviceId, fileSystem)
        return handle
    }

    @Synchronized
    fun fileSystem(volumeHandle: Long): FileSystem = volumes[volumeHandle]?.fileSystem ?: throw VolumeClosedException()

    @Synchronized
    fun volumeHandlesOf(deviceId: Int): Set<Long> = volumes.filterValues { it.deviceId == deviceId }.keys.toSet()

    @Synchronized
    fun closeDevice(deviceId: Int) {
        volumes.entries.removeAll { it.value.deviceId == deviceId }
        devices.remove(deviceId)?.close()
    }

    @Synchronized
    fun closeAll() {
        volumes.clear()
        devices.values.forEach { it.close() }
        devices.clear()
    }
}
