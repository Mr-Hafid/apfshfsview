import 'package:flutter/cupertino.dart';

import '../../bridge/reader_api.g.dart';
import '../../shared/file_type_resolver.dart';
import '../../shared/format_bytes.dart';
import '../../shared/format_date.dart';
import '../../shared/widgets/entry_thumbnail.dart';
import '../browser/browser_location.dart';

Route<void> fileInfoRoute({required VolumeContext volume, required FileEntryMessage entry, required String path}) =>
    CupertinoPageRoute<void>(
      fullscreenDialog: true,
      builder: (_) => FileInfoScreen(volume: volume, entry: entry, path: path),
    );

class FileInfoScreen extends StatelessWidget {
  const FileInfoScreen({super.key, required this.volume, required this.entry, required this.path});

  final VolumeContext volume;
  final FileEntryMessage entry;
  final String path;

  @override
  Widget build(BuildContext context) {
    final isFolder = entry.kind == NodeKindMessage.directory;
    final compression = !entry.isCompressed
        ? 'Tidak'
        : entry.isReadable
            ? 'Ya'
            : 'Ya — tidak didukung';
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.systemGroupedBackground,
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Info'),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Selesai', style: TextStyle(fontWeight: FontWeight.w600)),
        ),
      ),
      child: SafeArea(
        child: ListView(
          children: [
            const SizedBox(height: 24),
            Center(
              child: SizedBox.square(
                dimension: 96,
                child: EntryThumbnail(volumeHandle: volume.handle, entry: entry, iconSize: 72),
              ),
            ),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Text(
                entry.name,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600),
              ),
            ),
            CupertinoListSection.insetGrouped(
              children: [
                _InfoRow(label: 'Jenis', value: categoryLabel(categorizeEntry(entry))),
                _InfoRow(
                  label: isFolder ? 'Isi' : 'Ukuran',
                  value: isFolder
                      ? '${entry.childCount} item'
                      : '${formatBytes(entry.size)} (${formatByteCount(entry.size)} byte)',
                ),
                _InfoRow(label: 'Dibuat', value: formatDateTime(entry.createdAtMillis)),
                _InfoRow(label: 'Diubah', value: formatDateTime(entry.modifiedAtMillis)),
                _InfoRow(label: 'Lokasi', value: path),
                _InfoRow(label: 'Format volume', value: volume.format),
                if (!isFolder) _InfoRow(label: 'Kompresi', value: compression),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 120, child: Text(label)),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: TextStyle(color: CupertinoColors.secondaryLabel.resolveFrom(context)),
            ),
          ),
        ],
      ),
    );
  }
}
