package com.apfshfsview.core.fs.exfat

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.CorruptStructureException

class ExfatBootSector(sector: ByteArray) {
    private val bytesPerSector: Long = 1L shl LittleEndian.u8(sector, 108)
    val clusterSize: Int = (bytesPerSector shl LittleEndian.u8(sector, 109)).toInt()
    val fatStartByte: Long = LittleEndian.u32(sector, 80) * bytesPerSector
    private val clusterHeapStartByte: Long = LittleEndian.u32(sector, 88) * bytesPerSector
    val clusterCount: Long = LittleEndian.u32(sector, 92)
    val rootCluster: Long = LittleEndian.u32(sector, 96)
    private val percentInUse: Int = LittleEndian.u8(sector, 112)

    val totalBytes: Long get() = clusterCount * clusterSize
    val reportedUsedBytes: Long? get() = if (percentInUse in 0..100) totalBytes * percentInUse / 100 else null

    init {
        if (clusterSize <= 0 || clusterSize > MAX_CLUSTER_SIZE || clusterCount <= 0 || rootCluster < 2) {
            throw CorruptStructureException("Invalid exFAT boot sector")
        }
    }

    fun clusterOffset(cluster: Long): Long = clusterHeapStartByte + (cluster - 2) * clusterSize

    companion object {
        private const val MAX_CLUSTER_SIZE = 32 * 1024 * 1024

        fun matches(sector: ByteArray): Boolean =
            sector.size >= 512 && String(sector, 3, 8, Charsets.ISO_8859_1) == "EXFAT   "
    }
}
