package com.apfshfsview.core.support

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.PartitionBlockDevice
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.partition.GptReader
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.text.Normalizer

data class ManifestEntry(
    val kind: NodeKind,
    val size: Long?,
    val sha256: String?,
    val isCompressed: Boolean,
    val path: String,
)

object Fixtures {
    private val directory = File(System.getProperty("fixturesDir") ?: "tools/fixtures")

    fun image(name: String): File = File(directory, "$name.dmg")

    fun assumePresent(name: String) {
        assumeTrue(image(name).exists(), "Fixture $name missing, run tools/make_fixtures.sh")
    }

    fun manifest(name: String): List<ManifestEntry> =
        File(directory, "$name.tsv").readLines().filter { it.isNotBlank() }.map { line ->
            val columns = line.split('\t')
            ManifestEntry(
                kind = when (columns[0]) {
                    "dir" -> NodeKind.DIRECTORY
                    "symlink" -> NodeKind.SYMLINK
                    else -> NodeKind.FILE
                },
                size = columns[1].toLongOrNull(),
                sha256 = columns[2].takeIf { it != "-" },
                isCompressed = columns[3] == "1",
                path = Normalizer.normalize(columns[4], Normalizer.Form.NFC),
            )
        }

    fun singlePartition(device: BlockDevice, typeGuid: String): BlockDevice {
        val partition = GptReader.read(device)!!.single { it.typeGuid == typeGuid }
        return PartitionBlockDevice(device, partition.startByte, partition.lengthBytes)
    }
}
