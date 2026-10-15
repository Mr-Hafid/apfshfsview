package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.ByteArrayFileReader
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.ExtentFileReader
import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.FileReader
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.fs.NodeNotFoundException
import com.apfshfsview.core.fs.NotADirectoryException
import com.apfshfsview.core.fs.NotAFileException
import com.apfshfsview.core.fs.PhysicalExtent
import com.apfshfsview.core.fs.UnsupportedCompressionException
import com.apfshfsview.core.fs.VolumeInfo
import com.apfshfsview.core.fs.decmpfs.Decmpfs
import com.apfshfsview.core.fs.decmpfs.DecmpfsHeader
import com.apfshfsview.core.fs.normalizeName
import com.apfshfsview.core.fs.readFully

class ApfsFileSystem(
    private val container: ApfsContainer,
    superblock: ApfsVolumeSuperblock,
    override val info: VolumeInfo,
) : FileSystem {
    private val objectMap = ApfsObjectMap(container, superblock.objectMapAddress)
    private val rootTree = ApfsBTree(container, objectMap.resolve(superblock.rootTreeOid), objectMap::resolve)

    override val rootId: Long = ROOT_DIRECTORY_ID

    override fun listDirectory(nodeId: Long): List<FileNode> {
        if (inode(nodeId).kind != NodeKind.DIRECTORY) throw NotADirectoryException(nodeId)
        return rootTree.entriesFrom { block, keyOffset, _ -> compareToRecordStart(block, keyOffset, nodeId, ApfsRecordTypes.DIR_REC) }
            .takeWhile { ApfsKeys.matches(it, nodeId, ApfsRecordTypes.DIR_REC) }
            .map(ApfsDirectoryRecord::parse)
            .filter { it.fileId != PRIVATE_DIRECTORY_ID }
            .toList()
            .map { record -> toFileNode(inode(record.fileId), record.name, nodeId) }
    }

    override fun stat(nodeId: Long): FileNode {
        val inode = inode(nodeId)
        val name = if (nodeId == ROOT_DIRECTORY_ID) info.name else inode.name.orEmpty()
        return toFileNode(inode, name, inode.parentId)
    }

    override fun openReader(nodeId: Long): FileReader {
        val inode = inode(nodeId)
        if (inode.kind == NodeKind.DIRECTORY) throw NotAFileException(nodeId)
        if (!inode.isCompressed) return ExtentFileReader(container.device, fileExtents(inode.privateId), inode.dataStreamSize ?: 0)
        val header = decmpfsHeader(inode) ?: throw UnsupportedCompressionException(-1)
        return Decmpfs.openReader(header) {
            xattrReader(nodeId, DecmpfsHeader.RESOURCE_FORK_ATTRIBUTE_NAME)
                ?: throw CorruptStructureException("Compressed file $nodeId has no resource fork")
        }
    }

    private fun toFileNode(inode: ApfsInode, name: String, parentId: Long): FileNode {
        val decmpfs = if (inode.isCompressed) decmpfsHeader(inode) else null
        val isDirectory = inode.kind == NodeKind.DIRECTORY
        return FileNode(
            id = inode.objectId,
            parentId = parentId,
            name = normalizeName(name),
            kind = inode.kind,
            size = when {
                isDirectory -> 0
                inode.isCompressed -> decmpfs?.uncompressedSize ?: 0
                else -> inode.dataStreamSize ?: 0
            },
            childCount = if (isDirectory) inode.childOrLinkCount else -1,
            createdAtMillis = inode.createdAtNanos / NANOS_PER_MILLI,
            modifiedAtMillis = inode.modifiedAtNanos / NANOS_PER_MILLI,
            isCompressed = inode.isCompressed,
            isReadable = isDirectory || !inode.isCompressed || decmpfs?.isSupported == true,
        )
    }

    private fun inode(objectId: Long): ApfsInode =
        rootTree.entriesFrom { block, keyOffset, _ -> ApfsKeys.compareHeader(block, keyOffset, objectId, ApfsRecordTypes.INODE) }
            .firstOrNull()
            ?.takeIf { ApfsKeys.matches(it, objectId, ApfsRecordTypes.INODE) }
            ?.let(ApfsInode::parse)
            ?: throw NodeNotFoundException(objectId)

    private fun fileExtents(privateId: Long): List<PhysicalExtent> =
        rootTree.entriesFrom { block, keyOffset, _ ->
            val header = ApfsKeys.compareHeader(block, keyOffset, privateId, ApfsRecordTypes.FILE_EXTENT)
            if (header != 0) header else LittleEndian.u64(block, keyOffset + 8).compareTo(0L)
        }
            .takeWhile { ApfsKeys.matches(it, privateId, ApfsRecordTypes.FILE_EXTENT) }
            .map { entry ->
                val physicalBlock = LittleEndian.u64(entry.block, entry.valueOffset + 8)
                PhysicalExtent(
                    logicalOffset = LittleEndian.u64(entry.block, entry.keyOffset + 8),
                    physicalOffset = if (physicalBlock == 0L) null else physicalBlock * container.blockSize,
                    length = LittleEndian.u64(entry.block, entry.valueOffset) and EXTENT_LENGTH_MASK,
                )
            }
            .toList()

    private fun xattr(objectId: Long, name: String): ApfsXattrValue? =
        rootTree.entriesFrom { block, keyOffset, _ -> compareToRecordStart(block, keyOffset, objectId, ApfsRecordTypes.XATTR) }
            .takeWhile { ApfsKeys.matches(it, objectId, ApfsRecordTypes.XATTR) }
            .firstOrNull { ApfsXattrs.name(it) == name }
            ?.let(ApfsXattrs::value)

    private fun xattrReader(objectId: Long, name: String): FileReader? = when (val value = xattr(objectId, name)) {
        is ApfsXattrValue.Embedded -> ByteArrayFileReader(value.bytes)
        is ApfsXattrValue.Stream -> ExtentFileReader(container.device, fileExtents(value.objectId), value.size)
        null -> null
    }

    private fun decmpfsHeader(inode: ApfsInode): DecmpfsHeader? {
        val reader = xattrReader(inode.objectId, DecmpfsHeader.ATTRIBUTE_NAME) ?: return null
        if (reader.size > MAX_DECMPFS_ATTRIBUTE_SIZE) return null
        return runCatching { DecmpfsHeader.parse(reader.readFully(0, reader.size.toInt())) }.getOrNull()
    }

    private fun compareToRecordStart(block: ByteArray, keyOffset: Int, objectId: Long, recordType: Int): Int {
        val header = ApfsKeys.compareHeader(block, keyOffset, objectId, recordType)
        return if (header != 0) header else 1
    }

    private companion object {
        const val ROOT_DIRECTORY_ID = 2L
        const val PRIVATE_DIRECTORY_ID = 3L
        const val NANOS_PER_MILLI = 1_000_000L
        const val EXTENT_LENGTH_MASK = 0x00FFFFFFFFFFFFFFL
        const val MAX_DECMPFS_ATTRIBUTE_SIZE = 64L * 1024 * 1024
    }
}
