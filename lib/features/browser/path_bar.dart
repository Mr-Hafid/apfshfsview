import 'package:flutter/cupertino.dart';

import 'browser_location.dart';

class PathBar extends StatelessWidget {
  const PathBar({super.key, required this.trail, required this.onSegmentTap});

  final List<PathSegment> trail;
  final ValueChanged<PathSegment> onSegmentTap;

  @override
  Widget build(BuildContext context) {
    final secondary = CupertinoColors.secondaryLabel.resolveFrom(context);
    final label = CupertinoColors.label.resolveFrom(context);
    final primary = CupertinoTheme.of(context).primaryColor;
    return SizedBox(
      height: 36,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        reverse: true,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        itemCount: trail.length,
        separatorBuilder: (_, _) => Icon(CupertinoIcons.chevron_right, size: 12, color: secondary),
        itemBuilder: (context, reversedIndex) {
          final index = trail.length - 1 - reversedIndex;
          final segment = trail[index];
          final isCurrent = index == trail.length - 1;
          return CupertinoButton(
            padding: const EdgeInsets.symmetric(horizontal: 6),
            minimumSize: const Size(28, 28),
            onPressed: isCurrent ? null : () => onSegmentTap(segment),
            child: Text(
              segment.name,
              style: TextStyle(fontSize: 14, color: isCurrent ? label : primary),
            ),
          );
        },
      ),
    );
  }
}
