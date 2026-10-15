package com.apfshfsview.reader.bridge

import com.apfshfsview.core.fs.ReaderException
import com.apfshfsview.reader.session.VolumeClosedException
import com.apfshfsview.reader.usb.UsbPermissionMissingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

fun Throwable.toFlutterError(): FlutterError = when (this) {
    is FlutterError -> this
    is ReaderException -> FlutterError(code, message)
    is VolumeClosedException -> FlutterError("usb_detached", message)
    is UsbPermissionMissingException -> FlutterError("permission_denied", message)
    is IOException -> FlutterError("io_error", message)
    is IndexOutOfBoundsException -> FlutterError("corrupt_structure", message)
    else -> FlutterError("unknown", message ?: javaClass.simpleName)
}

suspend fun <T> onIoThread(block: () -> T): T =
    try {
        withContext(Dispatchers.IO) { block() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        throw error.toFlutterError()
    }

inline fun <T> bridged(block: () -> T): T =
    try {
        block()
    } catch (error: Throwable) {
        throw error.toFlutterError()
    }
