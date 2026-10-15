import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../bridge/reader_api.g.dart';
import 'entry_subtitle.dart';

enum EntryAction { open, copyToPhone, copyName, info, select }

Future<EntryAction?> showEntryActions(BuildContext context, FileEntryMessage entry) {
  HapticFeedback.mediumImpact();
  final isFolder = entry.kind == NodeKindMessage.directory;
  return showCupertinoModalPopup<EntryAction>(
    context: context,
    builder: (sheetContext) {
      Widget action(EntryAction value, IconData icon, String label) => CupertinoActionSheetAction(
        onPressed: () => Navigator.pop(sheetContext, value),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [Icon(icon, size: 20), const SizedBox(width: 10), Text(label)],
        ),
      );
      return CupertinoActionSheet(
        title: Text(entry.name, maxLines: 2, overflow: TextOverflow.ellipsis),
        message: Text(entrySubtitle(entry)),
        actions: [
          action(
            EntryAction.open,
            isFolder ? CupertinoIcons.folder_open : CupertinoIcons.eye,
            isFolder ? 'Buka Folder' : 'Buka',
          ),
          action(EntryAction.copyToPhone, CupertinoIcons.square_arrow_down, 'Salin ke HP'),
          action(EntryAction.copyName, CupertinoIcons.doc_on_clipboard, 'Salin Nama'),
          action(EntryAction.info, CupertinoIcons.info_circle, 'Info'),
          action(EntryAction.select, CupertinoIcons.checkmark_circle, 'Pilih'),
        ],
        cancelButton: CupertinoActionSheetAction(
          isDefaultAction: true,
          onPressed: () => Navigator.pop(sheetContext),
          child: const Text('Batal'),
        ),
      );
    },
  );
}
