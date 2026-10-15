package com.apfshfsview.core.support

import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import java.security.MessageDigest
import kotlin.test.fail

object FixtureTree {
    fun walkDataFolder(fileSystem: FileSystem): Map<String, FileNode> {
        val dataFolder = fileSystem.listDirectory(fileSystem.rootId).firstOrNull { it.name == "data" }
            ?: fail("data folder missing")
        val result = linkedMapOf<String, FileNode>()
        fun visit(folder: FileNode, prefix: String) {
            fileSystem.listDirectory(folder.id).forEach { child ->
                val path = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                result[path] = child
                if (child.kind == NodeKind.DIRECTORY) visit(child, path)
            }
        }
        visit(dataFolder, "")
        return result
    }

    fun readAll(fileSystem: FileSystem, node: FileNode): ByteArray =
        fileSystem.openReader(node.id).use { reader ->
            val output = ByteArray(reader.size.toInt())
            var position = 0
            while (position < output.size) {
                val count = reader.read(position.toLong(), output, position, minOf(1 shl 20, output.size - position))
                if (count <= 0) fail("Unexpected end of ${node.name} at $position")
                position += count
            }
            output
        }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
