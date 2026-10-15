package com.apfshfsview.core.fs.fat

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.readBytes
import com.apfshfsview.core.fs.ExtentFileReader
import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.FileReader
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.fs.NodeNotFoundException
import com.apfshfsview.core.fs.NotADirectoryException
import com.apfshfsview.core.fs.NotAFileException
import com.apfshfsview.core.fs.VolumeInfo
import com.apfshfsview.core.fs.cluster.ClusterRun
import com.apfshfsview.core.fs.cluster.DirectoryBuffer
import com.apfshfsview.core.fs.cluster.clusterRuns
import com.apfshfsview.core.fs.cluster.toExtents
import com.apfshfsview.core.fs.normalizeName
import java.util.concurrent.ConcurrentHashMap

class FatFileSystem private constructor(private val device: BlockDevice, private val boot: FatBootSector) : FileSystem {
    private class KnownEntry(val entry: FatDirectoryEntry, val parentId: Long)

    private val table = FatTable(device, boot)
    private val knownEntries = ConcurrentHashMap<Long, KnownEntry>()
    private val rootListing: FatDirectoryListing by lazy { FatDirectory.parse(directoryBuffer(rootRuns())) }

    override val rootId: Long = ROOT_ID

    override val info: VolumeInfo by lazy {
        val label = rootListing.volumeLabel?.takeIf { it.isNotBlank() }
            ?: boot.bootLabel.takeIf { it.isNotBlank() && it != NO_NAME_LABEL }
            ?: boot.format.label
        VolumeInfo(
            name = label,
            format = boot.format,
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
            FatDirectory.parse(directoryBuffer(chainRuns(entry.firstCluster)))
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
        return ExtentFileReader(device, chainRuns(entry.firstCluster).toExtents(entry.size), entry.size)
    }

    private fun resolve(nodeId: Long): KnownEntry =
        knownEntries[nodeId] ?: run {
            if (nodeId < boot.fatStartByte || nodeId + FatBootSector.DIRECTORY_ENTRY_SIZE > device.sizeBytes) {
                throw NodeNotFoundException(nodeId)
            }
            KnownEntry(FatDirectory.parseShortEntry(device.readBytes(nodeId, FatBootSector.DIRECTORY_ENTRY_SIZE), nodeId), -1)
        }

    private fun rootRuns(): List<ClusterRun> =
        if (boot.rootDirectoryBytes > 0) listOf(ClusterRun(boot.rootDirectoryStartByte, boot.rootDirectoryBytes))
        else chainRuns(boot.rootCluster)

    private fun chainRuns(firstCluster: Long): List<ClusterRun> =
        clusterRuns(table.chain(firstCluster), boot.clusterSize, boot::clusterOffset)

    private fun directoryBuffer(runs: List<ClusterRun>) = DirectoryBuffer(device, runs, MAX_DIRECTORY_BYTES)

    private fun usedBytes(): Long {
        val freeClusters = fsInfoFreeClusters()
            ?: if (boot.clusterCount <= MAX_SCANNED_CLUSTERS) table.countFreeClusters() else return 0
        return (boot.clusterCount - freeClusters).coerceAtLeast(0) * boot.clusterSize
    }

    private fun fsInfoFreeClusters(): Long? {
        if (boot.rootDirectoryBytes > 0 || boot.fsInfoSector == 0) return null
        val sector = device.readBytes(boot.fsInfoSector.toLong() * boot.bytesPerSector, 512)
        if (LittleEndian.u32(sector, 0) != FS_INFO_LEAD_SIGNATURE || LittleEndian.u32(sector, 484) != FS_INFO_STRUCT_SIGNATURE) return null
        return LittleEndian.u32(sector, 488).takeIf { it <= boot.clusterCount }
    }

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

    private fun toFileNode(entry: FatDirectoryEntry, parentId: Long): FileNode = FileNode(
        id = entry.id,
        parentId = parentId,
        name = normalizeName(entry.name),
        kind = if (entry.isDirectory) NodeKind.DIRECTORY else NodeKind.FILE,
        size = if (entry.isDirectory) 0 else entry.size,
        childCount = -1,
        createdAtMillis = entry.createdAtMillis,
        modifiedAtMillis = entry.modifiedAtMillis,
        isCompressed = false,
        isReadable = true,
    )

    companion object {
        private const val ROOT_ID = 1L
        private const val NO_NAME_LABEL = "NO NAME"
        private const val MAX_DIRECTORY_BYTES = 64L * 1024 * 1024
        private const val MAX_SCANNED_CLUSTERS = 4L * 1024 * 1024
        private const val FS_INFO_LEAD_SIGNATURE = 0x41615252L
        private const val FS_INFO_STRUCT_SIGNATURE = 0x61417272L

        fun matches(device: BlockDevice): Boolean =
            device.sizeBytes >= 512 && FatBootSector.matches(device.readBytes(0, 512))

        fun open(device: BlockDevice): FatFileSystem = FatFileSystem(device, FatBootSector(device.readBytes(0, 512)))
    }
}
