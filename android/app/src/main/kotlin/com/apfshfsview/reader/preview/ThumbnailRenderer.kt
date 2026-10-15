package com.apfshfsview.reader.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.fs.readFully
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ThumbnailRenderer {
    private const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
    private const val JPEG_QUALITY = 82

    fun render(fileSystem: FileSystem, nodeId: Long, maxDimension: Int): ByteArray? {
        val node = fileSystem.stat(nodeId)
        if (node.kind != NodeKind.FILE || !node.isReadable || node.size <= 0 || node.size > MAX_SOURCE_BYTES) return null
        val bytes = fileSystem.openReader(nodeId).use { it.readFully(0, node.size.toInt()) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) sampleSize *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null
        val oriented = applyExifOrientation(decoded, bytes)
        val scale = maxDimension.toFloat() / max(oriented.width, oriented.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).roundToInt().coerceAtLeast(1),
                (oriented.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            oriented
        }
        return ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
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
