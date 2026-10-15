package com.apfshfsview.reader.export

import android.app.Activity
import android.content.Intent
import android.net.Uri
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DestinationPicker(private val activity: Activity) {
    private var pending: Continuation<Uri?>? = null

    suspend fun pick(): Uri? = suspendCoroutine { continuation ->
        pending?.resume(null)
        pending = continuation
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        activity.startActivityForResult(intent, REQUEST_CODE)
    }

    fun handleResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val uri = data?.data?.takeIf { resultCode == Activity.RESULT_OK }
        uri?.let {
            activity.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        pending?.resume(uri)
        pending = null
        return true
    }

    private companion object {
        const val REQUEST_CODE = 4107
    }
}
