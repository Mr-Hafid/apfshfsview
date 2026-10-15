import 'dart:typed_data';

import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../file_icon.dart';
import '../file_type_resolver.dart';
import '../thumbnail_cache.dart';

class EntryThumbnail extends ConsumerStatefulWidget {
  const EntryThumbnail({super.key, required this.volumeHandle, required this.entry, required this.iconSize});

  final int volumeHandle;
  final FileEntryMessage entry;
  final double iconSize;

  @override
  ConsumerState<EntryThumbnail> createState() => _EntryThumbnailState();
}

class _EntryThumbnailState extends ConsumerState<EntryThumbnail> {
  Future<Uint8List?>? _thumbnailFuture;

  @override
  void initState() {
    super.initState();
    if (categorizeEntry(widget.entry) == FileCategory.image && widget.entry.isReadable) {
      _thumbnailFuture = ref.read(thumbnailCacheProvider).load(widget.volumeHandle, widget.entry.nodeId);
    }
  }

  @override
  Widget build(BuildContext context) {
    final category = categorizeEntry(widget.entry);
    final icon = Center(
      child: Icon(
        iconForCategory(category),
        size: widget.iconSize,
        color: colorForCategory(category).resolveFrom(context),
      ),
    );
    final future = _thumbnailFuture;
    if (future == null) return icon;
    return FutureBuilder<Uint8List?>(
      future: future,
      builder: (context, snapshot) {
        final bytes = snapshot.data;
        if (bytes == null) return icon;
        return ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Image.memory(
            bytes,
            fit: BoxFit.cover,
            width: double.infinity,
            height: double.infinity,
            gaplessPlayback: true,
            errorBuilder: (_, _, _) => icon,
          ),
        );
      },
    );
  }
}
