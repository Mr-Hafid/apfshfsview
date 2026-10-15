import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../browser/browser_location.dart';
import '../copy/copy_flow.dart';
import '../info/file_info_screen.dart';

class PreviewNavigationBar extends ConsumerWidget implements ObstructingPreferredSizeWidget {
  const PreviewNavigationBar({
    super.key,
    required this.volume,
    required this.entry,
    required this.path,
    required this.middle,
    this.extraActions = const [],
  });

  final VolumeContext volume;
  final FileEntryMessage entry;
  final String path;
  final Widget middle;
  final List<Widget> extraActions;

  @override
  Size get preferredSize => const Size.fromHeight(kMinInteractiveDimensionCupertino);

  @override
  bool shouldFullyObstruct(BuildContext context) => true;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return CupertinoNavigationBar(
      leading: CupertinoButton(
        padding: EdgeInsets.zero,
        onPressed: () => Navigator.of(context).pop(),
        child: const Text('Selesai', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      middle: middle,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          ...extraActions,
          CupertinoButton(
            padding: const EdgeInsets.symmetric(horizontal: 6),
            onPressed: () => Navigator.of(context).push(fileInfoRoute(volume: volume, entry: entry, path: path)),
            child: const Icon(CupertinoIcons.info),
          ),
          CupertinoButton(
            padding: const EdgeInsets.symmetric(horizontal: 6),
            onPressed: () => startCopyFlow(context, ref, volumeHandle: volume.handle, entries: [entry]),
            child: const Icon(CupertinoIcons.square_arrow_down),
          ),
        ],
      ),
    );
  }
}
