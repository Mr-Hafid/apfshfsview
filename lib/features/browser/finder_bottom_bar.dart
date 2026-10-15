import 'package:flutter/cupertino.dart';

import '../../shared/widgets/finder_icons.dart';
import 'browser_location.dart';
import 'finder_palette.dart';

class FinderBottomBar extends StatelessWidget {
  const FinderBottomBar({
    super.key,
    required this.trail,
    required this.itemCount,
    required this.onSegmentTap,
    required this.isSelecting,
    required this.selectedCount,
    required this.onCopy,
    required this.onInfo,
  });

  final List<PathSegment> trail;
  final int? itemCount;
  final ValueChanged<int> onSegmentTap;
  final bool isSelecting;
  final int selectedCount;
  final VoidCallback onCopy;
  final VoidCallback onInfo;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: FinderPalette.window(context),
        border: Border(top: BorderSide(color: FinderPalette.hairline(context), width: 0.5)),
      ),
      child: Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.paddingOf(context).bottom),
        child: SizedBox(
          height: isSelecting ? 52 : 36,
          child: isSelecting ? _buildActions(context) : _buildPath(context),
        ),
      ),
    );
  }

  Widget _buildActions(BuildContext context) {
    Widget action(IconData icon, String label, VoidCallback? onTap) => Expanded(
      child: CupertinoButton(
        onPressed: onTap,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 20),
            const SizedBox(width: 6),
            Text(label, style: const TextStyle(fontSize: 15)),
          ],
        ),
      ),
    );
    return Row(
      children: [
        action(CupertinoIcons.square_arrow_down, 'Salin ke HP', selectedCount > 0 ? onCopy : null),
        action(CupertinoIcons.info_circle, 'Info', selectedCount == 1 ? onInfo : null),
      ],
    );
  }

  Widget _buildSegment(BuildContext context, int index, Color secondary) {
    final isCurrent = index == trail.length - 1;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: isCurrent ? null : () => onSegmentTap(index),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 5),
        child: Row(
          children: [
            if (index == 0)
              Icon(CupertinoIcons.tray_2_fill, size: 14, color: secondary)
            else
              const FinderFolderIcon(size: 16),
            const SizedBox(width: 4),
            Text(
              trail[index].name,
              style: TextStyle(
                fontSize: 12.5,
                color: isCurrent ? CupertinoColors.label.resolveFrom(context) : secondary,
                fontWeight: isCurrent ? FontWeight.w600 : FontWeight.w400,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPath(BuildContext context) {
    final secondary = FinderPalette.secondaryText(context);
    final count = itemCount;
    return Row(
      children: [
        Expanded(
          child: LayoutBuilder(
            builder: (context, constraints) => SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              reverse: true,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: ConstrainedBox(
                constraints: BoxConstraints(minWidth: (constraints.maxWidth - 24).clamp(0, double.infinity)),
                child: Row(
                  children: [
                    for (var index = 0; index < trail.length; index++) ...[
                      if (index > 0) Icon(CupertinoIcons.chevron_right, size: 10, color: secondary),
                      _buildSegment(context, index, secondary),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
        if (count != null)
          Padding(
            padding: const EdgeInsets.only(right: 14),
            child: Text('$count item', style: TextStyle(fontSize: 12.5, color: secondary)),
          ),
      ],
    );
  }
}
