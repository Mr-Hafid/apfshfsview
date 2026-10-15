package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.readBytes
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.ProbedVolume
import com.apfshfsview.core.fs.ReaderException
import com.apfshfsview.core.fs.UnsupportedEncryptionException
import com.apfshfsview.core.fs.VolumeFormat
import com.apfshfsview.core.fs.VolumeInfo
import com.apfshfsview.core.fs.normalizeName

class ApfsContainer private constructor(val device: BlockDevice, private val superblock: NxSuperblock) {
    val blockSize: Int = superblock.blockSize
    private val objectMap by lazy { ApfsObjectMap(this, superblock.objectMapAddress) }

    fun readObject(address: Long): ByteArray {
        val block = device.readBytes(address * blockSize, blockSize)
        if (!Fletcher64.isValid(block)) throw CorruptStructureException("APFS checksum mismatch at block $address")
        return block
    }

    fun volumes(): List<ProbedVolume> = superblock.fileSystemOids.map(::openVolume)

    private fun openVolume(oid: Long): ProbedVolume {
        val volumeSuperblock = ApfsVolumeSuperblock(readObject(objectMap.resolve(oid)))
        val info = VolumeInfo(
            name = normalizeName(volumeSuperblock.name),
            format = VolumeFormat.APFS,
            role = volumeSuperblock.roleLabel,
            usedBytes = volumeSuperblock.allocatedBlocks * blockSize,
            totalBytes = superblock.blockCount * blockSize,
            isEncrypted = volumeSuperblock.isEncrypted,
            isUncleanlyUnmounted = false,
        )
        if (volumeSuperblock.isEncrypted) return ProbedVolume(info, null, UnsupportedEncryptionException())
        return try {
            ProbedVolume(info, ApfsFileSystem(this, volumeSuperblock, info), null)
        } catch (error: ReaderException) {
            ProbedVolume(info, null, error)
        }
    }

    companion object {
        private const val MIN_BLOCK_SIZE = 4096
        private const val MAX_BLOCK_SIZE = 65536
        private const val TREE_BACKED_DESCRIPTOR_FLAG = 0x80000000L

        fun matches(device: BlockDevice): Boolean =
            device.sizeBytes >= MIN_BLOCK_SIZE && NxSuperblock.hasMagic(device.readBytes(0, 40))

        fun open(device: BlockDevice): ApfsContainer {
            val header = device.readBytes(0, 40)
            if (!NxSuperblock.hasMagic(header)) throw CorruptStructureException("APFS container superblock not found")
            val blockSize = NxSuperblock.blockSizeOf(header)
            if (blockSize !in MIN_BLOCK_SIZE..MAX_BLOCK_SIZE || blockSize and (blockSize - 1) != 0) {
                throw CorruptStructureException("Invalid APFS block size $blockSize")
            }
            return ApfsContainer(device, latestSuperblock(device, NxSuperblock(device.readBytes(0, blockSize))))
        }

        private fun latestSuperblock(device: BlockDevice, primary: NxSuperblock): NxSuperblock {
            var latest = primary.takeIf { Fletcher64.isValid(it.block) }
            if (primary.checkpointDescriptorBlocks and TREE_BACKED_DESCRIPTOR_FLAG == 0L) {
                for (index in 0 until primary.checkpointDescriptorBlocks) {
                    val block = device.readBytes((primary.checkpointDescriptorBase + index) * primary.blockSize, primary.blockSize)
                    val objectType = LittleEndian.u32(block, 24) and 0xFFFF
                    if (objectType != NxSuperblock.OBJECT_TYPE.toLong() || !NxSuperblock.hasMagic(block) || !Fletcher64.isValid(block)) continue
                    val candidate = NxSuperblock(block)
                    if (latest == null || candidate.transactionId > latest.transactionId) latest = candidate
                }
            }
            return latest ?: throw CorruptStructureException("No valid APFS container superblock")
        }
    }
}
