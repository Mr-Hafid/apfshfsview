package com.apfshfsview.reader.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.fs.ReadAheadFileReader
import com.apfshfsview.core.fs.readFully
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ThumbnailRenderer {
    private const val MAX_IMAGE_SOURCE_BYTES = 64L * 1024 * 1024
    private const val JPEG_QUALITY = 82
    private const val VIDEO_READ_AHEAD_BYTES = 512 * 1024
    private const val PREFERRED_FRAME_TIME_MICROS = 1_000_000L
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp", "avi")

    fun render(fileSystem: FileSystem, nodeId: Long, maxDimension: Int): ByteArray? {
        val node = fileSystem.stat(nodeId)
        if (node.kind != NodeKind.FILE || !node.isReadable || node.size <= 0) return null
        val bitmap = if (isVideo(node)) videoFrame(fileSystem, node, maxDimension) else imageBitmap(fileSystem, node, maxDimension)
        return bitmap?.let { encode(scaleDown(it, maxDimension)) }
    }

    private fun isVideo(node: FileNode): Boolean = node.name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    private fun imageBitmap(fileSystem: FileSystem, node: FileNode, maxDimension: Int): Bitmap? {
        if (node.size > MAX_IMAGE_SOURCE_BYTES) return null
        val bytes = fileSystem.openReader(node.id).use { it.readFully(0, node.size.toInt()) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) sampleSize *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null
        return applyExifOrientation(decoded, bytes)
    }

    private fun videoFrame(fileSystem: FileSystem, node: FileNode, maxDimension: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(FileReaderMediaDataSource(ReadAheadFileReader(fileSystem.openReader(node.id), VIDEO_READ_AHEAD_BYTES)))
            val durationMicros = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000)
            val frameTime = durationMicros?.let { minOf(PREFERRED_FRAME_TIME_MICROS, it / 10) } ?: 0L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDimension, maxDimension)
            } else {
                retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (error: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        output.toByteArray()
    }

    private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }
}
