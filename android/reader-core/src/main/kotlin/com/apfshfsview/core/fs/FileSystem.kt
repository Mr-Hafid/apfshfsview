package com.apfshfsview.core.fs

interface FileSystem {
    val info: VolumeInfo
    val rootId: Long

    fun listDirectory(nodeId: Long): List<FileNode>

    fun stat(nodeId: Long): FileNode

    fun openReader(nodeId: Long): FileReader
}

class ProbedVolume(
    val info: VolumeInfo,
    val fileSystem: FileSystem?,
    val failure: ReaderException?,
)
