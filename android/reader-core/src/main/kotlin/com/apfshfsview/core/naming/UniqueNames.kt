package com.apfshfsview.core.naming

object UniqueNames {
    private const val FALLBACK_NAME = "tanpa_nama"

    fun sanitize(name: String): String =
        name.replace('/', '_').replace('\u0000', '_').trim().ifEmpty { FALLBACK_NAME }

    fun resolve(desiredName: String, isTaken: (String) -> Boolean): String {
        if (!isTaken(desiredName)) return desiredName
        val dotIndex = desiredName.lastIndexOf('.').takeIf { it > 0 }
        val baseName = dotIndex?.let { desiredName.substring(0, it) } ?: desiredName
        val extension = dotIndex?.let { desiredName.substring(it) } ?: ""
        var counter = 1
        while (true) {
            val candidate = "$baseName ($counter)$extension"
            if (!isTaken(candidate)) return candidate
            counter++
        }
    }
}
