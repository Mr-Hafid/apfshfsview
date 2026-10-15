import 'package:flutter/cupertino.dart';

import '../../bridge/reader_api.g.dart';
import '../../shared/file_type_resolver.dart';
import '../../shared/format_bytes.dart';
import '../../shared/format_date.dart';
import '../../shared/widgets/entry_thumbnail.dart';
import 'entry_sorting.dart';
import 'entry_subtitle.dart';
import 'finder_palette.dart';

const finderListRowHeight = 40.0;
const _indentPerLevel = 18.0;
const _dateColumnWidth = 150.0;
const _sizeColumnWidth = 84.0;
const _kindColumnWidth = 130.0;

class FinderListHeader extends StatelessWidget {
  const FinderListHeader({super.key, required this.sortField, required this.ascending, required this.onSort});

  final SortField sortField;
  final bool ascending;
  final ValueChanged<SortField> onSort;

  @override
  Widget build(BuildContext context) {
    final secondary = FinderPalette.secondaryText(context);
    Widget column(String label, SortField? field, {double? width, TextAlign align = TextAlign.left}) {
      final isActive = field == sortField;
      final content = Row(
        mainAxisAlignment: align == TextAlign.right ? MainAxisAlignment.end : MainAxisAlignment.start,
        children: [
          Flexible(
            child: Text(
              label,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                fontWeight: isActive ? FontWeight.w700 : FontWeight.w500,
                color: secondary,
              ),
            ),
          ),
          if (isActive) ...[
            const SizedBox(width: 3),
            Icon(ascending ? CupertinoIcons.chevron_up : CupertinoIcons.chevron_down, size: 11, color: secondary),
          ],
        ],
      );
      final tappable = field == null
          ? content
          : GestureDetector(behavior: HitTestBehavior.opaque, onTap: () => onSort(field), child: content);
      return width == null ? Expanded(child: tappable) : SizedBox(width: width, child: tappable);
    }

    return Container(
      height: 30,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: FinderPalette.hairline(context), width: 0.5)),
      ),
      child: Row(
        children: [
          const SizedBox(width: 24 + 26),
          column('Nama', SortField.name),
          column('Tanggal Diubah', SortField.date, width: _dateColumnWidth),
          column('Ukuran', SortField.size, width: _sizeColumnWidth, align: TextAlign.right),
          const SizedBox(width: 16),
          column('Jenis', null, width: _kindColumnWidth),
        ],
      ),
    );
  }
}

class FinderListRow extends StatelessWidget {
  const FinderListRow({
    super.key,
    required this.volumeHandle,
    required this.entry,
    required this.depth,
    required this.rowIndex,
    required this.isExpanded,
    required this.isSelected,
    required this.showsColumns,
    required this.onTap,
    required this.onLongPress,
    required this.onToggleExpanded,
  });

  final int volumeHandle;
  final FileEntryMessage entry;
  final int depth;
  final int rowIndex;
  final bool isExpanded;
  final bool isSelected;
  final bool showsColumns;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onToggleExpanded;

  @override
  Widget build(BuildContext context) {
    final isFolder = entry.kind == NodeKindMessage.directory;
    final primaryText = isSelected ? CupertinoColors.white : CupertinoColors.label.resolveFrom(context);
    final secondaryText = isSelected ? const Color(0xE6FFFFFF) : FinderPalette.secondaryText(context);
    final background = isSelected
        ? FinderPalette.accent(context)
        : rowIndex.isOdd
        ? FinderPalette.stripe(context)
        : FinderPalette.window(context);
    final sizeText = isFolder ? folderContentLabel(entry) : formatBytes(entry.size);
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      onLongPress: onLongPress,
      child: Container(
        height: finderListRowHeight,
        margin: isSelected ? const EdgeInsets.symmetric(horizontal: 6) : EdgeInsets.zero,
        padding: EdgeInsets.only(left: isSelected ? 10 : 16, right: isSelected ? 10 : 16),
        decoration: BoxDecoration(color: background, borderRadius: isSelected ? BorderRadius.circular(6) : null),
        child: Opacity(
          opacity: entry.isReadable ? 1 : 0.5,
          child: Row(
            children: [
              SizedBox(width: depth * _indentPerLevel),
              SizedBox(
                width: 24,
                child: isFolder
                    ? GestureDetector(
                        key: ValueKey('disclosure-${entry.nodeId}'),
                        behavior: HitTestBehavior.opaque,
                        onTap: onToggleExpanded,
                        child: SizedBox(
                          height: finderListRowHeight,
                          child: AnimatedRotation(
                            turns: isExpanded ? 0.25 : 0,
                            duration: const Duration(milliseconds: 150),
                            child: Icon(CupertinoIcons.play_fill, size: 9, color: secondaryText),
                          ),
                        ),
                      )
                    : null,
              ),
              SizedBox.square(
                dimension: 22,
                child: EntryThumbnail(volumeHandle: volumeHandle, entry: entry),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  entry.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 14, color: primaryText),
                ),
              ),
              if (showsColumns) ...[
                SizedBox(
                  width: _dateColumnWidth,
                  child: Text(
                    formatDateTime(entry.modifiedAtMillis),
                    style: TextStyle(fontSize: 12.5, color: secondaryText),
                  ),
                ),
                SizedBox(
                  width: _sizeColumnWidth,
                  child: Text(
                    isFolder ? '—' : formatBytes(entry.size),
                    textAlign: TextAlign.right,
                    style: TextStyle(fontSize: 12.5, color: secondaryText),
                  ),
                ),
                const SizedBox(width: 16),
                SizedBox(
                  width: _kindColumnWidth,
                  child: Text(
                    entryKindLabel(entry),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 12.5, color: secondaryText),
                  ),
                ),
              ] else
                Text(sizeText, style: TextStyle(fontSize: 12.5, color: secondaryText)),
            ],
          ),
        ),
      ),
    );
  }
}

class FinderListLoadingRow extends StatelessWidget {
  const FinderListLoadingRow({super.key, required this.depth});

  final int depth;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: finderListRowHeight,
      child: Row(
        children: [
          SizedBox(width: 16 + depth * _indentPerLevel + 24),
          const CupertinoActivityIndicator(radius: 7),
          const SizedBox(width: 8),
          Text('Memuat…', style: TextStyle(fontSize: 13, color: FinderPalette.secondaryText(context))),
        ],
      ),
    );
  }
}
