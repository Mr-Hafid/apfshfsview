package com.apfshfsview.core.fs.fat

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.VolumeFormat

class FatBootSector(sector: ByteArray) {
    val bytesPerSector: Int = LittleEndian.u16(sector, 11)
    val sectorsPerCluster: Int = LittleEndian.u8(sector, 13)
    private val reservedSectors: Int = LittleEndian.u16(sector, 14)
    private val fatCount: Int = LittleEndian.u8(sector, 16)
    private val rootEntryCount: Int = LittleEndian.u16(sector, 17)
    private val totalSectors: Long = LittleEndian.u16(sector, 19).toLong().takeIf { it != 0L } ?: LittleEndian.u32(sector, 32)
    private val fatSizeSectors: Long = LittleEndian.u16(sector, 22).toLong().takeIf { it != 0L } ?: LittleEndian.u32(sector, 36)
    val rootCluster: Long = LittleEndian.u32(sector, 44)
    val fsInfoSector: Int = LittleEndian.u16(sector, 48)

    val clusterSize: Int get() = bytesPerSector * sectorsPerCluster
    val fatStartByte: Long get() = reservedSectors.toLong() * bytesPerSector
    val rootDirectoryStartByte: Long get() = (reservedSectors + fatCount * fatSizeSectors) * bytesPerSector
    val rootDirectoryBytes: Long get() = rootEntryCount * DIRECTORY_ENTRY_SIZE.toLong()
    val dataStartByte: Long get() = rootDirectoryStartByte + roundUp(rootDirectoryBytes, bytesPerSector.toLong())
    val clusterCount: Long get() = (totalSectors * bytesPerSector - dataStartByte) / clusterSize
    val totalBytes: Long get() = clusterCount * clusterSize
    val format: VolumeFormat
        get() = when {
            clusterCount < 4085 -> VolumeFormat.FAT12
            clusterCount < 65525 -> VolumeFormat.FAT16
            else -> VolumeFormat.FAT32
        }
    val bootLabel: String = labelAt(sector, if (LittleEndian.u16(sector, 22) == 0) 71 else 43)

    init {
        if (sectorsPerCluster == 0 || fatCount == 0 || fatSizeSectors == 0L || clusterCount <= 0) {
            throw CorruptStructureException("Invalid FAT boot sector")
        }
    }

    fun clusterOffset(cluster: Long): Long = dataStartByte + (cluster - 2) * clusterSize

    private fun labelAt(sector: ByteArray, offset: Int): String =
        String(sector, offset, 11, Charsets.ISO_8859_1).trimEnd(' ', '\u0000')

    private fun roundUp(value: Long, unit: Long): Long = (value + unit - 1) / unit * unit

    companion object {
        const val DIRECTORY_ENTRY_SIZE = 32

        fun matches(sector: ByteArray): Boolean {
            if (sector.size < 512) return false
            val jump = LittleEndian.u8(sector, 0)
            if (jump != 0xEB && jump != 0xE9) return false
            val oem = String(sector, 3, 8, Charsets.ISO_8859_1)
            if (oem == "EXFAT   " || oem == "NTFS    ") return false
            val bytesPerSector = LittleEndian.u16(sector, 11)
            val sectorsPerCluster = LittleEndian.u8(sector, 13)
            val fatCount = LittleEndian.u8(sector, 16)
            return bytesPerSector in setOf(512, 1024, 2048, 4096) &&
                sectorsPerCluster != 0 && sectorsPerCluster and (sectorsPerCluster - 1) == 0 &&
                LittleEndian.u16(sector, 14) > 0 && fatCount in 1..2
        }
    }
}
