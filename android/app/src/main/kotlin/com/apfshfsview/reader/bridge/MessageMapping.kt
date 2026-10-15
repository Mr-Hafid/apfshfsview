package com.apfshfsview.reader.bridge

import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.fs.ProbedVolume
import com.apfshfsview.reader.export.CopyProgress

fun FileNode.toMessage(): FileEntryMessage = FileEntryMessage(
    nodeId = id,
    parentId = parentId,
    name = name,
    kind = when (kind) {
        NodeKind.FILE -> NodeKindMessage.FILE
        NodeKind.DIRECTORY -> NodeKindMessage.DIRECTORY
        NodeKind.SYMLINK -> NodeKindMessage.SYMLINK
    },
    size = size,
    childCount = childCount.toLong(),
    createdAtMillis = createdAtMillis,
    modifiedAtMillis = modifiedAtMillis,
    isCompressed = isCompressed,
    isReadable = isReadable,
)

fun ProbedVolume.toMessage(volumeHandle: Long?): VolumeMessage = VolumeMessage(
    volumeHandle = volumeHandle,
    name = info.name,
    format = info.format.label,
    role = info.role,
    usedBytes = info.usedBytes,
    totalBytes = info.totalBytes,
    isEncrypted = info.isEncrypted,
    isUncleanlyUnmounted = info.isUncleanlyUnmounted,
    failureCode = failure?.code,
)

fun CopyProgress.toMessage(): CopyProgressMessage = CopyProgressMessage(
    jobId = jobId,
    currentName = currentName,
    copiedBytes = copiedBytes,
    totalBytes = totalBytes,
    copiedFiles = copiedFiles.toLong(),
    totalFiles = totalFiles.toLong(),
    skippedFiles = skippedFiles.toLong(),
    bytesPerSecond = bytesPerSecond,
    state = state.wireName,
    errorMessage = errorMessage,
    destinationName = destinationName,
)
