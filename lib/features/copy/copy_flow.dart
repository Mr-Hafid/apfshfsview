import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../../shared/format_bytes.dart';
import '../../shared/widgets/toast_banner.dart';

enum _CopyDecision { copy, changeFolder, cancel }

String describeCopyItems(List<FileEntryMessage> entries) {
  final hasFolder = entries.any((entry) => entry.kind == NodeKindMessage.directory);
  if (hasFolder) return '${entries.length} item';
  final totalBytes = entries.fold<int>(0, (sum, entry) => sum + entry.size);
  return '${entries.length} item (${formatBytes(totalBytes)})';
}

Future<void> startCopyFlow(
  BuildContext context,
  WidgetRef ref, {
  required int volumeHandle,
  required List<FileEntryMessage> entries,
}) async {
  if (entries.isEmpty) return;
  final repository = ref.read(readerRepositoryProvider);
  try {
    var destinationName = await repository.savedDestinationName() ?? await repository.pickDestination();
    while (destinationName != null) {
      if (!context.mounted) return;
      final decision = await showCupertinoDialog<_CopyDecision>(
        context: context,
        builder: (dialogContext) => CupertinoAlertDialog(
          title: Text('Salin ${describeCopyItems(entries)}?'),
          content: Text('Ke folder "$destinationName" di HP.'),
          actions: [
            CupertinoDialogAction(
              isDefaultAction: true,
              onPressed: () => Navigator.pop(dialogContext, _CopyDecision.copy),
              child: const Text('Salin'),
            ),
            CupertinoDialogAction(
              onPressed: () => Navigator.pop(dialogContext, _CopyDecision.changeFolder),
              child: const Text('Ganti Folder'),
            ),
            CupertinoDialogAction(
              onPressed: () => Navigator.pop(dialogContext, _CopyDecision.cancel),
              child: const Text('Batal'),
            ),
          ],
        ),
      );
      switch (decision) {
        case _CopyDecision.copy:
          await repository.startCopy(volumeHandle, entries.map((entry) => entry.nodeId).toList());
          if (context.mounted) ToastBanner.show(context, 'Menyalin ${entries.length} item…');
          return;
        case _CopyDecision.changeFolder:
          destinationName = await repository.pickDestination() ?? destinationName;
        case _CopyDecision.cancel:
        case null:
          return;
      }
    }
  } catch (error) {
    if (context.mounted) ToastBanner.show(context, describeReaderError(error));
  }
}
