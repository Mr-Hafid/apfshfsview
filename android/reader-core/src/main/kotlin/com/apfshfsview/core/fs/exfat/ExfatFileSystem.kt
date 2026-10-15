package com.apfshfsview.core.fs.exfat

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.readBytes
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.ExtentFileReader
import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.FileReader
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.fs.NodeNotFoundException
import com.apfshfsview.core.fs.NotADirectoryException
import com.apfshfsview.core.fs.NotAFileException
import com.apfshfsview.core.fs.VolumeFormat
import com.apfshfsview.core.fs.VolumeInfo
import com.apfshfsview.core.fs.cluster.ClusterRun
import com.apfshfsview.core.fs.cluster.DirectoryBuffer
import com.apfshfsview.core.fs.cluster.clusterRuns
import com.apfshfsview.core.fs.cluster.toExtents
import com.apfshfsview.core.fs.normalizeName
import java.util.concurrent.ConcurrentHashMap

class ExfatFileSystem private constructor(private val device: BlockDevice, private val boot: ExfatBootSector) : FileSystem {
    private class KnownEntry(val entry: ExfatDirectoryEntry, val parentId: Long)

    private val knownEntries = ConcurrentHashMap<Long, KnownEntry>()
    private val rootListing: ExfatDirectoryListing by lazy { ExfatDirectory.parse(directoryBuffer(chainRuns(boot.rootCluster))) }

    override val rootId: Long = ROOT_ID

    override val info: VolumeInfo by lazy {
        VolumeInfo(
            name = rootListing.volumeLabel?.takeIf { it.isNotBlank() }?.let(::normalizeName) ?: VolumeFormat.EXFAT.label,
            format = VolumeFormat.EXFAT,
            role = null,
            usedBytes = usedBytes(),
            totalBytes = boot.totalBytes,
            isEncrypted = false,
            isUncleanlyUnmounted = false,
        )
    }

    override fun listDirectory(nodeId: Long): List<FileNode> {
        val listing = if (nodeId == ROOT_ID) {
            rootListing
        } else {
            val entry = resolve(nodeId).entry
            if (!entry.isDirectory) throw NotADirectoryException(nodeId)
            ExfatDirectory.parse(directoryBuffer(dataRuns(entry)))
        }
        return listing.entries.map { entry ->
            knownEntries[entry.id] = KnownEntry(entry, nodeId)
            toFileNode(entry, nodeId)
        }
    }

    override fun stat(nodeId: Long): FileNode {
        if (nodeId == ROOT_ID) return rootNode()
        val known = resolve(nodeId)
        return toFileNode(known.entry, known.parentId)
    }

    override fun openReader(nodeId: Long): FileReader {
        val entry = resolve(nodeId).entry
        if (entry.isDirectory) throw NotAFileException(nodeId)
        val validLength = minOf(entry.validDataLength, entry.dataLength)
        return ExtentFileReader(device, dataRuns(entry).toExtents(validLength), entry.dataLength)
    }

    private fun resolve(nodeId: Long): KnownEntry = knownEntries[nodeId] ?: throw NodeNotFoundException(nodeId)

    private fun dataRuns(entry: ExfatDirectoryEntry): List<ClusterRun> {
        if (entry.firstCluster < 2 || entry.dataLength == 0L) return emptyList()
        if (!entry.isContiguous) return chainRuns(entry.firstCluster)
        val clusters = (entry.dataLength + boot.clusterSize - 1) / boot.clusterSize
        if (entry.firstCluster + clusters - 2 > boot.clusterCount) throw CorruptStructureException("exFAT extent out of range")
        return listOf(ClusterRun(boot.clusterOffset(entry.firstCluster), clusters * boot.clusterSize))
    }

    private fun chainRuns(firstCluster: Long): List<ClusterRun> {
        val clusters = mutableListOf<Long>()
        var cluster = firstCluster
        while (cluster in 2..(boot.clusterCount + 1)) {
            clusters += cluster
            if (clusters.size > boot.clusterCount) throw CorruptStructureException("exFAT chain loops")
            val next = LittleEndian.u32(device.readBytes(boot.fatStartByte + cluster * 4, 4), 0)
            if (next >= END_OF_CHAIN) break
            cluster = next
        }
        return clusterRuns(clusters, boot.clusterSize, boot::clusterOffset)
    }

    private fun usedBytes(): Long {
        val bitmap = rootListing.allocationBitmap
        if (bitmap == null || bitmap.length > MAX_BITMAP_BYTES) return boot.reportedUsedBytes ?: 0
        val runs = chainRuns(bitmap.firstCluster)
        val bytes = ExtentFileReader(device, runs.toExtents(bitmap.length), bitmap.length).use { reader ->
            ByteArray(bitmap.length.toInt()).also { buffer ->
                var position = 0
                while (position < buffer.size) {
                    val count = reader.read(position.toLong(), buffer, position, buffer.size - position)
                    if (count <= 0) break
                    position += count
                }
            }
        }
        var allocatedClusters = 0L
        val validBits = boot.clusterCount
        for (index in bytes.indices) {
            val bitsInByte = minOf(8L, validBits - index * 8L).toInt()
            if (bitsInByte <= 0) break
            val mask = if (bitsInByte == 8) 0xFF else (1 shl bitsInByte) - 1
            allocatedClusters += Integer.bitCount(bytes[index].toInt() and mask)
        }
        return allocatedClusters * boot.clusterSize
    }

    private fun directoryBuffer(runs: List<ClusterRun>) = DirectoryBuffer(device, runs, MAX_DIRECTORY_BYTES)

    private fun rootNode(): FileNode = FileNode(
        id = ROOT_ID,
        parentId = ROOT_ID,
        name = info.name,
        kind = NodeKind.DIRECTORY,
        size = 0,
        childCount = -1,
        createdAtMillis = 0,
        modifiedAtMillis = 0,
        isCompressed = false,
        isReadable = true,
    )

    private fun toFileNode(entry: ExfatDirectoryEntry, parentId: Long): FileNode = FileNode(
        id = entry.id,
        parentId = parentId,
        name = normalizeName(entry.name),
        kind = if (entry.isDirectory) NodeKind.DIRECTORY else NodeKind.FILE,
        size = if (entry.isDirectory) 0 else entry.dataLength,
        childCount = -1,
        createdAtMillis = entry.createdAtMillis,
        modifiedAtMillis = entry.modifiedAtMillis,
        isCompressed = false,
        isReadable = true,
    )

    companion object {
        private const val ROOT_ID = 1L
        private const val END_OF_CHAIN = 0xFFFFFFF7L
        private const val MAX_DIRECTORY_BYTES = 256L * 1024 * 1024
        private const val MAX_BITMAP_BYTES = 64L * 1024 * 1024

        fun matches(device: BlockDevice): Boolean =
            device.sizeBytes >= 512 && ExfatBootSector.matches(device.readBytes(0, 512))

        fun open(device: BlockDevice): ExfatFileSystem = ExfatFileSystem(device, ExfatBootSector(device.readBytes(0, 512)))
    }
}
