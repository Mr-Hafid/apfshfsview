import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pdfx/pdfx.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../../shared/widgets/empty_state.dart';
import '../browser/browser_location.dart';
import 'preview_navigation_bar.dart';

class PdfPreviewScreen extends ConsumerStatefulWidget {
  const PdfPreviewScreen({super.key, required this.volume, required this.entry, required this.path});

  final VolumeContext volume;
  final FileEntryMessage entry;
  final String path;

  @override
  ConsumerState<PdfPreviewScreen> createState() => _PdfPreviewScreenState();
}

class _PdfPreviewScreenState extends ConsumerState<PdfPreviewScreen> {
  PdfController? _controller;
  Object? _error;
  int _page = 1;
  int _pageCount = 0;

  @override
  void initState() {
    super.initState();
    _open(ref.read(readerRepositoryProvider));
  }

  Future<void> _open(ReaderRepository repository) async {
    try {
      final path = await repository.exportToPreviewCache(widget.volume.handle, widget.entry.nodeId);
      if (!mounted) return;
      setState(() => _controller = PdfController(document: PdfDocument.openFile(path)));
    } catch (error) {
      if (mounted) setState(() => _error = error);
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.systemGroupedBackground,
      navigationBar: PreviewNavigationBar(
        volume: widget.volume,
        entry: widget.entry,
        path: widget.path,
        middle: Text(widget.entry.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      ),
      child: SafeArea(
        child: Stack(
          children: [
            Positioned.fill(child: _buildBody()),
            if (_pageCount > 0)
              Positioned(
                left: 0,
                right: 0,
                bottom: 16,
                child: Center(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: CupertinoColors.darkBackgroundGray.withValues(alpha: 0.85),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                      child: Text(
                        'Halaman $_page dari $_pageCount',
                        style: const TextStyle(color: CupertinoColors.white, fontSize: 13),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildBody() {
    final error = _error;
    if (error != null) {
      return EmptyState(
        icon: CupertinoIcons.exclamationmark_triangle,
        title: 'PDF tidak bisa dibuka',
        subtitle: describeReaderError(error),
      );
    }
    final controller = _controller;
    if (controller == null) return const Center(child: CupertinoActivityIndicator());
    return PdfView(
      controller: controller,
      scrollDirection: Axis.vertical,
      onDocumentLoaded: (document) => setState(() => _pageCount = document.pagesCount),
      onPageChanged: (page) => setState(() => _page = page),
      builders: PdfViewBuilders<DefaultBuilderOptions>(
        options: const DefaultBuilderOptions(),
        documentLoaderBuilder: (_) => const Center(child: CupertinoActivityIndicator()),
        pageLoaderBuilder: (_) => const Center(child: CupertinoActivityIndicator()),
        errorBuilder: (_, _) => const EmptyState(
          icon: CupertinoIcons.doc_richtext,
          title: 'PDF tidak bisa dibuka',
        ),
      ),
    );
  }
}
