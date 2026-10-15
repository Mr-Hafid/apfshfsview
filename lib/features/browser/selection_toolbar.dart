import 'package:flutter/cupertino.dart';

class SelectionToolbar extends StatelessWidget {
  const SelectionToolbar({super.key, required this.selectedCount, required this.onCopy, required this.onInfo});

  final int selectedCount;
  final VoidCallback onCopy;
  final VoidCallback onInfo;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: CupertinoTheme.of(context).barBackgroundColor,
        border: Border(top: BorderSide(color: CupertinoColors.separator.resolveFrom(context), width: 0.5)),
      ),
      child: SizedBox(
        height: 50,
        child: Row(
          children: [
            CupertinoButton(
              onPressed: selectedCount > 0 ? onCopy : null,
              child: const Icon(CupertinoIcons.square_arrow_down),
            ),
            Expanded(
              child: Center(
                child: Text('$selectedCount dipilih', style: const TextStyle(fontWeight: FontWeight.w600)),
              ),
            ),
            CupertinoButton(
              onPressed: selectedCount == 1 ? onInfo : null,
              child: const Icon(CupertinoIcons.info_circle),
            ),
          ],
        ),
      ),
    );
  }
}
