import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../../shared/file_type_resolver.dart';
import '../../shared/hidden_files.dart';
import '../../shared/widgets/empty_state.dart';
import '../copy/copy_flow.dart';
import '../info/file_info_screen.dart';
import '../player/video_player_screen.dart';
import '../preview/image_preview_screen.dart';
import '../preview/pdf_preview_screen.dart';
import '../preview/text_preview_screen.dart';
import 'browser_location.dart';
import 'browser_preferences.dart';
import 'entry_grid_tile.dart';
import 'entry_list_tile.dart';
import 'entry_sorting.dart';
import 'path_bar.dart';
import 'selection_toolbar.dart';

Route<void> browserRoute(BrowserLocation location) => CupertinoPageRoute<void>(
      settings: RouteSettings(name: browserRouteName(location.nodeId)),
      title: location.title,
      builder: (_) => BrowserScreen(location: location),
    );

class BrowserScreen extends ConsumerStatefulWidget {
  const BrowserScreen({super.key, required this.location});

  final BrowserLocation location;

  @override
  ConsumerState<BrowserScreen> createState() => _BrowserScreenState();
}

class _BrowserScreenState extends ConsumerState<BrowserScreen> {
  late final Future<List<FileEntryMessage>> _entriesFuture;
  final Set<int> _selectedIds = {};
  bool _isSelecting = false;

  BrowserLocation get _location => widget.location;

  @override
  void initState() {
    super.initState();
    _entriesFuture = ref.read(readerRepositoryProvider).listDirectory(_location.volume.handle, _location.nodeId);
  }

  @override
  Widget build(BuildContext context) {
    final preferences = ref.watch(browserPreferencesProvider);
    return CupertinoPageScaffold(
      child: FutureBuilder<List<FileEntryMessage>>(
        future: _entriesFuture,
        builder: (context, snapshot) {
          final entries = snapshot.data;
          final visibleEntries = entries == null
              ? const <FileEntryMessage>[]
              : sortEntries(
                  entries.where((entry) => preferences.showHidden || !isHiddenName(entry.name)),
                  preferences.sortField,
                  preferences.ascending,
                );
          return Stack(
            children: [
              CustomScrollView(
                slivers: [
                  _buildNavigationBar(visibleEntries),
                  SliverToBoxAdapter(child: PathBar(trail: _location.trail, onSegmentTap: _jumpTo)),
                  SliverToBoxAdapter(
                    child: _LayoutSwitcher(
                      layout: preferences.layout,
                      onChanged: ref.read(browserPreferencesProvider.notifier).setLayout,
                    ),
                  ),
                  _buildContent(snapshot, visibleEntries, preferences.layout),
                  SliverPadding(padding: EdgeInsets.only(bottom: _isSelecting ? 96 : 24)),
                ],
              ),
              if (_isSelecting)
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: MediaQuery.paddingOf(context).bottom,
                  child: SelectionToolbar(
                    selectedCount: _selectedIds.length,
                    onCopy: () => _copy(_selectedEntries(visibleEntries)),
                    onInfo: () => _showInfo(_selectedEntries(visibleEntries).first),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  List<FileEntryMessage> _selectedEntries(List<FileEntryMessage> visibleEntries) =>
      visibleEntries.where((entry) => _selectedIds.contains(entry.nodeId)).toList();

  Widget _buildNavigationBar(List<FileEntryMessage> visibleEntries) {
    if (_isSelecting) {
      final allSelected = visibleEntries.isNotEmpty && _selectedIds.length == visibleEntries.length;
      return CupertinoSliverNavigationBar(
        largeTitle: Text(_location.title),
        leading: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: () => setState(() {
            if (allSelected) {
              _selectedIds.clear();
            } else {
              _selectedIds.addAll(visibleEntries.map((entry) => entry.nodeId));
            }
          }),
          child: Text(allSelected ? 'Batal Pilih' : 'Pilih Semua'),
        ),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: _endSelection,
          child: const Text('Selesai', style: TextStyle(fontWeight: FontWeight.w600)),
        ),
      );
    }
    return CupertinoSliverNavigationBar(
      largeTitle: Text(_location.title),
      previousPageTitle: _location.previousTitle,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          CupertinoButton(
            padding: EdgeInsets.zero,
            onPressed: () => setState(() => _isSelecting = true),
            child: const Text('Pilih'),
          ),
          const SizedBox(width: 12),
          CupertinoButton(
            padding: EdgeInsets.zero,
            onPressed: _showOptions,
            child: const Icon(CupertinoIcons.ellipsis_circle),
          ),
        ],
      ),
    );
  }

  Widget _buildContent(
    AsyncSnapshot<List<FileEntryMessage>> snapshot,
    List<FileEntryMessage> visibleEntries,
    BrowserLayout layout,
  ) {
    if (snapshot.hasError) {
      return SliverFillRemaining(
        hasScrollBody: false,
        child: EmptyState(
          icon: CupertinoIcons.exclamationmark_triangle,
          title: 'Folder tidak bisa dibuka',
          subtitle: describeReaderError(snapshot.error!),
        ),
      );
    }
    if (!snapshot.hasData) {
      return const SliverFillRemaining(hasScrollBody: false, child: Center(child: CupertinoActivityIndicator()));
    }
    if (visibleEntries.isEmpty) {
      return const SliverFillRemaining(
        hasScrollBody: false,
        child: EmptyState(icon: CupertinoIcons.folder, title: 'Folder ini kosong'),
      );
    }
    return layout == BrowserLayout.grid ? _buildGrid(visibleEntries) : _buildList(visibleEntries);
  }

  Widget _buildGrid(List<FileEntryMessage> entries) {
    final isPortrait = MediaQuery.orientationOf(context) == Orientation.portrait;
    return SliverPadding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      sliver: SliverGrid.builder(
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: isPortrait ? 3 : 5,
          childAspectRatio: 0.62,
          mainAxisSpacing: 8,
          crossAxisSpacing: 10,
        ),
        itemCount: entries.length,
        itemBuilder: (context, index) {
          final entry = entries[index];
          return KeyedSubtree(
            key: ValueKey(entry.nodeId),
            child: _withContextMenu(
              entry,
              EntryGridTile(
                volumeHandle: _location.volume.handle,
                entry: entry,
                isSelecting: _isSelecting,
                isSelected: _selectedIds.contains(entry.nodeId),
                onTap: () => _onEntryTap(entry, entries),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildList(List<FileEntryMessage> entries) {
    final background = CupertinoColors.secondarySystemGroupedBackground.resolveFrom(context);
    final separator = CupertinoColors.separator.resolveFrom(context);
    return SliverList.builder(
      itemCount: entries.length,
      itemBuilder: (context, index) {
        final entry = entries[index];
        return KeyedSubtree(
          key: ValueKey(entry.nodeId),
          child: ColoredBox(
            color: background,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _withContextMenu(
                  entry,
                  EntryListTile(
                    volumeHandle: _location.volume.handle,
                    entry: entry,
                    isSelecting: _isSelecting,
                    isSelected: _selectedIds.contains(entry.nodeId),
                    onTap: () => _onEntryTap(entry, entries),
                  ),
                ),
                if (index < entries.length - 1)
                  Padding(
                    padding: const EdgeInsets.only(left: 72),
                    child: SizedBox(height: 0.5, width: double.infinity, child: ColoredBox(color: separator)),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _withContextMenu(FileEntryMessage entry, Widget child) {
    if (_isSelecting) return child;
    return CupertinoContextMenu(
      actions: [
        CupertinoContextMenuAction(
          trailingIcon: CupertinoIcons.square_arrow_down,
          onPressed: () {
            Navigator.of(context, rootNavigator: true).pop();
            _copy([entry]);
          },
          child: const Text('Salin ke HP'),
        ),
        CupertinoContextMenuAction(
          trailingIcon: CupertinoIcons.info_circle,
          onPressed: () {
            Navigator.of(context, rootNavigator: true).pop();
            _showInfo(entry);
          },
          child: const Text('Info'),
        ),
        CupertinoContextMenuAction(
          trailingIcon: CupertinoIcons.checkmark_circle,
          onPressed: () {
            Navigator.of(context, rootNavigator: true).pop();
            setState(() {
              _isSelecting = true;
              _selectedIds.add(entry.nodeId);
            });
          },
          child: const Text('Pilih'),
        ),
      ],
      child: child,
    );
  }

  void _onEntryTap(FileEntryMessage entry, List<FileEntryMessage> entries) {
    if (_isSelecting) {
      setState(() {
        if (!_selectedIds.remove(entry.nodeId)) _selectedIds.add(entry.nodeId);
      });
      return;
    }
    final category = categorizeEntry(entry);
    if (category == FileCategory.folder) {
      Navigator.of(context).push(browserRoute(_location.child(entry)));
      return;
    }
    if (!entry.isReadable || !isPreviewable(category)) {
      _showInfo(entry);
      return;
    }
    final rootNavigator = Navigator.of(context, rootNavigator: true);
    final volume = _location.volume;
    final path = _location.pathOf(entry);
    final Widget screen = switch (category) {
      FileCategory.image => _imagePreview(entry, entries),
      FileCategory.pdf => PdfPreviewScreen(volume: volume, entry: entry, path: path),
      FileCategory.video || FileCategory.audio => VideoPlayerScreen(
          volume: volume,
          entry: entry,
          path: path,
          isAudio: category == FileCategory.audio,
        ),
      _ => TextPreviewScreen(volume: volume, entry: entry, path: path),
    };
    rootNavigator.push(CupertinoPageRoute<void>(fullscreenDialog: true, builder: (_) => screen));
  }

  Widget _imagePreview(FileEntryMessage entry, List<FileEntryMessage> entries) {
    final images = entries
        .where((candidate) => candidate.isReadable && categorizeEntry(candidate) == FileCategory.image)
        .toList();
    return ImagePreviewScreen(
      volume: _location.volume,
      images: images,
      initialIndex: images.indexWhere((image) => image.nodeId == entry.nodeId),
      pathOf: _location.pathOf,
    );
  }

  void _showInfo(FileEntryMessage entry) {
    Navigator.of(context, rootNavigator: true)
        .push(fileInfoRoute(volume: _location.volume, entry: entry, path: _location.pathOf(entry)));
  }

  Future<void> _copy(List<FileEntryMessage> entries) async {
    await startCopyFlow(context, ref, volumeHandle: _location.volume.handle, entries: entries);
    if (mounted && _isSelecting) _endSelection();
  }

  void _endSelection() => setState(() {
        _isSelecting = false;
        _selectedIds.clear();
      });

  void _jumpTo(PathSegment segment) {
    if (segment.nodeId == _location.nodeId) return;
    Navigator.of(context).popUntil((route) => route.settings.name == browserRouteName(segment.nodeId) || route.isFirst);
  }

  void _showOptions() {
    final preferences = ref.read(browserPreferencesProvider);
    final notifier = ref.read(browserPreferencesProvider.notifier);
    showCupertinoModalPopup<void>(
      context: context,
      builder: (sheetContext) => CupertinoActionSheet(
        title: const Text('Urutkan'),
        actions: [
          for (final field in SortField.values)
            CupertinoActionSheetAction(
              onPressed: () {
                notifier.selectSortField(field);
                Navigator.pop(sheetContext);
              },
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (preferences.sortField == field) ...[
                    const Icon(CupertinoIcons.checkmark, size: 18),
                    const SizedBox(width: 6),
                  ],
                  Text(sortFieldLabel(field)),
                  if (preferences.sortField == field) ...[
                    const SizedBox(width: 6),
                    Icon(preferences.ascending ? CupertinoIcons.chevron_up : CupertinoIcons.chevron_down, size: 16),
                  ],
                ],
              ),
            ),
          CupertinoActionSheetAction(
            onPressed: () {
              notifier.toggleHidden();
              Navigator.pop(sheetContext);
            },
            child: Text(preferences.showHidden ? 'Sembunyikan File Tersembunyi' : 'Tampilkan File Tersembunyi'),
          ),
        ],
        cancelButton: CupertinoActionSheetAction(
          isDefaultAction: true,
          onPressed: () => Navigator.pop(sheetContext),
          child: const Text('Batal'),
        ),
      ),
    );
  }
}

class _LayoutSwitcher extends StatelessWidget {
  const _LayoutSwitcher({required this.layout, required this.onChanged});

  final BrowserLayout layout;
  final ValueChanged<BrowserLayout> onChanged;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      child: CupertinoSlidingSegmentedControl<BrowserLayout>(
        groupValue: layout,
        onValueChanged: (value) {
          if (value != null) onChanged(value);
        },
        children: const {
          BrowserLayout.grid: Padding(
            padding: EdgeInsets.symmetric(horizontal: 24, vertical: 4),
            child: Icon(CupertinoIcons.square_grid_2x2, size: 18),
          ),
          BrowserLayout.list: Padding(
            padding: EdgeInsets.symmetric(horizontal: 24, vertical: 4),
            child: Icon(CupertinoIcons.list_bullet, size: 18),
          ),
        },
      ),
    );
  }
}
