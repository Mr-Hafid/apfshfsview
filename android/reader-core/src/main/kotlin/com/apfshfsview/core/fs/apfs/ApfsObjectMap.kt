package com.apfshfsview.core.fs.apfs

import com.apfshfsview.core.binary.LittleEndian
import com.apfshfsview.core.fs.CorruptStructureException
import com.apfshfsview.core.fs.UnsupportedEncryptionException

class ApfsObjectMap(container: ApfsContainer, objectMapAddress: Long) {
    private val tree = ApfsBTree(container, LittleEndian.u64(container.readObject(objectMapAddress), 48)) { it }
    private val resolved = HashMap<Long, Long>()

    fun resolve(oid: Long): Long {
        synchronized(resolved) { resolved[oid] }?.let { return it }
        val address = lookup(oid)
        synchronized(resolved) { resolved[oid] = address }
        return address
    }

    private fun lookup(oid: Long): Long {
        val entry = tree.entriesFrom { block, keyOffset, _ -> LittleEndian.u64(block, keyOffset).compareTo(oid) }
            .takeWhile { LittleEndian.u64(it.block, it.keyOffset) == oid }
            .lastOrNull() ?: throw CorruptStructureException("APFS object $oid missing from object map")
        if (LittleEndian.u32(entry.block, entry.valueOffset) and ENCRYPTED_FLAG != 0L) {
            throw UnsupportedEncryptionException()
        }
        return LittleEndian.u64(entry.block, entry.valueOffset + 8)
    }

    private companion object {
        const val ENCRYPTED_FLAG = 0x4L
    }
}
