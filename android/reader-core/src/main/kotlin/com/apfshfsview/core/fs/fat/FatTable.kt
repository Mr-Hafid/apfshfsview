package com.apfshfsview.core.fs.fat

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.readBytes
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.VolumeFormat

class FatTable(private val device: BlockDevice, private val boot: FatBootSector) {
    private val format = boot.format
    private val endOfChain: Long = when (format) {
        VolumeFormat.FAT12 -> 0xFF8L
        VolumeFormat.FAT16 -> 0xFFF8L
        else -> 0x0FFFFFF8L
    }

    fun next(cluster: Long): Long = when (format) {
        VolumeFormat.FAT12 -> {
            val bytes = device.readBytes(boot.fatStartByte + cluster + cluster / 2, 2)
            val value = LittleEndian.u16(bytes, 0)
            (if (cluster % 2 == 0L) value and 0x0FFF else value ushr 4).toLong()
        }
        VolumeFormat.FAT16 -> LittleEndian.u16(device.readBytes(boot.fatStartByte + cluster * 2, 2), 0).toLong()
        else -> LittleEndian.u32(device.readBytes(boot.fatStartByte + cluster * 4, 4), 0) and 0x0FFFFFFF
    }

    fun chain(firstCluster: Long): List<Long> {
        if (firstCluster < 2) return emptyList()
        val clusters = mutableListOf<Long>()
        var cluster = firstCluster
        while (cluster in 2..(boot.clusterCount + 1)) {
            clusters += cluster
            if (clusters.size > boot.clusterCount) throw CorruptStructureException("FAT chain loops")
            val next = next(cluster)
            if (next >= endOfChain) break
            cluster = next
        }
        return clusters
    }

    fun countFreeClusters(): Long {
        var free = 0L
        for (cluster in 2..(boot.clusterCount + 1)) if (next(cluster) == 0L) free++
        return free
    }
}
