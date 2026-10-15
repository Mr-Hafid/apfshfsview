package com.apfshfsview.reader.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class DestinationStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var treeUri: Uri?
        get() = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)?.takeIf(::hasWritePermission)
        set(value) {
            preferences.edit().putString(KEY_TREE_URI, value?.toString()).apply()
        }

    fun displayName(): String? = treeUri?.let { DocumentFile.fromTreeUri(context, it)?.name }

    private fun hasWritePermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private companion object {
        const val PREFERENCES_NAME = "copy_destination"
        const val KEY_TREE_URI = "tree_uri"
    }
}
