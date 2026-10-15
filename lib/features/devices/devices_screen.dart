import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../../shared/widgets/empty_state.dart';
import '../../shared/widgets/toast_banner.dart';
import '../volumes/volumes_screen.dart';

const devicesTitle = 'Lokasi';

class DevicesScreen extends ConsumerStatefulWidget {
  const DevicesScreen({super.key});

  @override
  ConsumerState<DevicesScreen> createState() => _DevicesScreenState();
}

class _DevicesScreenState extends ConsumerState<DevicesScreen> {
  late final ReaderRepository _repository;
  final List<StreamSubscription<Object>> _subscriptions = [];
  List<UsbDeviceMessage>? _devices;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _repository = ref.read(readerRepositoryProvider);
    _subscriptions
      ..add(_repository.deviceAttached.listen((_) => _reload()))
      ..add(_repository.deviceDetached.listen((_) => _reload()));
    _reload();
  }

  @override
  void dispose() {
    for (final subscription in _subscriptions) {
      subscription.cancel();
    }
    super.dispose();
  }

  Future<void> _reload() async {
    try {
      final devices = await _repository.listDevices();
      if (!mounted) return;
      setState(() {
        _devices = devices;
        _error = null;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error);
    }
  }

  Future<void> _grantAccess(UsbDeviceMessage device) async {
    final granted = await _repository.requestPermission(device.deviceId);
    if (!granted && mounted) ToastBanner.show(context, 'Izin akses USB ditolak');
    await _reload();
  }

  void _open(UsbDeviceMessage device) {
    Navigator.of(context).push(
      CupertinoPageRoute<void>(title: device.productName, builder: (_) => VolumesScreen(device: device)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.systemGroupedBackground,
      child: CustomScrollView(
        slivers: [
          const CupertinoSliverNavigationBar(largeTitle: Text(devicesTitle)),
          _buildContent(context),
        ],
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    final error = _error;
    if (error != null) {
      return SliverFillRemaining(
        hasScrollBody: false,
        child: EmptyState(
          icon: CupertinoIcons.exclamationmark_triangle,
          title: 'Perangkat tidak bisa dibaca',
          subtitle: describeReaderError(error),
        ),
      );
    }
    final devices = _devices;
    if (devices == null) {
      return const SliverFillRemaining(hasScrollBody: false, child: Center(child: CupertinoActivityIndicator()));
    }
    if (devices.isEmpty) {
      return const SliverFillRemaining(
        hasScrollBody: false,
        child: EmptyState(
          icon: CupertinoIcons.device_desktop,
          title: 'Colokkan SSD lewat OTG',
          subtitle: 'Data hanya dibaca. Aplikasi ini tidak punya akses internet.',
        ),
      );
    }
    return SliverList.list(
      children: [
        CupertinoListSection.insetGrouped(
          header: const Text('Perangkat'),
          children: [for (final device in devices) _buildDeviceTile(device)],
        ),
        CupertinoListSection.insetGrouped(
          header: const Text('Privasi'),
          children: [
            CupertinoListTile(
              leading: Icon(CupertinoIcons.lock_shield, color: CupertinoColors.systemGreen.resolveFrom(context)),
              title: const Text('Offline · Read-only'),
              subtitle: const Text('Tidak ada data yang dikirim atau ditulis ke SSD'),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildDeviceTile(UsbDeviceMessage device) {
    return CupertinoListTile(
      leading: Icon(CupertinoIcons.tray_2_fill, color: CupertinoTheme.of(context).primaryColor),
      title: Text(device.productName),
      subtitle: Text(device.manufacturerName),
      trailing: device.hasPermission
          ? const CupertinoListTileChevron()
          : CupertinoButton(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              minimumSize: const Size(28, 28),
              onPressed: () => _grantAccess(device),
              child: const Text('Izinkan'),
            ),
      onTap: device.hasPermission ? () => _open(device) : () => _grantAccess(device),
    );
  }
}
