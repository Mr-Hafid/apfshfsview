import 'package:flutter/cupertino.dart';

class SelectionMark extends StatelessWidget {
  const SelectionMark({super.key, required this.isSelected});

  final bool isSelected;

  @override
  Widget build(BuildContext context) {
    final primary = CupertinoTheme.of(context).primaryColor;
    return DecoratedBox(
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: isSelected ? primary : CupertinoColors.systemBackground.resolveFrom(context).withValues(alpha: 0.8),
        border: Border.all(color: isSelected ? primary : CupertinoColors.systemGrey2.resolveFrom(context), width: 1.5),
      ),
      child: SizedBox.square(
        dimension: 24,
        child: isSelected ? const Icon(CupertinoIcons.checkmark, size: 15, color: CupertinoColors.white) : null,
      ),
    );
  }
}
