package com.apfshfsview.core.fs

import java.io.IOException

sealed class ReaderException(message: String, val code: String) : IOException(message)

class CorruptStructureException(message: String) : ReaderException(message, "corrupt_structure")

class UnsupportedEncryptionException : ReaderException("Encrypted volumes are not supported", "unsupported_encryption")

class UnsupportedCompressionException(compressionType: Int) :
    ReaderException("Compression type $compressionType is not supported", "unsupported_compression")

class NodeNotFoundException(nodeId: Long) : ReaderException("Node $nodeId not found", "node_not_found")

class NotADirectoryException(nodeId: Long) : ReaderException("Node $nodeId is not a directory", "not_a_directory")

class NotAFileException(nodeId: Long) : ReaderException("Node $nodeId is not a file", "not_a_file")

class DeviceRangeUnsupportedException(byteOffset: Long) :
    ReaderException("Offset $byteOffset is beyond the 2 TB driver limit", "device_too_large")

class DeviceNotRespondingException(message: String) : ReaderException(message, "device_not_responding")
