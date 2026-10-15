package com.apfshfsview.core.fs.cluster

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.PhysicalExtent

data class ClusterRun(val physicalOffset: Long, val length: Long)

fun clusterRuns(clusters: List<Long>, clusterSize: Int, clusterOffset: (Long) -> Long): List<ClusterRun> {
    val runs = mutableListOf<ClusterRun>()
    var runStart = -1L
    var runLength = 0L
    var previous = -1L
    for (cluster in clusters) {
        if (runStart >= 0 && cluster == previous + 1) {
            runLength += clusterSize
        } else {
            if (runStart >= 0) runs += ClusterRun(clusterOffset(runStart), runLength)
            runStart = cluster
            runLength = clusterSize.toLong()
        }
        previous = cluster
    }
    if (runStart >= 0) runs += ClusterRun(clusterOffset(runStart), runLength)
    return runs
}

fun ClusterRun.toExtents(startLogical: Long, limit: Long): PhysicalExtent? {
    if (startLogical >= limit) return null
    return PhysicalExtent(startLogical, physicalOffset, minOf(length, limit - startLogical))
}

fun List<ClusterRun>.toExtents(limit: Long): List<PhysicalExtent> {
    var logical = 0L
    return mapNotNull { run -> run.toExtents(logical, limit).also { logical += run.length } }
}

class DirectoryBuffer(device: BlockDevice, private val runs: List<ClusterRun>, maxBytes: Long) {
    val bytes: ByteArray

    init {
        val total = runs.sumOf { it.length }
        if (total > maxBytes) throw CorruptStructureException("Directory is too large ($total bytes)")
        bytes = ByteArray(total.toInt())
        var position = 0
        for (run in runs) {
            device.read(run.physicalOffset, bytes, position, run.length.toInt())
            position += run.length.toInt()
        }
    }

    fun absoluteOffset(index: Int): Long {
        var remaining = index.toLong()
        for (run in runs) {
            if (remaining < run.length) return run.physicalOffset + remaining
            remaining -= run.length
        }
        throw CorruptStructureException("Directory offset $index out of range")
    }
}
