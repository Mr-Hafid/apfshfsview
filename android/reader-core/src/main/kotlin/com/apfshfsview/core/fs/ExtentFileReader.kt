package com.apfshfsview.core.fs

import com.apfshfsview.core.block.BlockDevice

data class PhysicalExtent(val logicalOffset: Long, val physicalOffset: Long?, val length: Long)

class ExtentFileReader(
    private val device: BlockDevice,
    extents: List<PhysicalExtent>,
    override val size: Long,
) : FileReader {
    private val sortedExtents = extents.filter { it.length > 0 }.sortedBy { it.logicalOffset }

    override fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        val readable = minOf(length.toLong(), size - position).toInt()
        var copied = 0
        while (copied < readable) {
            val current = position + copied
            val remaining = readable - copied
            val index = extentIndexAt(current)
            val extent = sortedExtents.getOrNull(index)?.takeIf { current < it.logicalOffset + it.length }
            if (extent == null) {
                val nextStart = sortedExtents.getOrNull(index + 1)?.logicalOffset ?: Long.MAX_VALUE
                val zeroCount = minOf(remaining.toLong(), nextStart - current).toInt()
                destination.fill(0, offset + copied, offset + copied + zeroCount)
                copied += zeroCount
                continue
            }
            val offsetInExtent = current - extent.logicalOffset
            val count = minOf(remaining.toLong(), extent.length - offsetInExtent).toInt()
            val physicalOffset = extent.physicalOffset
            if (physicalOffset == null) {
                destination.fill(0, offset + copied, offset + copied + count)
            } else {
                device.read(physicalOffset + offsetInExtent, destination, offset + copied, count)
            }
            copied += count
        }
        return readable
    }

    private fun extentIndexAt(position: Long): Int {
        var low = 0
        var high = sortedExtents.size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (sortedExtents[middle].logicalOffset <= position) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }
}
