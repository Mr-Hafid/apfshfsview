package com.apfshfsview.reader.export

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import com.apfshfsview.core.naming.UniqueNames
import java.io.IOException

class CopyCancelledException : RuntimeException()

class CopyEngine(
    private val context: Context,
    private val job: CopyJob,
    private val fileSystem: FileSystem,
    private val onProgress: (CopyProgress) -> Unit,
) {
    private val buffer = ByteArray(BUFFER_SIZE)
    private val knownChildNames = HashMap<Uri, MutableSet<String>>()
    private val startedAt = SystemClock.elapsedRealtime()
    private var lastPublishedAt = 0L
    private var currentName = ""
    private var copiedBytes = 0L
    private var totalBytes = 0L
    private var copiedFiles = 0
    private var totalFiles = 0
    private var skippedFiles = 0

    fun run(): CopyProgress {
        val roots = job.nodeIds.map(fileSystem::stat)
        roots.forEach(::measure)
        val destination = DocumentFile.fromTreeUri(context, job.destination)
            ?: throw IOException("Folder tujuan tidak bisa dibuka")
        knownChildNames[destination.uri] = destination.listFiles().mapNotNull { it.name }.toHashSet()
        return try {
            publish(force = true)
            roots.forEach { copyNode(it, destination) }
            snapshot(CopyState.COMPLETED, skippedMessage())
        } catch (cancelled: CopyCancelledException) {
            snapshot(CopyState.CANCELLED, null)
        } catch (error: Exception) {
            if (CopyJobs.isCancelled(job.jobId)) {
                snapshot(CopyState.CANCELLED, null)
            } else {
                snapshot(CopyState.FAILED, "Gagal menyalin $currentName: ${error.message}")
            }
        }
    }

    private fun measure(node: FileNode) {
        when (node.kind) {
            NodeKind.DIRECTORY -> fileSystem.listDirectory(node.id).forEach(::measure)
            NodeKind.FILE -> {
                totalFiles++
                totalBytes += node.size
            }
            NodeKind.SYMLINK -> Unit
        }
    }

    private fun copyNode(node: FileNode, parent: DocumentFile) {
        checkCancelled()
        when (node.kind) {
            NodeKind.DIRECTORY -> {
                val directory = parent.createDirectory(uniqueName(parent, node.name))
                    ?: throw IOException("Gagal membuat folder ${node.name}")
                knownChildNames[directory.uri] = HashSet()
                fileSystem.listDirectory(node.id).forEach { copyNode(it, directory) }
            }
            NodeKind.FILE -> copyFile(node, parent)
            NodeKind.SYMLINK -> Unit
        }
    }

    private fun copyFile(node: FileNode, parent: DocumentFile) {
        currentName = node.name
        if (!node.isReadable) {
            skippedFiles++
            totalBytes -= node.size
            publish(force = true)
            return
        }
        val target = parent.createFile(MIME_OCTET_STREAM, uniqueName(parent, node.name))
            ?: throw IOException("Gagal membuat file ${node.name}")
        var isComplete = false
        try {
            val output = context.contentResolver.openOutputStream(target.uri)
                ?: throw IOException("Gagal menulis file ${node.name}")
            output.use {
                fileSystem.openReader(node.id).use { reader ->
                    var position = 0L
                    while (position < reader.size) {
                        checkCancelled()
                        val count = reader.read(position, buffer, 0, buffer.size)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        position += count
                        copiedBytes += count
                        publish(force = false)
                    }
                }
            }
            isComplete = true
            copiedFiles++
            publish(force = true)
        } finally {
            if (!isComplete) runCatching { target.delete() }
        }
    }

    private fun uniqueName(parent: DocumentFile, name: String): String {
        val names = knownChildNames.getOrPut(parent.uri) { HashSet() }
        return UniqueNames.resolve(UniqueNames.sanitize(name)) { it in names }.also { names += it }
    }

    private fun checkCancelled() {
        if (CopyJobs.isCancelled(job.jobId)) throw CopyCancelledException()
    }

    private fun publish(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublishedAt < PUBLISH_INTERVAL_MILLIS) return
        lastPublishedAt = now
        onProgress(snapshot(CopyState.RUNNING, null))
    }

    private fun skippedMessage(): String? =
        if (skippedFiles > 0) "$skippedFiles file dilewati karena kompresinya tidak didukung" else null

    private fun snapshot(state: CopyState, errorMessage: String?): CopyProgress {
        val elapsedMillis = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1)
        return CopyProgress(
            jobId = job.jobId,
            currentName = currentName,
            copiedBytes = copiedBytes,
            totalBytes = totalBytes,
            copiedFiles = copiedFiles,
            totalFiles = totalFiles,
            skippedFiles = skippedFiles,
            bytesPerSecond = copiedBytes * 1000 / elapsedMillis,
            state = state,
            errorMessage = errorMessage,
            destinationName = job.destinationName,
        )
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20
        const val PUBLISH_INTERVAL_MILLIS = 250L
        const val MIME_OCTET_STREAM = "application/octet-stream"
    }
}
