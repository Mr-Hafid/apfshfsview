import 'package:flutter/cupertino.dart';

import '../../bridge/reader_api.g.dart';
import '../../shared/widgets/entry_thumbnail.dart';
import 'entry_subtitle.dart';
import 'selection_mark.dart';

class EntryListTile extends StatelessWidget {
  const EntryListTile({
    super.key,
    required this.volumeHandle,
    required this.entry,
    required this.isSelecting,
    required this.isSelected,
    required this.onTap,
  });

  final int volumeHandle;
  final FileEntryMessage entry;
  final bool isSelecting;
  final bool isSelected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final trailing = isSelecting
        ? SelectionMark(isSelected: isSelected)
        : entry.kind == NodeKindMessage.directory
            ? const CupertinoListTileChevron()
            : null;
    return Opacity(
      opacity: entry.isReadable ? 1 : 0.5,
      child: CupertinoListTile(
        leadingSize: 40,
        leading: SizedBox.square(
          dimension: 40,
          child: EntryThumbnail(volumeHandle: volumeHandle, entry: entry, iconSize: 28),
        ),
        title: Text(entry.name, overflow: TextOverflow.ellipsis),
        subtitle: Text(entrySubtitle(entry)),
        trailing: trailing,
        onTap: onTap,
      ),
    );
  }
}
