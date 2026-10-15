package com.apfshfsview.core.volume

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.PartitionBlockDevice
import com.apfshfsview.core.block.readBytes
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.ProbedVolume
import com.apfshfsview.core.fs.ReaderException
import com.apfshfsview.core.fs.VolumeFormat
import com.apfshfsview.core.fs.VolumeInfo
import com.apfshfsview.core.fs.apfs.ApfsContainer
import com.apfshfsview.core.fs.exfat.ExfatFileSystem
import com.apfshfsview.core.fs.fat.FatFileSystem
import com.apfshfsview.core.fs.hfsplus.HfsPlusFileSystem
import com.apfshfsview.core.partition.GptReader
import com.apfshfsview.core.partition.MbrReader
import com.apfshfsview.core.partition.PartitionTypes

data class ProbeResult(
    val volumes: List<ProbedVolume>,
    val unrecognizedPartitionTypes: List<String>,
)

object VolumeProbe {
    private class Slice(val name: String, val typeDescription: String, val isIgnorable: Boolean, val startByte: Long, val lengthBytes: Long)

    private sealed interface SliceContent {
        class Readable(val volumes: List<ProbedVolume>) : SliceContent
        class Unsupported(val description: String) : SliceContent
        data object Unknown : SliceContent
    }

    private const val NO_PARTITION_TABLE = "Tidak ada tabel partisi atau filesystem yang dikenali"
    private const val EXT_MAGIC = 0xEF53
    private val IGNORED_GPT_TYPES = setOf(
        PartitionTypes.EFI_SYSTEM,
        "426F6F74-0000-11AA-AA11-00306543ECAC",
        "E3C9E316-0B5C-4DB8-817D-F92DF00215AE",
    )
    private val GPT_TYPE_NAMES = mapOf(
        "EBD0A0A2-B9E5-4433-87C0-68B6B72699C7" to "Microsoft Basic Data",
        "0FC63DAF-8483-4772-8E79-3D69D8477DE4" to "Linux",
        "0657FD6D-A4AB-43C4-84E5-0933C84B4F4F" to "Linux Swap",
        "DE94BBA4-06D1-4D40-A16A-BFD50179D6AC" to "Windows Recovery",
    )
    private val IGNORED_MBR_TYPES = setOf(0xEF, MbrReader.PROTECTIVE_GPT_TYPE)

    fun probe(device: BlockDevice): ProbeResult {
        GptReader.read(device)?.let { partitions ->
            return probeSlices(
                device,
                partitions.map { partition ->
                    Slice(
                        name = partition.name,
                        typeDescription = GPT_TYPE_NAMES[partition.typeGuid] ?: partition.typeGuid,
                        isIgnorable = partition.typeGuid in IGNORED_GPT_TYPES,
                        startByte = partition.startByte,
                        lengthBytes = partition.lengthBytes,
                    )
                },
            )
        }
        if (!startsWithFilesystem(device)) {
            MbrReader.read(device)?.let { partitions ->
                return probeSlices(
                    device,
                    partitions.map { partition ->
                        Slice(
                            name = "",
                            typeDescription = "Tipe MBR 0x%02X".format(partition.typeCode),
                            isIgnorable = partition.typeCode in IGNORED_MBR_TYPES,
                            startByte = partition.startByte,
                            lengthBytes = partition.lengthBytes,
                        )
                    },
                )
            }
        }
        return when (val content = guarded("", device.sizeBytes) { detect(device) }) {
            is SliceContent.Readable -> ProbeResult(content.volumes, emptyList())
            is SliceContent.Unsupported -> ProbeResult(emptyList(), listOf(content.description))
            SliceContent.Unknown -> ProbeResult(emptyList(), listOf(NO_PARTITION_TABLE))
        }
    }

    private fun probeSlices(device: BlockDevice, slices: List<Slice>): ProbeResult {
        val volumes = mutableListOf<ProbedVolume>()
        val unrecognized = mutableListOf<String>()
        for (slice in slices) {
            if (slice.startByte + slice.lengthBytes > device.sizeBytes || slice.lengthBytes <= 0) continue
            val view = PartitionBlockDevice(device, slice.startByte, slice.lengthBytes)
            when (val content = guarded(slice.name, slice.lengthBytes) { detect(view) }) {
                is SliceContent.Readable -> volumes += content.volumes
                is SliceContent.Unsupported -> unrecognized += content.description
                SliceContent.Unknown -> if (!slice.isIgnorable) unrecognized += slice.typeDescription
            }
        }
        return ProbeResult(volumes, unrecognized)
    }

    private fun detect(device: BlockDevice): SliceContent {
        if (device.sizeBytes < 4096) return SliceContent.Unknown
        val head = device.readBytes(0, 4096)
        val oem = String(head, 3, 8, Charsets.ISO_8859_1)
        return when {
            ApfsContainer.matches(device) -> SliceContent.Readable(ApfsContainer.open(device).volumes())
            HfsPlusFileSystem.matches(device) -> SliceContent.Readable(listOf(readable(HfsPlusFileSystem.open(device))))
            ExfatFileSystem.matches(device) -> SliceContent.Readable(listOf(readable(ExfatFileSystem.open(device))))
            FatFileSystem.matches(device) -> SliceContent.Readable(listOf(readable(FatFileSystem.open(device))))
            oem == "NTFS    " -> SliceContent.Unsupported("NTFS — belum didukung")
            LittleEndian.u16(head, 1024 + 56) == EXT_MAGIC -> SliceContent.Unsupported("Linux ext2/3/4 — belum didukung")
            else -> SliceContent.Unknown
        }
    }

    private fun startsWithFilesystem(device: BlockDevice): Boolean {
        if (device.sizeBytes < 4096) return false
        return ExfatFileSystem.matches(device) || FatFileSystem.matches(device) ||
            HfsPlusFileSystem.matches(device) || ApfsContainer.matches(device) ||
            String(device.readBytes(3, 8), Charsets.ISO_8859_1) == "NTFS    "
    }

    private fun readable(fileSystem: FileSystem): ProbedVolume = ProbedVolume(fileSystem.info, fileSystem, null)

    private fun guarded(name: String, lengthBytes: Long, detect: () -> SliceContent): SliceContent =
        try {
            detect()
        } catch (error: ReaderException) {
            SliceContent.Readable(listOf(failedVolume(name, lengthBytes, error)))
        } catch (error: IndexOutOfBoundsException) {
            SliceContent.Readable(
                listOf(failedVolume(name, lengthBytes, CorruptStructureException(error.message ?: "Structure out of bounds"))),
            )
        }

    private fun failedVolume(name: String, lengthBytes: Long, error: ReaderException): ProbedVolume =
        ProbedVolume(
            VolumeInfo(
                name = name.ifBlank { "Partisi" },
                format = VolumeFormat.UNKNOWN,
                role = null,
                usedBytes = 0,
                totalBytes = lengthBytes,
                isEncrypted = false,
                isUncleanlyUnmounted = false,
            ),
            null,
            error,
        )
}
