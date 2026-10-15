const _monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];

String formatDate(int millis) {
  if (millis <= 0) return '—';
  final date = DateTime.fromMillisecondsSinceEpoch(millis);
  return '${date.day} ${_monthNames[date.month - 1]} ${date.year}';
}

String formatDateTime(int millis) {
  if (millis <= 0) return '—';
  final date = DateTime.fromMillisecondsSinceEpoch(millis);
  final hour = date.hour.toString().padLeft(2, '0');
  final minute = date.minute.toString().padLeft(2, '0');
  return '${formatDate(millis)} $hour.$minute';
}
