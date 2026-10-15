import 'package:flutter_test/flutter_test.dart';
import 'package:reader/shared/format_bytes.dart';
import 'package:reader/shared/format_duration.dart';
import 'package:reader/shared/natural_compare.dart';

void main() {
  test('formatBytes uses decimal units with comma separator', () {
    expect(formatBytes(0), '0 B');
    expect(formatBytes(999), '999 B');
    expect(formatBytes(1000), '1,0 KB');
    expect(formatBytes(1536), '1,5 KB');
    expect(formatBytes(123456789), '123 MB');
    expect(formatBytes(999999), '1,0 MB');
    expect(formatBytes(2500000000000), '2,5 TB');
  });

  test('formatByteCount groups thousands with dots', () {
    expect(formatByteCount(0), '0');
    expect(formatByteCount(1234567), '1.234.567');
  });

  test('formatPlaybackTime renders minutes and hours', () {
    expect(formatPlaybackTime(0), '0:00');
    expect(formatPlaybackTime(65000), '1:05');
    expect(formatPlaybackTime(3725000), '1:02:05');
  });

  test('compareNatural orders numbers by value and ignores case', () {
    final names = ['item 10', 'item 2', 'Item 1', 'alpha'];
    names.sort(compareNatural);
    expect(names, ['alpha', 'Item 1', 'item 2', 'item 10']);
  });
}
