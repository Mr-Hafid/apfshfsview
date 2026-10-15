const _units = ['KB', 'MB', 'GB', 'TB', 'PB'];

String formatBytes(int bytes) {
  if (bytes < 1000) return '$bytes B';
  var value = bytes / 1000;
  var unitIndex = 0;
  while (value >= 999.95 && unitIndex < _units.length - 1) {
    value /= 1000;
    unitIndex++;
  }
  final text = value >= 100 ? value.round().toString() : value.toStringAsFixed(1).replaceAll('.', ',');
  return '$text ${_units[unitIndex]}';
}

String formatByteCount(int bytes) {
  final digits = bytes.toString();
  final buffer = StringBuffer();
  for (var index = 0; index < digits.length; index++) {
    if (index > 0 && (digits.length - index) % 3 == 0) buffer.write('.');
    buffer.write(digits[index]);
  }
  return buffer.toString();
}
