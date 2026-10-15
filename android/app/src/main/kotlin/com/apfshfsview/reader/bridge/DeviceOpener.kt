package com.apfshfsview.reader.bridge

import com.apfshfsview.core.block.CachedBlockDevice
import com.apfshfsview.core.volume.VolumeProbe
import com.apfshfsview.reader.session.ReaderSessions
import com.apfshfsview.reader.session.VolumeClosedException
import com.apfshfsview.reader.usb.UsbDeviceManager

object DeviceOpener {
    fun open(usb: UsbDeviceManager, deviceId: Int): DeviceOpenResult {
        val usbDevice = usb.findDevice(deviceId) ?: throw VolumeClosedException()
        val opened = usb.open(usbDevice)
        ReaderSessions.registerDevice(opened)
        try {
            val probe = VolumeProbe.probe(CachedBlockDevice(opened.blockDevice))
            val volumes = probe.volumes.map { volume ->
                val handle = volume.fileSystem?.let { ReaderSessions.registerVolume(deviceId, it) }
                volume.toMessage(handle)
            }
            return DeviceOpenResult(
                capacityBytes = opened.blockDevice.sizeBytes,
                volumes = volumes,
                unrecognizedPartitionTypes = probe.unrecognizedPartitionTypes,
            )
        } catch (error: Exception) {
            ReaderSessions.closeDevice(deviceId)
            throw error
        }
    }
}
