package com.apfshfsview.reader.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.reader.bridge.UsbDeviceMessage
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import me.jahnen.libaums.core.usb.UsbCommunication
import me.jahnen.libaums.core.usb.UsbCommunicationFactory
import java.io.Closeable
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class UsbPermissionMissingException : IllegalStateException("USB permission missing")

class OpenedUsbDevice(
    val deviceId: Int,
    private val communication: UsbCommunication,
    val blockDevice: BlockDevice,
) : Closeable {
    override fun close() {
        runCatching { communication.close() }
    }
}

class UsbDeviceManager(private val context: Context) {
    private val usbManager = context.getSystemService(UsbManager::class.java)
    private var attachmentReceiver: BroadcastReceiver? = null

    fun massStorageDevices(): List<UsbDevice> =
        usbManager.deviceList.values.filter { massStorageInterface(it) != null }.sortedBy { it.deviceId }

    fun findDevice(deviceId: Int): UsbDevice? = massStorageDevices().firstOrNull { it.deviceId == deviceId }

    fun toMessage(device: UsbDevice): UsbDeviceMessage = UsbDeviceMessage(
        deviceId = device.deviceId.toLong(),
        productName = runCatching { device.productName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "USB Storage",
        manufacturerName = runCatching { device.manufacturerName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Tidak diketahui",
        hasPermission = usbManager.hasPermission(device),
    )

    suspend fun requestPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true
        return suspendCoroutine { continuation ->
            val action = "$PERMISSION_ACTION_PREFIX.${device.deviceId}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    context.unregisterReceiver(this)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    continuation.resume(granted || usbManager.hasPermission(device))
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                device.deviceId,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
            )
            usbManager.requestPermission(device, pendingIntent)
        }
    }

    fun open(device: UsbDevice): OpenedUsbDevice {
        if (!usbManager.hasPermission(device)) throw UsbPermissionMissingException()
        val usbInterface = massStorageInterface(device) ?: throw IOException("Perangkat bukan USB mass storage")
        val bulkEndpoints = (0 until usbInterface.endpointCount)
            .map { usbInterface.getEndpoint(it) }
            .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val inEndpoint = bulkEndpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
            ?: throw IOException("Endpoint bulk IN tidak ditemukan")
        val outEndpoint = bulkEndpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
            ?: throw IOException("Endpoint bulk OUT tidak ditemukan")
        val communication = UsbCommunicationFactory.createUsbCommunication(usbManager, device, usbInterface, outEndpoint, inEndpoint)
        try {
            val driver = BlockDeviceDriverFactory.createBlockDevice(communication, lun = 0)
            driver.init()
            return OpenedUsbDevice(device.deviceId, communication, LibaumsBlockDevice(driver))
        } catch (error: Exception) {
            runCatching { communication.close() }
            throw error
        }
    }

    fun registerAttachmentListener(onAttached: (UsbDevice) -> Unit, onDetached: (UsbDevice) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (massStorageInterface(device) != null) onAttached(device)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> onDetached(device)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        attachmentReceiver = receiver
    }

    fun unregisterAttachmentListener() {
        attachmentReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        attachmentReceiver = null
    }

    private fun massStorageInterface(device: UsbDevice): UsbInterface? =
        (0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull {
            it.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE &&
                it.interfaceSubclass == SCSI_TRANSPARENT_SUBCLASS &&
                it.interfaceProtocol == BULK_ONLY_PROTOCOL
        }

    private companion object {
        const val PERMISSION_ACTION_PREFIX = "com.apfshfsview.reader.USB_PERMISSION"
        const val SCSI_TRANSPARENT_SUBCLASS = 6
        const val BULK_ONLY_PROTOCOL = 80
    }
}
