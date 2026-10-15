import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../../shared/format_bytes.dart';
import '../../shared/readable_width.dart';
import '../../shared/widgets/empty_state.dart';
import '../../shared/widgets/thin_progress_bar.dart';
import '../../shared/widgets/toast_banner.dart';
import '../browser/browser_location.dart';
import '../browser/browser_screen.dart';
import '../browser/finder_sidebar.dart';
import '../copy/copy_jobs.dart';

const _volumesRouteName = 'volumes';

Route<void> volumesRoute(UsbDeviceMessage device) => CupertinoPageRoute<void>(
  settings: const RouteSettings(name: _volumesRouteName),
  title: device.productName,
  builder: (_) => VolumesScreen(device: device),
);

String volumeSubtitle(VolumeMessage volume) {
  if (volume.isEncrypted) return 'Terenkripsi — tidak didukung';
  final failureCode = volume.failureCode;
  if (volume.volumeHandle == null && failureCode != null) return describeErrorCode(failureCode);
  final role = volume.role == null ? '' : ' · ${volume.role}';
  return '${volume.format}$role · ${formatBytes(volume.usedBytes)} dari ${formatBytes(volume.totalBytes)}';
}

class VolumesScreen extends ConsumerStatefulWidget {
  const VolumesScreen({super.key, required this.device});

  final UsbDeviceMessage device;

  @override
  ConsumerState<VolumesScreen> createState() => _VolumesScreenState();
}

class _VolumesScreenState extends ConsumerState<VolumesScreen> {
  late final ReaderRepository _repository;
  late final Future<DeviceOpenResult> _openFuture;
  List<VolumeMessage> _volumes = const [];
  bool _isEjected = false;

  @override
  void initState() {
    super.initState();
    _repository = ref.read(readerRepositoryProvider);
    _openFuture = _repository.openDevice(widget.device.deviceId);
    _openFuture.then((result) => _volumes = result.volumes, onError: (_) {});
  }

  @override
  void dispose() {
    if (!_isEjected) _repository.closeDevice(widget.device.deviceId);
    super.dispose();
  }

  Future<void> _eject() async {
    final activeCopies = ref.read(activeCopyCountProvider);
    final confirmed = await showCupertinoDialog<bool>(
      context: context,
      builder: (dialogContext) => CupertinoAlertDialog(
        title: Text('Eject ${widget.device.productName}?'),
        content: Text(
          activeCopies > 0
              ? 'Penyalinan yang sedang berjalan dari SSD ini akan dihentikan dan file yang belum selesai dihapus.'
              : 'Semua akses ke SSD akan ditutup. Setelah itu SSD aman dicabut.',
        ),
        actions: [
          CupertinoDialogAction(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('Batal')),
          CupertinoDialogAction(
            isDestructiveAction: activeCopies > 0,
            isDefaultAction: activeCopies == 0,
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Eject'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    final navigator = Navigator.of(context);
    _isEjected = true;
    await _repository.closeDevice(widget.device.deviceId);
    navigator.popUntil((route) => route.isFirst);
    final overlay = navigator.overlay;
    if (overlay != null) ToastBanner.showOn(overlay, 'SSD sudah di-eject, aman dicabut');
  }

  Future<void> _openVolume(VolumeMessage volume) async {
    final handle = volume.volumeHandle;
    if (handle == null) return;
    if (volume.isUncleanlyUnmounted) {
      final proceed = await showCupertinoDialog<bool>(
        context: context,
        builder: (dialogContext) => CupertinoAlertDialog(
          title: const Text('Volume tidak di-eject dengan benar'),
          content: const Text('Isi volume mungkin tidak konsisten. Tetap buka?'),
          actions: [
            CupertinoDialogAction(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('Batal')),
            CupertinoDialogAction(
              isDefaultAction: true,
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Buka'),
            ),
          ],
        ),
      );
      if (proceed != true) return;
    }
    try {
      final rootId = await _repository.rootNodeId(handle);
      if (!mounted) return;
      final navigator = Navigator.of(context);
      navigator.popUntil((route) => route.settings.name == _volumesRouteName || route.isFirst);
      navigator.push(
        browserRoute(
          volume: VolumeContext(handle: handle, name: volume.name, format: volume.format),
          rootNodeId: rootId,
          sidebar: BrowserSidebarContext(
            deviceName: widget.device.productName,
            volumes: _volumes,
            onSelectVolume: _openVolume,
            onEject: _eject,
          ),
        ),
      );
    } catch (error) {
      if (mounted) ToastBanner.show(context, describeReaderError(error));
    }
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.systemGroupedBackground,
      child: CustomScrollView(
        slivers: [
          CupertinoSliverNavigationBar(
            largeTitle: Text(widget.device.productName),
            previousPageTitle: 'Lokasi',
            trailing: CupertinoButton(
              padding: EdgeInsets.zero,
              onPressed: _eject,
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [Icon(CupertinoIcons.eject_fill, size: 20), SizedBox(width: 4), Text('Eject')],
              ),
            ),
          ),
          FutureBuilder<DeviceOpenResult>(
            future: _openFuture,
            builder: (context, snapshot) {
              if (snapshot.hasError) {
                return SliverFillRemaining(
                  hasScrollBody: false,
                  child: EmptyState(
                    icon: CupertinoIcons.exclamationmark_triangle,
                    title: 'SSD tidak bisa dibuka',
                    subtitle: describeReaderError(snapshot.error!),
                  ),
                );
              }
              final result = snapshot.data;
              if (result == null) {
                return const SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(child: CupertinoActivityIndicator()),
                );
              }
              if (result.volumes.isEmpty) {
                return SliverFillRemaining(
                  hasScrollBody: false,
                  child: EmptyState(
                    icon: CupertinoIcons.question_circle,
                    title: 'Format tidak didukung',
                    subtitle: result.unrecognizedPartitionTypes.isEmpty
                        ? 'Tidak ada partisi APFS atau HFS+.'
                        : 'Partisi yang ditemukan:\n${result.unrecognizedPartitionTypes.join('\n')}',
                  ),
                );
              }
              return SliverPadding(
                padding: readableInsets(context),
                sliver: SliverToBoxAdapter(
                  child: CupertinoListSection.insetGrouped(
                    header: Text('Volume · ${formatBytes(result.capacityBytes)}'),
                    children: [for (final volume in result.volumes) _VolumeTile(volume: volume, onTap: _openVolume)],
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _VolumeTile extends StatelessWidget {
  const _VolumeTile({required this.volume, required this.onTap});

  final VolumeMessage volume;
  final ValueChanged<VolumeMessage> onTap;

  @override
  Widget build(BuildContext context) {
    final isOpenable = volume.volumeHandle != null;
    final fraction = volume.totalBytes > 0 ? volume.usedBytes / volume.totalBytes : 0.0;
    final Widget? trailing = volume.isUncleanlyUnmounted
        ? Icon(CupertinoIcons.exclamationmark_triangle_fill, color: CupertinoColors.systemOrange.resolveFrom(context))
        : isOpenable
        ? const CupertinoListTileChevron()
        : null;
    return Opacity(
      opacity: isOpenable ? 1 : 0.45,
      child: CupertinoListTile(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
        leading: Icon(
          volume.isEncrypted ? CupertinoIcons.lock_fill : CupertinoIcons.folder_fill,
          color: isOpenable ? CupertinoTheme.of(context).primaryColor : CupertinoColors.systemGrey.resolveFrom(context),
        ),
        title: Text(volume.name),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(volumeSubtitle(volume)),
            if (isOpenable) ...[const SizedBox(height: 6), ThinProgressBar(fraction: fraction)],
          ],
        ),
        trailing: trailing,
        onTap: isOpenable ? () => onTap(volume) : null,
      ),
    );
  }
}
