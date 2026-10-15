import '../../bridge/reader_api.g.dart';
import '../../shared/format_bytes.dart';
import '../../shared/format_date.dart';

String entrySubtitle(FileEntryMessage entry) {
  final date = formatDate(entry.modifiedAtMillis);
  if (entry.kind == NodeKindMessage.directory) return '$date · ${entry.childCount} item';
  final size = '$date · ${formatBytes(entry.size)}';
  return entry.isReadable ? size : '$size · kompresi tidak didukung';
}

String entryShortDetail(FileEntryMessage entry) =>
    entry.kind == NodeKindMessage.directory ? '${entry.childCount} item' : formatBytes(entry.size);
