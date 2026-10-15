package com.apfshfsview.core.naming

import kotlin.test.Test
import kotlin.test.assertEquals

class UniqueNamesTest {
    @Test
    fun `free name is kept`() {
        assertEquals("photo.jpg", UniqueNames.resolve("photo.jpg") { false })
    }

    @Test
    fun `taken names get a numbered suffix before the extension`() {
        val taken = setOf("photo.jpg", "photo (1).jpg")
        assertEquals("photo (2).jpg", UniqueNames.resolve("photo.jpg") { it in taken })
    }

    @Test
    fun `names without extension and dotfiles get suffix at the end`() {
        assertEquals("Folder (1)", UniqueNames.resolve("Folder") { it == "Folder" })
        assertEquals(".env (1)", UniqueNames.resolve(".env") { it == ".env" })
    }

    @Test
    fun `slashes and empty names are sanitized`() {
        assertEquals("a_b", UniqueNames.sanitize("a/b"))
        assertEquals("tanpa_nama", UniqueNames.sanitize("   "))
    }
}
