import 'dart:math';
import 'dart:typed_data';

import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../file_type_resolver.dart';
import '../thumbnail_cache.dart';
import 'finder_icons.dart';

const _thumbnailCategories = {FileCategory.image, FileCategory.video};

class EntryThumbnail extends ConsumerStatefulWidget {
  const EntryThumbnail({super.key, required this.volumeHandle, required this.entry});

  final int volumeHandle;
  final FileEntryMessage entry;

  @override
  ConsumerState<EntryThumbnail> createState() => _EntryThumbnailState();
}

class _EntryThumbnailState extends ConsumerState<EntryThumbnail> {
  Future<Uint8List?>? _thumbnailFuture;

  @override
  void initState() {
    super.initState();
    if (_thumbnailCategories.contains(categorizeEntry(widget.entry)) && widget.entry.isReadable) {
      _thumbnailFuture = ref.read(thumbnailCacheProvider).load(widget.volumeHandle, widget.entry.nodeId);
    }
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final side = min(constraints.maxWidth, constraints.maxHeight);
        final glyph = Center(
          child: FinderEntryGlyph(entry: widget.entry, size: side),
        );
        final future = _thumbnailFuture;
        if (future == null) return glyph;
        return FutureBuilder<Uint8List?>(
          future: future,
          builder: (context, snapshot) {
            final bytes = snapshot.data;
            if (bytes == null) return glyph;
            return _FramedThumbnail(
              bytes: bytes,
              side: side,
              isVideo: categorizeEntry(widget.entry) == FileCategory.video,
              fallback: glyph,
            );
          },
        );
      },
    );
  }
}

class _FramedThumbnail extends StatelessWidget {
  const _FramedThumbnail({required this.bytes, required this.side, required this.isVideo, required this.fallback});

  final Uint8List bytes;
  final double side;
  final bool isVideo;
  final Widget fallback;

  @override
  Widget build(BuildContext context) {
    final framed = DecoratedBox(
      decoration: const BoxDecoration(
        color: CupertinoColors.white,
        boxShadow: [BoxShadow(color: Color(0x40000000), blurRadius: 6, offset: Offset(0, 1.5))],
      ),
      child: Padding(
        padding: const EdgeInsets.all(5),
        child: Image.memory(bytes, gaplessPlayback: true, errorBuilder: (_, _, _) => const SizedBox.shrink()),
      ),
    );
    return Center(
      child: SizedBox.square(
        dimension: side * 0.92,
        child: Stack(
          alignment: Alignment.center,
          children: [
            FittedBox(fit: BoxFit.contain, child: framed),
            if (isVideo) _PlayBadge(size: max(side * 0.3, 14)),
          ],
        ),
      ),
    );
  }
}

class _PlayBadge extends StatelessWidget {
  const _PlayBadge({required this.size});

  final double size;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(color: Color(0x8C000000), shape: BoxShape.circle),
      child: SizedBox.square(
        dimension: size,
        child: Icon(CupertinoIcons.play_fill, size: size * 0.5, color: CupertinoColors.white),
      ),
    );
  }
}
