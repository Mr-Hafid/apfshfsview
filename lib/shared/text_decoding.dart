import 'dart:convert';
import 'dart:typed_data';

String decodePreviewText(Uint8List bytes, {required bool isTruncated}) {
  final decoded = _decodeUtf8(bytes, isTruncated: isTruncated) ?? latin1.decode(bytes);
  return decoded.startsWith('﻿') ? decoded.substring(1) : decoded;
}

String? _decodeUtf8(Uint8List bytes, {required bool isTruncated}) {
  final maxTrim = isTruncated ? 3 : 0;
  for (var trim = 0; trim <= maxTrim && trim <= bytes.length; trim++) {
    try {
      return utf8.decode(Uint8List.sublistView(bytes, 0, bytes.length - trim));
    } on FormatException {
      continue;
    }
  }
  return null;
}
