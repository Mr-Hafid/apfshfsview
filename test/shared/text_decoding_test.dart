import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:reader/shared/text_decoding.dart';

void main() {
  test('utf8 text is decoded and bom stripped', () {
    final bytes = Uint8List.fromList([0xEF, 0xBB, 0xBF, ...utf8.encode('halo café')]);
    expect(decodePreviewText(bytes, isTruncated: false), 'halo café');
  });

  test('truncated multibyte character keeps utf8 decoding', () {
    final full = utf8.encode('mie 🍜');
    final cut = Uint8List.fromList(full.sublist(0, full.length - 2));
    expect(decodePreviewText(cut, isTruncated: true), 'mie ');
  });

  test('invalid utf8 falls back to latin1', () {
    final bytes = Uint8List.fromList([0x63, 0x61, 0x66, 0xE9]);
    expect(decodePreviewText(bytes, isTruncated: false), 'café');
  });
}
