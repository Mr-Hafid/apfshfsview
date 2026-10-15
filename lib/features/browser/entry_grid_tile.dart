import 'package:flutter/cupertino.dart';

import '../../bridge/reader_api.g.dart';
import '../../shared/format_date.dart';
import '../../shared/widgets/entry_thumbnail.dart';
import 'entry_subtitle.dart';
import 'selection_mark.dart';

class EntryGridTile extends StatelessWidget {
  const EntryGridTile({
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
    final secondary = CupertinoColors.secondaryLabel.resolveFrom(context);
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Opacity(
        opacity: entry.isReadable ? 1 : 0.5,
        child: Column(
          children: [
            AspectRatio(
              aspectRatio: 1,
              child: Stack(
                children: [
                  Positioned.fill(child: EntryThumbnail(volumeHandle: volumeHandle, entry: entry, iconSize: 56)),
                  if (isSelecting) Positioned(right: 4, bottom: 4, child: SelectionMark(isSelected: isSelected)),
                ],
              ),
            ),
            const SizedBox(height: 6),
            Text(
              entry.name,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 13),
            ),
            Text(
              formatDate(entry.modifiedAtMillis),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 11, color: secondary),
            ),
            Text(
              entryShortDetail(entry),
              maxLines: 1,
              style: TextStyle(fontSize: 11, color: secondary),
            ),
          ],
        ),
      ),
    );
  }
}
