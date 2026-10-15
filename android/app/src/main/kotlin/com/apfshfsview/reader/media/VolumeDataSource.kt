package com.apfshfsview.reader.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.apfshfsview.core.fs.FileReader
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.core.fs.ReadAheadFileReader

@OptIn(UnstableApi::class)
class VolumeDataSource(private val fileSystem: FileSystem) : BaseDataSource(true) {
    private var reader: FileReader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var isOpened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val nodeId = dataSpec.uri.lastPathSegment?.toLongOrNull()
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        val opened = ReadAheadFileReader(fileSystem.openReader(nodeId), READ_AHEAD_BYTES)
        reader = opened
        position = dataSpec.position
        if (position > opened.size) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else opened.size - position
        isOpened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val currentReader = reader ?: return C.RESULT_END_OF_INPUT
        val count = currentReader.read(position, buffer, offset, minOf(length.toLong(), bytesRemaining).toInt())
        if (count <= 0) return C.RESULT_END_OF_INPUT
        position += count
        bytesRemaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        reader?.close()
        reader = null
        uri = null
        if (isOpened) {
            isOpened = false
            transferEnded()
        }
    }

    companion object {
        private const val READ_AHEAD_BYTES = 1 shl 20

        fun uriFor(nodeId: Long): Uri = Uri.parse("volume://node/$nodeId")
    }
}
