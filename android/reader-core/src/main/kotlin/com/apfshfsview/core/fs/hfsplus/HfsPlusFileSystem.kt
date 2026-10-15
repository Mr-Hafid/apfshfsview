package com.apfshfsview.core.fs.hfsplus

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.fs.ExtentFileReader
import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.FileReader
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.fs.NodeNotFoundException
import com.apfshfsview.core.fs.NotADirectoryException
import com.apfshfsview.core.fs.NotAFileException
import com.apfshfsview.core.fs.UnsupportedCompressionException
import com.apfshfsview.core.fs.VolumeFormat
import com.apfshfsview.core.fs.VolumeInfo
import com.apfshfsview.core.fs.decmpfs.Decmpfs
import com.apfshfsview.core.fs.decmpfs.DecmpfsHeader
import com.apfshfsview.core.fs.normalizeName

class HfsPlusFileSystem private constructor(
    private val device: BlockDevice,
    private val header: HfsPlusVolumeHeader,
) : FileSystem {
    private class ResolvedNode(val node: FileNode, val contentFile: HfsCatalogFile?)

    private val blockSize = header.blockSize
    private val extentsOverflow = HfsExtentsOverflow(
        HfsBTree(ExtentFileReader(device, header.extentsFile.extents.toPhysicalExtents(blockSize), header.extentsFile.logicalSize)),
    )
    private val catalog = HfsCatalog(
        HfsBTree(forkReader(CATALOG_FILE_ID, header.catalogFile, DATA_FORK)),
        isCaseInsensitive = !header.isHfsx,
    )
    private val attributes = header.attributesFile.takeIf { it.logicalSize > 0 }?.let {
        HfsAttributes(HfsBTree(forkReader(ATTRIBUTES_FILE_ID, it, DATA_FORK)))
    }
    private val privateDataFolderId: Long? by lazy {
        val entry = catalog.find(ROOT_FOLDER_ID, PRIVATE_DATA_FOLDER_NAME)
            ?: catalog.children(ROOT_FOLDER_ID).firstOrNull { it.name == PRIVATE_DATA_FOLDER_NAME }
        (entry?.record as? HfsCatalogFolder)?.folderId
    }
    private val nodeCache = object : LinkedHashMap<Long, ResolvedNode>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ResolvedNode>): Boolean = size > NODE_CACHE_SIZE
    }

    override val rootId: Long = ROOT_FOLDER_ID

    override val info: VolumeInfo = VolumeInfo(
        name = catalog.thread(ROOT_FOLDER_ID)?.name?.let(::normalizeName) ?: "HFS+",
        format = if (header.isHfsx) VolumeFormat.HFSX else VolumeFormat.HFS_PLUS,
        role = null,
        usedBytes = (header.totalBlocks - header.freeBlocks) * blockSize,
        totalBytes = header.totalBlocks * blockSize,
        isEncrypted = false,
        isUncleanlyUnmounted = !header.isCleanlyUnmounted,
    )

    override fun listDirectory(nodeId: Long): List<FileNode> {
        if (resolve(nodeId).node.kind != NodeKind.DIRECTORY) throw NotADirectoryException(nodeId)
        return catalog.children(nodeId)
            .filterNot { isSystemEntry(nodeId, it.name) }
            .map { entry -> toResolved(entry).also { remember(it) }.node }
    }

    override fun stat(nodeId: Long): FileNode = resolve(nodeId).node

    override fun openReader(nodeId: Long): FileReader {
        val resolved = resolve(nodeId)
        val file = resolved.contentFile ?: throw NotAFileException(nodeId)
        if (!file.isCompressed) return forkReader(file.fileId, file.dataFork, DATA_FORK)
        val decmpfs = decmpfsHeader(file) ?: throw UnsupportedCompressionException(-1)
        return Decmpfs.openReader(decmpfs) { forkReader(file.fileId, file.resourceFork, RESOURCE_FORK) }
    }

    private fun resolve(nodeId: Long): ResolvedNode {
        cached(nodeId)?.let { return it }
        val thread = catalog.thread(nodeId) ?: throw NodeNotFoundException(nodeId)
        val entry = catalog.find(thread.parentId, thread.name)?.takeIf { idOf(it) == nodeId }
            ?: catalog.children(thread.parentId).firstOrNull { idOf(it) == nodeId }
            ?: throw NodeNotFoundException(nodeId)
        return toResolved(entry).also { remember(it) }
    }

    @Synchronized
    private fun cached(nodeId: Long): ResolvedNode? = nodeCache[nodeId]

    @Synchronized
    private fun remember(resolved: ResolvedNode) {
        nodeCache[resolved.node.id] = resolved
    }

    private fun idOf(entry: HfsCatalogEntry): Long? = when (val record = entry.record) {
        is HfsCatalogFolder -> record.folderId
        is HfsCatalogFile -> record.fileId
        is HfsCatalogThread -> null
    }

    private fun toResolved(entry: HfsCatalogEntry): ResolvedNode = when (val record = entry.record) {
        is HfsCatalogFolder -> ResolvedNode(
            FileNode(
                id = record.folderId,
                parentId = entry.parentId,
                name = normalizeName(entry.name),
                kind = NodeKind.DIRECTORY,
                size = 0,
                childCount = record.valence.toInt(),
                createdAtMillis = hfsTimeToMillis(record.createDate),
                modifiedAtMillis = hfsTimeToMillis(record.contentModDate),
                isCompressed = false,
                isReadable = true,
            ),
            null,
        )
        is HfsCatalogFile -> fileNode(entry, record)
        is HfsCatalogThread -> throw NodeNotFoundException(-1)
    }

    private fun fileNode(entry: HfsCatalogEntry, record: HfsCatalogFile): ResolvedNode {
        val content = if (record.isHardLink) resolveHardLinkTarget(record.special) ?: record else record
        val decmpfs = if (content.isCompressed) decmpfsHeader(content) else null
        val isReadable = !record.isDirectoryLink && (!content.isCompressed || decmpfs?.isSupported == true)
        return ResolvedNode(
            FileNode(
                id = record.fileId,
                parentId = entry.parentId,
                name = normalizeName(entry.name),
                kind = if (content.isSymlink) NodeKind.SYMLINK else NodeKind.FILE,
                size = decmpfs?.uncompressedSize ?: content.dataFork.logicalSize,
                childCount = -1,
                createdAtMillis = hfsTimeToMillis(content.createDate),
                modifiedAtMillis = hfsTimeToMillis(content.contentModDate),
                isCompressed = content.isCompressed,
                isReadable = isReadable,
            ),
            if (record.isDirectoryLink) null else content,
        )
    }

    private fun resolveHardLinkTarget(inodeNumber: Long): HfsCatalogFile? {
        val privateFolder = privateDataFolderId ?: return null
        val name = "iNode$inodeNumber"
        val entry = catalog.find(privateFolder, name)
            ?: catalog.children(privateFolder).firstOrNull { it.name == name }
        return entry?.record as? HfsCatalogFile
    }

    private fun decmpfsHeader(file: HfsCatalogFile): DecmpfsHeader? =
        attributes?.inlineAttribute(file.fileId, DecmpfsHeader.ATTRIBUTE_NAME)
            ?.let { runCatching { DecmpfsHeader.parse(it) }.getOrNull() }

    private fun forkReader(fileId: Long, fork: HfsForkData, forkType: Int): ExtentFileReader {
        val inlineBlocks = fork.extents.sumOf { it.blockCount }
        val extents = if (inlineBlocks < fork.totalBlocks) {
            fork.extents + extentsOverflow.extents(fileId, forkType, inlineBlocks)
        } else {
            fork.extents
        }
        return ExtentFileReader(device, extents.toPhysicalExtents(blockSize), fork.logicalSize)
    }

    private fun isSystemEntry(parentId: Long, name: String): Boolean =
        name.startsWith('\u0000') || (parentId == ROOT_FOLDER_ID && name in ROOT_SYSTEM_NAMES)

    private fun hfsTimeToMillis(seconds: Long): Long =
        if (seconds == 0L) 0 else (seconds - HFS_EPOCH_OFFSET_SECONDS) * 1000

    companion object {
        private const val ROOT_FOLDER_ID = 2L
        private const val CATALOG_FILE_ID = 4L
        private const val ATTRIBUTES_FILE_ID = 8L
        private const val DATA_FORK = 0x00
        private const val RESOURCE_FORK = 0xFF
        private const val NODE_CACHE_SIZE = 8192
        private const val HFS_EPOCH_OFFSET_SECONDS = 2082844800L
        private const val PRIVATE_DATA_FOLDER_NAME = "\u0000\u0000\u0000\u0000HFS+ Private Data"
        private val ROOT_SYSTEM_NAMES = setOf(".HFS+ Private Directory Data\r", ".journal", ".journal_info_block")

        fun matches(device: BlockDevice): Boolean = HfsPlusVolumeHeader.matches(device)

        fun open(device: BlockDevice): HfsPlusFileSystem = HfsPlusFileSystem(device, HfsPlusVolumeHeader.read(device))
    }
}
