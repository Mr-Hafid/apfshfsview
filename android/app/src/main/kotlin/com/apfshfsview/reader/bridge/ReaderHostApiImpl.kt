package com.apfshfsview.reader.bridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.apfshfsview.core.fs.readUpTo
import com.apfshfsview.reader.export.CopyForegroundService
import com.apfshfsview.reader.export.CopyJobs
import com.apfshfsview.reader.export.CopyProgress
import com.apfshfsview.reader.export.DestinationPicker
import com.apfshfsview.reader.export.DestinationStore
import com.apfshfsview.reader.media.PlayerRegistry
import com.apfshfsview.reader.preview.PreviewCache
import com.apfshfsview.reader.preview.ThumbnailRenderer
import com.apfshfsview.reader.session.ReaderSessions
import com.apfshfsview.reader.usb.UsbDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReaderHostApiImpl(
    private val activity: Activity,
    private val events: ReaderEventsApi,
    private val destinationPicker: DestinationPicker,
) : ReaderHostApi {
    private val context = activity.applicationContext
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val usb = UsbDeviceManager(context)
    private val previewCache = PreviewCache(context)
    private val destinations = DestinationStore(context)
    val players = PlayerRegistry(context) { state -> emit { events.onPlayerState(state) } }
    private val copyListener: (CopyProgress) -> Unit = { progress -> emit { events.onCopyProgress(progress.toMessage()) } }

    init {
        CopyJobs.addListener(copyListener)
        usb.registerAttachmentListener(
            onAttached = { device -> emit { events.onDeviceAttached(usb.toMessage(device)) } },
            onDetached = { device -> handleDetached(device.deviceId) },
        )
    }

    private fun emit(block: suspend () -> Unit) {
        eventScope.launch { runCatching { block() } }
    }

    private fun handleDetached(deviceId: Int) {
        closeDeviceSessions(deviceId)
        emit { events.onDeviceDetached(deviceId.toLong()) }
    }

    private fun closeDeviceSessions(deviceId: Int) {
        val volumeHandles = ReaderSessions.volumeHandlesOf(deviceId)
        CopyJobs.cancelForVolumes(volumeHandles)
        players.disposeForVolumes(volumeHandles)
        ReaderSessions.closeDevice(deviceId)
        previewCache.clear()
    }

    fun onAttachedIntent(intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
        emit { events.onDeviceAttached(usb.toMessage(device)) }
    }

    override fun listDevices(): List<UsbDeviceMessage> = bridged { usb.massStorageDevices().map(usb::toMessage) }

    override suspend fun requestPermission(deviceId: Long): Boolean {
        val device = usb.findDevice(deviceId.toInt()) ?: return false
        return usb.requestPermission(device)
    }

    override suspend fun openDevice(deviceId: Long): DeviceOpenResult {
        closeDeviceSessions(deviceId.toInt())
        return onIoThread { DeviceOpener.open(usb, deviceId.toInt()) }
    }

    override fun closeDevice(deviceId: Long) = closeDeviceSessions(deviceId.toInt())

    override fun rootNodeId(volumeHandle: Long): Long = bridged { ReaderSessions.fileSystem(volumeHandle).rootId }

    override suspend fun listDirectory(volumeHandle: Long, nodeId: Long): List<FileEntryMessage> = onIoThread {
        ReaderSessions.fileSystem(volumeHandle).listDirectory(nodeId).map { it.toMessage() }
    }

    override suspend fun stat(volumeHandle: Long, nodeId: Long): FileEntryMessage = onIoThread {
        ReaderSessions.fileSystem(volumeHandle).stat(nodeId).toMessage()
    }

    override suspend fun readBytes(volumeHandle: Long, nodeId: Long, offset: Long, length: Long): ByteArray = onIoThread {
        ReaderSessions.fileSystem(volumeHandle).openReader(nodeId).use { reader ->
            reader.readUpTo(offset, length.coerceIn(0, MAX_READ_BYTES).toInt())
        }
    }

    override suspend fun thumbnail(volumeHandle: Long, nodeId: Long, maxDimension: Long): ByteArray? = onIoThread {
        ThumbnailRenderer.render(ReaderSessions.fileSystem(volumeHandle), nodeId, maxDimension.toInt())
    }

    override suspend fun exportToPreviewCache(volumeHandle: Long, nodeId: Long): String = onIoThread {
        previewCache.export(ReaderSessions.fileSystem(volumeHandle), volumeHandle, nodeId)
    }

    override fun savedDestinationName(): String? = bridged { destinations.displayName() }

    override suspend fun pickDestination(): String? {
        val uri = destinationPicker.pick() ?: return null
        destinations.treeUri = uri
        return destinations.displayName()
    }

    override suspend fun startCopy(volumeHandle: Long, nodeIds: List<Long>): Long {
        val destination = destinations.treeUri ?: throw FlutterError("no_destination", "Folder tujuan belum dipilih")
        bridged { ReaderSessions.fileSystem(volumeHandle) }
        requestNotificationPermission()
        val job = CopyJobs.enqueue(volumeHandle, nodeIds, destination, destinations.displayName().orEmpty())
        ContextCompat.startForegroundService(context, Intent(context, CopyForegroundService::class.java))
        return job.jobId
    }

    override fun cancelCopy(jobId: Long) = CopyJobs.cancel(jobId)

    override fun createPlayer(volumeHandle: Long, nodeId: Long): Long = bridged {
        players.create(volumeHandle, ReaderSessions.fileSystem(volumeHandle), nodeId)
    }

    override fun playerPlay(playerId: Long) = players.play(playerId)

    override fun playerPause(playerId: Long) = players.pause(playerId)

    override fun playerSeekTo(playerId: Long, positionMillis: Long) = players.seekTo(playerId, positionMillis)

    override fun disposePlayer(playerId: Long) = players.dispose(playerId)

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }

    fun dispose() {
        CopyJobs.removeListener(copyListener)
        usb.unregisterAttachmentListener()
        players.disposeAll()
        eventScope.cancel()
    }

    private companion object {
        const val MAX_READ_BYTES = 16L * 1024 * 1024
        const val NOTIFICATION_PERMISSION_REQUEST = 4109
    }
}
