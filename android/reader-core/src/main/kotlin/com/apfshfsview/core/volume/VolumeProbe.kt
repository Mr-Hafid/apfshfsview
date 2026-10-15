package com.apfshfsview.core.volume

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.PartitionBlockDevice
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.ProbedVolume
import com.apfshfsview.core.fs.ReaderException
import com.apfshfsview.core.fs.VolumeFormat
import com.apfshfsview.core.fs.VolumeInfo
import com.apfshfsview.core.fs.apfs.ApfsContainer
import com.apfshfsview.core.fs.hfsplus.HfsPlusFileSystem
import com.apfshfsview.core.partition.GptReader
import com.apfshfsview.core.partition.PartitionEntry
import com.apfshfsview.core.partition.PartitionTypes

data class ProbeResult(
    val volumes: List<ProbedVolume>,
    val unrecognizedPartitionTypes: List<String>,
)

object VolumeProbe {
    private const val NO_PARTITION_TABLE = "Tidak ada tabel partisi GPT"
    private val IGNORED_PARTITION_TYPES = setOf(PartitionTypes.EFI_SYSTEM)

    fun probe(device: BlockDevice): ProbeResult {
        val partitions = GptReader.read(device) ?: return probeWholeDevice(device)
        val volumes = mutableListOf<ProbedVolume>()
        val unrecognized = mutableListOf<String>()
        for (partition in partitions) {
            val slice = PartitionBlockDevice(device, partition.startByte, partition.lengthBytes)
            when (partition.typeGuid) {
                PartitionTypes.APFS -> volumes += guarded(partition, VolumeFormat.APFS) { ApfsContainer.open(slice).volumes() }
                PartitionTypes.HFS_PLUS -> volumes += guarded(partition, VolumeFormat.HFS_PLUS) { listOf(openHfs(slice)) }
                in IGNORED_PARTITION_TYPES -> Unit
                else -> unrecognized += partition.typeGuid
            }
        }
        return ProbeResult(volumes, unrecognized)
    }

    private fun probeWholeDevice(device: BlockDevice): ProbeResult {
        val volumes = when {
            HfsPlusFileSystem.matches(device) -> listOf(openHfs(device))
            ApfsContainer.matches(device) -> ApfsContainer.open(device).volumes()
            else -> emptyList()
        }
        return ProbeResult(volumes, if (volumes.isEmpty()) listOf(NO_PARTITION_TABLE) else emptyList())
    }

    private fun openHfs(device: BlockDevice): ProbedVolume =
        HfsPlusFileSystem.open(device).let { ProbedVolume(it.info, it, null) }

    private fun guarded(partition: PartitionEntry, format: VolumeFormat, open: () -> List<ProbedVolume>): List<ProbedVolume> =
        try {
            open()
        } catch (error: ReaderException) {
            listOf(failedVolume(partition, format, error))
        } catch (error: IndexOutOfBoundsException) {
            listOf(failedVolume(partition, format, CorruptStructureException(error.message ?: "Structure out of bounds")))
        }

    private fun failedVolume(partition: PartitionEntry, format: VolumeFormat, error: ReaderException): ProbedVolume =
        ProbedVolume(
            VolumeInfo(
                name = partition.name.ifBlank { "Partisi" },
                format = format,
                role = null,
                usedBytes = 0,
                totalBytes = partition.lengthBytes,
                isEncrypted = false,
                isUncleanlyUnmounted = false,
            ),
            null,
            error,
        )
}
