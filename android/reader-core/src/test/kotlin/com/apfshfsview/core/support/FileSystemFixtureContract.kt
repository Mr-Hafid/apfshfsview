package com.apfshfsview.core.support

import com.apfshfsview.core.block.BlockDevice
import com.apfshfsview.core.block.CachedBlockDevice
import com.apfshfsview.core.block.FileBlockDevice
import com.apfshfsview.core.fs.FileNode
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.NodeKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class FileSystemFixtureContract {
    abstract val fixtureName: String

    open val expectedVolumeName: String = "Fixture"

    abstract fun openFileSystem(device: BlockDevice): FileSystem

    private var device: FileBlockDevice? = null
    protected lateinit var fileSystem: FileSystem
    private lateinit var manifest: List<ManifestEntry>
    private lateinit var tree: Map<String, FileNode>

    @BeforeEach
    fun openFixture() {
        Fixtures.assumePresent(fixtureName)
        val opened = FileBlockDevice(Fixtures.image(fixtureName))
        device = opened
        fileSystem = openFileSystem(CachedBlockDevice(opened))
        manifest = Fixtures.manifest(fixtureName)
        tree = FixtureTree.walkDataFolder(fileSystem)
    }

    @AfterEach
    fun closeFixture() {
        device?.close()
    }

    @Test
    fun `volume name and root stat use the volume label`() {
        assertEquals(expectedVolumeName, fileSystem.info.name)
        assertEquals(NodeKind.DIRECTORY, fileSystem.stat(fileSystem.rootId).kind)
    }

    @Test
    fun `reader lists exactly the manifest paths with matching kinds`() {
        assertEquals(manifest.map { it.path }.toSet(), tree.keys)
        manifest.forEach { entry -> assertEquals(entry.kind, tree.getValue(entry.path).kind, entry.path) }
    }

    @Test
    fun `file sizes and contents match the manifest`() {
        manifest.filter { it.kind == NodeKind.FILE }.forEach { entry ->
            val node = tree.getValue(entry.path)
            assertEquals(entry.size, node.size, entry.path)
            if (!node.isReadable) {
                assertTrue(node.isCompressed, "${entry.path} unreadable but not compressed")
                return@forEach
            }
            assertEquals(entry.sha256, FixtureTree.sha256(FixtureTree.readAll(fileSystem, node)), entry.path)
        }
    }

    @Test
    fun `compressed flag matches the manifest`() {
        manifest.filter { it.isCompressed }.forEach { entry -> assertTrue(tree.getValue(entry.path).isCompressed, entry.path) }
    }

    @Test
    fun `large folder lists every item and reports its child count`() {
        val many = tree.getValue("many")
        assertEquals(1200, fileSystem.listDirectory(many.id).size)
        if (many.childCount >= 0) assertEquals(1200, many.childCount)
    }

    @Test
    fun `random reads of a fragmented file match sequential content`() {
        val node = tree.getValue("fragmented.bin")
        val whole = FixtureTree.readAll(fileSystem, node)
        val random = Random(7)
        fileSystem.openReader(node.id).use { reader ->
            repeat(200) {
                val position = random.nextLong(whole.size.toLong())
                val length = minOf(random.nextInt(1, 300_000).toLong(), whole.size - position).toInt()
                val buffer = ByteArray(length)
                var copied = 0
                while (copied < length) copied += reader.read(position + copied, buffer, copied, length - copied)
                assertContentEquals(whole.copyOfRange(position.toInt(), position.toInt() + length), buffer)
            }
        }
    }

    @Test
    fun `stat of a nested file returns its name and parent`() {
        val node = tree.getValue("nested/level2/level3/deep.txt")
        val stat = fileSystem.stat(node.id)
        assertEquals("deep.txt", stat.name)
        assertEquals(tree.getValue("nested/level2/level3").id, stat.parentId)
    }

    @Test
    fun `hard link reads the same content as its target`() {
        val link = tree["links/hardlink_to_hello.txt"] ?: return
        val target = tree.getValue("hello.txt")
        assertContentEquals(FixtureTree.readAll(fileSystem, target), FixtureTree.readAll(fileSystem, link))
    }
}
