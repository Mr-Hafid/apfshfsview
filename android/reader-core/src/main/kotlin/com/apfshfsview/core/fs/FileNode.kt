package com.apfshfsview.core.fs

enum class NodeKind { FILE, DIRECTORY, SYMLINK }

enum class VolumeFormat(val label: String) {
    HFS_PLUS("HFS+"),
    HFSX("HFSX"),
    APFS("APFS"),
}

data class FileNode(
    val id: Long,
    val parentId: Long,
    val name: String,
    val kind: NodeKind,
    val size: Long,
    val childCount: Int,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
    val isCompressed: Boolean,
    val isReadable: Boolean,
)

data class VolumeInfo(
    val name: String,
    val format: VolumeFormat,
    val role: String?,
    val usedBytes: Long,
    val totalBytes: Long,
    val isEncrypted: Boolean,
    val isUncleanlyUnmounted: Boolean,
)
