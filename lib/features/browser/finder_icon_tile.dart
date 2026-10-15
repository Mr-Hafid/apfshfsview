import 'package:flutter/cupertino.dart';

import '../../bridge/reader_api.g.dart';
import '../../shared/widgets/entry_thumbnail.dart';
import 'finder_palette.dart';

const _nameAreaHeight = 36.0;

class FinderIconTile extends StatelessWidget {
  const FinderIconTile({
    super.key,
    required this.volumeHandle,
    required this.entry,
    required this.isSelected,
    required this.onTap,
    required this.onLongPress,
  });

  final int volumeHandle;
  final FileEntryMessage entry;
  final bool isSelected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      onLongPress: onLongPress,
      child: Opacity(
        opacity: entry.isReadable ? 1 : 0.45,
        child: Column(
          children: [
            Expanded(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: isSelected ? FinderPalette.selectionTile(context) : null,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(8),
                  child: EntryThumbnail(volumeHandle: volumeHandle, entry: entry),
                ),
              ),
            ),
            const SizedBox(height: 4),
            SizedBox(
              height: _nameAreaHeight,
              child: Align(
                alignment: Alignment.topCenter,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: isSelected ? FinderPalette.accent(context) : null,
                    borderRadius: BorderRadius.circular(5),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
                    child: Text(
                      entry.name,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 12.5,
                        height: 1.25,
                        color: isSelected ? CupertinoColors.white : CupertinoColors.label.resolveFrom(context),
                        fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
