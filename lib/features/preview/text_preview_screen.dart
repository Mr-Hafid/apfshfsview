import 'dart:convert';
import 'dart:math';

import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../../shared/text_decoding.dart';
import '../../shared/widgets/empty_state.dart';
import '../browser/browser_location.dart';
import 'preview_navigation_bar.dart';

class TextPreviewScreen extends ConsumerStatefulWidget {
  const TextPreviewScreen({super.key, required this.volume, required this.entry, required this.path});

  final VolumeContext volume;
  final FileEntryMessage entry;
  final String path;

  @override
  ConsumerState<TextPreviewScreen> createState() => _TextPreviewScreenState();
}

class _TextDocument {
  _TextDocument(this.lines, {required this.isTruncated})
      : longestLineLength = lines.fold<int>(0, (longest, line) => max(longest, line.length));

  final List<String> lines;
  final bool isTruncated;
  final int longestLineLength;
}

class _TextPreviewScreenState extends ConsumerState<TextPreviewScreen> {
  static const _maxPreviewBytes = 2 * 1024 * 1024;
  static const _characterWidth = 7.9;
  static const _maxContentWidth = 40000.0;

  late final Future<_TextDocument> _documentFuture;
  bool _wrapLines = true;

  @override
  void initState() {
    super.initState();
    _documentFuture = _load(ref.read(readerRepositoryProvider));
  }

  Future<_TextDocument> _load(ReaderRepository repository) async {
    final isTruncated = widget.entry.size > _maxPreviewBytes;
    final bytes = await repository.readPrefix(widget.volume.handle, widget.entry.nodeId, min(widget.entry.size, _maxPreviewBytes));
    final text = decodePreviewText(bytes, isTruncated: isTruncated);
    return _TextDocument(const LineSplitter().convert(text), isTruncated: isTruncated);
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: PreviewNavigationBar(
        volume: widget.volume,
        entry: widget.entry,
        path: widget.path,
        middle: Text(widget.entry.name, maxLines: 1, overflow: TextOverflow.ellipsis),
        extraActions: [
          CupertinoButton(
            padding: const EdgeInsets.symmetric(horizontal: 6),
            onPressed: () => setState(() => _wrapLines = !_wrapLines),
            child: Icon(_wrapLines ? CupertinoIcons.text_alignleft : CupertinoIcons.arrow_right_arrow_left),
          ),
        ],
      ),
      child: SafeArea(
        child: FutureBuilder<_TextDocument>(
          future: _documentFuture,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return EmptyState(
                icon: CupertinoIcons.exclamationmark_triangle,
                title: 'File tidak bisa dibuka',
                subtitle: describeReaderError(snapshot.error!),
              );
            }
            final document = snapshot.data;
            if (document == null) return const Center(child: CupertinoActivityIndicator());
            return _buildDocument(context, document);
          },
        ),
      ),
    );
  }

  Widget _buildDocument(BuildContext context, _TextDocument document) {
    final secondary = CupertinoColors.secondaryLabel.resolveFrom(context);
    final codeStyle = TextStyle(
      fontFamily: 'monospace',
      fontFamilyFallback: const ['Menlo', 'Roboto Mono'],
      fontSize: 13,
      height: 1.4,
      color: CupertinoColors.label.resolveFrom(context),
    );
    final gutterWidth = document.lines.length.toString().length * 9.0 + 12;
    final lineList = ListView.builder(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
      itemCount: document.lines.length,
      itemBuilder: (context, index) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: gutterWidth,
            child: Text('${index + 1}', textAlign: TextAlign.right, style: codeStyle.copyWith(color: secondary)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              document.lines[index],
              style: codeStyle,
              softWrap: _wrapLines,
              overflow: _wrapLines ? TextOverflow.visible : TextOverflow.clip,
            ),
          ),
        ],
      ),
    );
    final body = _wrapLines
        ? lineList
        : SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: min(gutterWidth + 40 + document.longestLineLength * _characterWidth, _maxContentWidth),
              child: lineList,
            ),
          );
    return Column(
      children: [
        if (document.isTruncated)
          Container(
            width: double.infinity,
            color: CupertinoColors.systemYellow.withValues(alpha: 0.25),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: const Text('Hanya 2 MB pertama ditampilkan', style: TextStyle(fontSize: 13)),
          ),
        Expanded(child: body),
      ],
    );
  }
}
