import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../../shared/file_type_resolver.dart';
import '../../shared/hidden_files.dart';
import '../../shared/widgets/empty_state.dart';
import '../../shared/widgets/toast_banner.dart';
import '../copy/copy_flow.dart';
import '../info/file_info_screen.dart';
import '../player/video_player_screen.dart';
import '../preview/image_preview_screen.dart';
import '../preview/pdf_preview_screen.dart';
import '../preview/text_preview_screen.dart';
import 'browser_location.dart';
import 'browser_preferences.dart';
import 'entry_actions_sheet.dart';
import 'entry_sorting.dart';
import 'finder_bottom_bar.dart';
import 'finder_icon_tile.dart';
import 'finder_list_row.dart';
import 'finder_palette.dart';
import 'finder_sidebar.dart';
import 'finder_toolbar.dart';

Route<void> browserRoute({required VolumeContext volume, required int rootNodeId, BrowserSidebarContext? sidebar}) =>
    CupertinoPageRoute<void>(
      title: volume.name,
      builder: (_) => BrowserScreen(volume: volume, rootNodeId: rootNodeId, sidebar: sidebar),
    );

class BrowserScreen extends ConsumerStatefulWidget {
  const BrowserScreen({super.key, required this.volume, required this.rootNodeId, this.sidebar});

  final VolumeContext volume;
  final int rootNodeId;
  final BrowserSidebarContext? sidebar;

  @override
  ConsumerState<BrowserScreen> createState() => _BrowserScreenState();
}

class _ListRowModel {
  const _ListRowModel({required this.entry, required this.depth, required this.trail, required this.siblings});

  final FileEntryMessage entry;
  final int depth;
  final List<PathSegment> trail;
  final List<FileEntryMessage> siblings;
}

class _BrowserScreenState extends ConsumerState<BrowserScreen> {
  static const _sidebarMinWidth = 900.0;
  static const _labeledToolbarMinWidth = 700.0;
  static const _columnsMinWidth = 640.0;
  static const _iconTileMaxWidth = 112.0;

  late final ReaderRepository _repository;
  late List<PathSegment> _trail;
  final List<List<PathSegment>> _forwardHistory = [];
  final Map<int, List<FileEntryMessage>> _listings = {};
  final Map<int, Object> _listingErrors = {};
  final Set<int> _loadingFolderIds = {};
  final Set<int> _expandedFolderIds = {};
  final Map<int, _ListRowModel> _selected = {};
  bool _isSelecting = false;
  int _transitionDirection = 0;
  Key? _contentKey;

  PathSegment get _current => _trail.last;

  @override
  void initState() {
    super.initState();
    _repository = ref.read(readerRepositoryProvider);
    _trail = [PathSegment(widget.rootNodeId, widget.volume.name)];
    _ensureLoaded(widget.rootNodeId);
  }

  void _ensureLoaded(int folderId) {
    if (_listings.containsKey(folderId) || _loadingFolderIds.contains(folderId)) return;
    _loadingFolderIds.add(folderId);
    _listingErrors.remove(folderId);
    _repository
        .listDirectory(widget.volume.handle, folderId)
        .then(
          (entries) {
            if (!mounted) return;
            setState(() {
              _loadingFolderIds.remove(folderId);
              _listings[folderId] = entries;
            });
          },
          onError: (Object error) {
            if (!mounted) return;
            setState(() {
              _loadingFolderIds.remove(folderId);
              _listingErrors[folderId] = error;
            });
          },
        );
  }

  List<FileEntryMessage> _visibleEntries(int folderId, BrowserPreferences preferences) {
    final entries = _listings[folderId];
    if (entries == null) return const [];
    return sortEntries(
      entries.where((entry) => preferences.showHidden || !isHiddenName(entry.name)),
      preferences.sortField,
      preferences.ascending,
    );
  }

  @override
  Widget build(BuildContext context) {
    final preferences = ref.watch(browserPreferencesProvider);
    final width = MediaQuery.sizeOf(context).width;
    final sidebar = widget.sidebar;
    final showsSidebar = sidebar != null && width >= _sidebarMinWidth;
    final visibleEntries = _visibleEntries(_current.nodeId, preferences);
    final isLoaded = _listings.containsKey(_current.nodeId);
    return PopScope(
      canPop: !_isSelecting && _trail.length == 1,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (_isSelecting) {
          _endSelection();
        } else {
          _goBack();
        }
      },
      child: CupertinoPageScaffold(
        backgroundColor: FinderPalette.window(context),
        child: Row(
          children: [
            if (showsSidebar)
              FinderSidebar(
                sidebar: sidebar,
                currentVolumeHandle: widget.volume.handle,
                folders: _visibleEntries(
                  widget.rootNodeId,
                  preferences,
                ).where((entry) => entry.kind == NodeKindMessage.directory).toList(),
                activeFolderId: _trail.length > 1 ? _trail[1].nodeId : null,
                onSelectFolder: _openSidebarFolder,
              ),
            Expanded(
              child: Column(
                children: [
                  FinderToolbar(
                    title: _current.name,
                    canGoForward: _forwardHistory.isNotEmpty,
                    onBack: _goBack,
                    onForward: _goForward,
                    layout: preferences.layout,
                    onLayoutChanged: (layout) {
                      setState(() => _transitionDirection = 0);
                      ref.read(browserPreferencesProvider.notifier).setLayout(layout);
                    },
                    onSelect: _startSelection,
                    onActions: _showActions,
                    showsLabels: width >= _labeledToolbarMinWidth,
                    isSelecting: _isSelecting,
                    selectedCount: _selected.length,
                    allSelected:
                        visibleEntries.isNotEmpty &&
                        visibleEntries.every((entry) => _selected.containsKey(entry.nodeId)),
                    onToggleSelectAll: () => _toggleSelectAll(visibleEntries),
                    onDoneSelecting: _endSelection,
                  ),
                  Expanded(
                    child: ClipRect(
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 280),
                        switchInCurve: Curves.easeOutCubic,
                        switchOutCurve: Curves.easeInCubic,
                        transitionBuilder: (child, animation) => _buildTransition(child, animation),
                        layoutBuilder: (current, previous) =>
                            Stack(fit: StackFit.expand, children: [...previous, ?current]),
                        child: KeyedSubtree(
                          key: _contentKey = ValueKey('${_current.nodeId}-${preferences.layout.name}'),
                          child: ColoredBox(
                            color: FinderPalette.window(context),
                            child: _buildContent(context, preferences, visibleEntries, width),
                          ),
                        ),
                      ),
                    ),
                  ),
                  FinderBottomBar(
                    trail: _trail,
                    itemCount: isLoaded ? visibleEntries.length : null,
                    onSegmentTap: _jumpToTrailIndex,
                    isSelecting: _isSelecting,
                    selectedCount: _selected.length,
                    onCopy: () => _copy(_selected.values.map((row) => row.entry).toList()),
                    onInfo: () => _showInfo(_selected.values.first),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContent(
    BuildContext context,
    BrowserPreferences preferences,
    List<FileEntryMessage> visibleEntries,
    double width,
  ) {
    final error = _listingErrors[_current.nodeId];
    if (error != null) {
      return EmptyState(
        icon: CupertinoIcons.exclamationmark_triangle,
        title: 'Folder tidak bisa dibuka',
        subtitle: describeReaderError(error),
      );
    }
    if (!_listings.containsKey(_current.nodeId)) return const Center(child: CupertinoActivityIndicator());
    if (visibleEntries.isEmpty) return const EmptyState(icon: CupertinoIcons.folder, title: 'Folder ini kosong');
    return preferences.layout == BrowserLayout.grid
        ? _buildIconGrid(visibleEntries)
        : _buildList(preferences, visibleEntries, width >= _columnsMinWidth);
  }

  Widget _buildIconGrid(List<FileEntryMessage> entries) {
    return GridView.builder(
      key: PageStorageKey('grid-${_current.nodeId}'),
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: _iconTileMaxWidth,
        childAspectRatio: 0.74,
        mainAxisSpacing: 6,
        crossAxisSpacing: 6,
      ),
      itemCount: entries.length,
      itemBuilder: (context, index) {
        final row = _ListRowModel(entry: entries[index], depth: 0, trail: _trail, siblings: entries);
        return FinderIconTile(
          key: ValueKey(row.entry.nodeId),
          volumeHandle: widget.volume.handle,
          entry: row.entry,
          isSelected: _selected.containsKey(row.entry.nodeId),
          onTap: () => _onTap(row),
          onLongPress: () => _onLongPress(row),
        );
      },
    );
  }

  Widget _buildList(BrowserPreferences preferences, List<FileEntryMessage> entries, bool showsColumns) {
    final rows = <Object>[];
    void addRows(List<FileEntryMessage> siblings, int depth, List<PathSegment> trail) {
      for (final entry in siblings) {
        rows.add(_ListRowModel(entry: entry, depth: depth, trail: trail, siblings: siblings));
        if (entry.kind != NodeKindMessage.directory || !_expandedFolderIds.contains(entry.nodeId)) continue;
        if (!_listings.containsKey(entry.nodeId)) {
          rows.add(depth + 1);
          continue;
        }
        addRows(_visibleEntries(entry.nodeId, preferences), depth + 1, [
          ...trail,
          PathSegment(entry.nodeId, entry.name),
        ]);
      }
    }

    addRows(entries, 0, _trail);
    final notifier = ref.read(browserPreferencesProvider.notifier);
    return Column(
      children: [
        if (showsColumns)
          FinderListHeader(
            sortField: preferences.sortField,
            ascending: preferences.ascending,
            onSort: notifier.selectSortField,
          ),
        Expanded(
          child: ListView.builder(
            key: PageStorageKey('list-${_current.nodeId}'),
            padding: const EdgeInsets.only(bottom: 24),
            itemCount: rows.length,
            itemBuilder: (context, index) {
              final row = rows[index];
              if (row is int) return FinderListLoadingRow(depth: row);
              final model = row as _ListRowModel;
              return FinderListRow(
                key: ValueKey('row-${model.entry.nodeId}'),
                volumeHandle: widget.volume.handle,
                entry: model.entry,
                depth: model.depth,
                rowIndex: index,
                isExpanded: _expandedFolderIds.contains(model.entry.nodeId),
                isSelected: _selected.containsKey(model.entry.nodeId),
                showsColumns: showsColumns,
                onTap: () => _onTap(model),
                onLongPress: () => _onLongPress(model),
                onToggleExpanded: () => _toggleExpanded(model.entry),
              );
            },
          ),
        ),
      ],
    );
  }

  void _toggleExpanded(FileEntryMessage folder) {
    setState(() {
      if (!_expandedFolderIds.remove(folder.nodeId)) {
        _expandedFolderIds.add(folder.nodeId);
        _ensureLoaded(folder.nodeId);
      }
    });
  }

  Widget _buildTransition(Widget child, Animation<double> animation) {
    final direction = _transitionDirection.toDouble();
    if (direction == 0) return FadeTransition(opacity: animation, child: child);
    final isIncoming = child.key == _contentKey;
    final offset = Tween<Offset>(
      begin: Offset((isIncoming ? direction : -direction) * 0.3, 0),
      end: Offset.zero,
    ).animate(animation);
    return FadeTransition(
      opacity: animation,
      child: SlideTransition(position: offset, child: child),
    );
  }

  void _navigateTo(List<PathSegment> trail, {required int direction, bool keepsForwardHistory = false}) {
    setState(() {
      _transitionDirection = direction;
      if (!keepsForwardHistory) _forwardHistory.clear();
      _trail = trail;
      _expandedFolderIds.clear();
      _isSelecting = false;
      _selected.clear();
    });
    _ensureLoaded(trail.last.nodeId);
  }

  void _openFolder(_ListRowModel row) =>
      _navigateTo([...row.trail, PathSegment(row.entry.nodeId, row.entry.name)], direction: 1);

  void _openSidebarFolder(FileEntryMessage folder) =>
      _navigateTo([_trail.first, PathSegment(folder.nodeId, folder.name)], direction: 1);

  void _goBack() {
    if (_trail.length == 1) {
      Navigator.of(context).maybePop();
      return;
    }
    final previous = _trail;
    _navigateTo(_trail.sublist(0, _trail.length - 1), direction: -1, keepsForwardHistory: true);
    setState(() => _forwardHistory.add(previous));
  }

  void _goForward() {
    if (_forwardHistory.isEmpty) return;
    final next = _forwardHistory.removeLast();
    _navigateTo(next, direction: 1, keepsForwardHistory: true);
  }

  void _jumpToTrailIndex(int index) {
    if (index >= _trail.length - 1) return;
    final previous = _trail;
    _navigateTo(_trail.sublist(0, index + 1), direction: -1, keepsForwardHistory: true);
    setState(
      () => _forwardHistory
        ..clear()
        ..add(previous),
    );
  }

  void _startSelection() => setState(() => _isSelecting = true);

  void _endSelection() => setState(() {
    _isSelecting = false;
    _selected.clear();
  });

  void _toggleSelected(_ListRowModel row) => setState(() {
    if (_selected.remove(row.entry.nodeId) == null) _selected[row.entry.nodeId] = row;
  });

  void _toggleSelectAll(List<FileEntryMessage> visibleEntries) {
    final allSelected = visibleEntries.every((entry) => _selected.containsKey(entry.nodeId));
    setState(() {
      if (allSelected) {
        _selected.clear();
      } else {
        for (final entry in visibleEntries) {
          _selected[entry.nodeId] = _ListRowModel(entry: entry, depth: 0, trail: _trail, siblings: visibleEntries);
        }
      }
    });
  }

  void _onTap(_ListRowModel row) {
    if (_isSelecting) {
      _toggleSelected(row);
      return;
    }
    _open(row);
  }

  void _open(_ListRowModel row) {
    final entry = row.entry;
    final category = categorizeEntry(entry);
    if (category == FileCategory.folder) {
      _openFolder(row);
      return;
    }
    if (!entry.isReadable || !isPreviewable(category)) {
      _showInfo(row);
      return;
    }
    final volume = widget.volume;
    final path = joinPath(row.trail, entry.name);
    final Widget screen = switch (category) {
      FileCategory.image => _imagePreview(row),
      FileCategory.pdf => PdfPreviewScreen(volume: volume, entry: entry, path: path),
      FileCategory.video || FileCategory.audio => VideoPlayerScreen(
        volume: volume,
        entry: entry,
        path: path,
        isAudio: category == FileCategory.audio,
      ),
      _ => TextPreviewScreen(volume: volume, entry: entry, path: path),
    };
    Navigator.of(
      context,
      rootNavigator: true,
    ).push(CupertinoPageRoute<void>(fullscreenDialog: true, builder: (_) => screen));
  }

  Widget _imagePreview(_ListRowModel row) {
    final images = row.siblings
        .where((candidate) => candidate.isReadable && categorizeEntry(candidate) == FileCategory.image)
        .toList();
    return ImagePreviewScreen(
      volume: widget.volume,
      images: images,
      initialIndex: images.indexWhere((image) => image.nodeId == row.entry.nodeId),
      pathOf: (image) => joinPath(row.trail, image.name),
    );
  }

  Future<void> _onLongPress(_ListRowModel row) async {
    if (_isSelecting) {
      _toggleSelected(row);
      return;
    }
    final action = await showEntryActions(context, row.entry);
    if (!mounted || action == null) return;
    switch (action) {
      case EntryAction.open:
        _open(row);
      case EntryAction.copyToPhone:
        await _copy([row.entry]);
      case EntryAction.copyName:
        await Clipboard.setData(ClipboardData(text: row.entry.name));
        if (mounted) ToastBanner.show(context, 'Nama disalin');
      case EntryAction.info:
        _showInfo(row);
      case EntryAction.select:
        setState(() {
          _isSelecting = true;
          _selected[row.entry.nodeId] = row;
        });
    }
  }

  void _showInfo(_ListRowModel row) {
    Navigator.of(
      context,
      rootNavigator: true,
    ).push(fileInfoRoute(volume: widget.volume, entry: row.entry, path: joinPath(row.trail, row.entry.name)));
  }

  Future<void> _copy(List<FileEntryMessage> entries) async {
    await startCopyFlow(context, ref, volumeHandle: widget.volume.handle, entries: entries);
    if (mounted && _isSelecting) _endSelection();
  }

  void _showActions() {
    final preferences = ref.read(browserPreferencesProvider);
    final notifier = ref.read(browserPreferencesProvider.notifier);
    showCupertinoModalPopup<void>(
      context: context,
      builder: (sheetContext) => CupertinoActionSheet(
        title: Text(_current.name),
        actions: [
          CupertinoActionSheetAction(
            onPressed: () {
              Navigator.pop(sheetContext);
              _startSelection();
            },
            child: const Text('Pilih'),
          ),
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
                  Text('Urutkan: ${sortFieldLabel(field)}'),
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
