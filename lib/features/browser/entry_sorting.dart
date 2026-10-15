import '../../bridge/reader_api.g.dart';
import '../../shared/natural_compare.dart';

enum SortField { name, date, size }

String sortFieldLabel(SortField field) => switch (field) {
  SortField.name => 'Nama',
  SortField.date => 'Tanggal',
  SortField.size => 'Ukuran',
};

List<FileEntryMessage> sortEntries(Iterable<FileEntryMessage> entries, SortField field, bool ascending) {
  final sorted = entries.toList();
  int compareWithinGroup(FileEntryMessage a, FileEntryMessage b) {
    final primary = switch (field) {
      SortField.name => compareNatural(a.name, b.name),
      SortField.date => a.modifiedAtMillis.compareTo(b.modifiedAtMillis),
      SortField.size => a.size.compareTo(b.size),
    };
    final result = primary != 0 ? primary : compareNatural(a.name, b.name);
    return ascending ? result : -result;
  }

  sorted.sort((a, b) {
    final aIsFolder = a.kind == NodeKindMessage.directory;
    final bIsFolder = b.kind == NodeKindMessage.directory;
    if (aIsFolder != bIsFolder) return aIsFolder ? -1 : 1;
    return compareWithinGroup(a, b);
  });
  return sorted;
}
