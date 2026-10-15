import 'dart:typed_data';

import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../browser/browser_location.dart';
import 'preview_navigation_bar.dart';

class ImagePreviewScreen extends StatefulWidget {
  const ImagePreviewScreen({
    super.key,
    required this.volume,
    required this.images,
    required this.initialIndex,
    required this.pathOf,
  });

  final VolumeContext volume;
  final List<FileEntryMessage> images;
  final int initialIndex;
  final String Function(FileEntryMessage) pathOf;

  @override
  State<ImagePreviewScreen> createState() => _ImagePreviewScreenState();
}

class _ImagePreviewScreenState extends State<ImagePreviewScreen> {
  late final PageController _pageController = PageController(initialPage: widget.initialIndex);
  late int _index = widget.initialIndex;
  bool _chromeVisible = true;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final entry = widget.images[_index];
    final secondary = CupertinoColors.secondaryLabel.resolveFrom(context);
    return CupertinoPageScaffold(
      backgroundColor: _chromeVisible ? CupertinoColors.systemBackground.resolveFrom(context) : CupertinoColors.black,
      navigationBar: _chromeVisible
          ? PreviewNavigationBar(
              volume: widget.volume,
              entry: entry,
              path: widget.pathOf(entry),
              middle: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(entry.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 15)),
                  Text(
                    '${_index + 1} dari ${widget.images.length}',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w400, color: secondary),
                  ),
                ],
              ),
            )
          : null,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => setState(() => _chromeVisible = !_chromeVisible),
        child: PageView.builder(
          controller: _pageController,
          itemCount: widget.images.length,
          onPageChanged: (index) => setState(() => _index = index),
          itemBuilder: (context, index) => _ImagePage(
            key: ValueKey(widget.images[index].nodeId),
            volumeHandle: widget.volume.handle,
            entry: widget.images[index],
          ),
        ),
      ),
    );
  }
}

class _ImagePage extends ConsumerStatefulWidget {
  const _ImagePage({super.key, required this.volumeHandle, required this.entry});

  final int volumeHandle;
  final FileEntryMessage entry;

  @override
  ConsumerState<_ImagePage> createState() => _ImagePageState();
}

class _ImagePageState extends ConsumerState<_ImagePage> {
  static const _maxImageBytes = 64 * 1024 * 1024;

  Future<Uint8List>? _bytesFuture;

  @override
  void initState() {
    super.initState();
    if (widget.entry.size <= _maxImageBytes) {
      _bytesFuture = ref.read(readerRepositoryProvider).readPrefix(widget.volumeHandle, widget.entry.nodeId, widget.entry.size);
    }
  }

  @override
  Widget build(BuildContext context) {
    final future = _bytesFuture;
    if (future == null) return const _ImageMessage(text: 'Gambar terlalu besar (maksimal 64 MB).');
    return FutureBuilder<Uint8List>(
      future: future,
      builder: (context, snapshot) {
        if (snapshot.hasError) return _ImageMessage(text: describeReaderError(snapshot.error!));
        final bytes = snapshot.data;
        if (bytes == null) return const Center(child: CupertinoActivityIndicator());
        return InteractiveViewer(
          maxScale: 6,
          child: Center(
            child: Image.memory(
              bytes,
              fit: BoxFit.contain,
              gaplessPlayback: true,
              errorBuilder: (_, _, _) => const _ImageMessage(text: 'Format gambar tidak didukung.'),
            ),
          ),
        );
      },
    );
  }
}

class _ImageMessage extends StatelessWidget {
  const _ImageMessage({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Text(text, textAlign: TextAlign.center, style: const TextStyle(color: CupertinoColors.systemGrey)),
        ),
      );
}
