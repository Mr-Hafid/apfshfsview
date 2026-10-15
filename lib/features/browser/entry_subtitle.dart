import '../../bridge/reader_api.g.dart';
import '../../shared/format_bytes.dart';
import '../../shared/format_date.dart';

String entrySubtitle(FileEntryMessage entry) {
  final date = formatDate(entry.modifiedAtMillis);
  if (entry.kind == NodeKindMessage.directory) return '$date · ${folderContentLabel(entry)}';
  final size = '$date · ${formatBytes(entry.size)}';
  return entry.isReadable ? size : '$size · kompresi tidak didukung';
}

String entryShortDetail(FileEntryMessage entry) =>
    entry.kind == NodeKindMessage.directory ? folderContentLabel(entry) : formatBytes(entry.size);

String folderContentLabel(FileEntryMessage folder) => folder.childCount >= 0 ? '${folder.childCount} item' : 'Folder';
