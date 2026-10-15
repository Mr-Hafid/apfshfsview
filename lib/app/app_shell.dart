import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/reader_repository.dart';
import '../features/copy/copy_jobs.dart';
import '../features/copy/copy_screen.dart';
import '../features/devices/devices_screen.dart';
import '../shared/widgets/toast_banner.dart';

class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key});

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  final _browseNavigatorKey = GlobalKey<NavigatorState>();
  late final StreamSubscription<int> _detachSubscription;

  @override
  void initState() {
    super.initState();
    _detachSubscription = ref.read(readerRepositoryProvider).deviceDetached.listen(_onDeviceDetached);
  }

  @override
  void dispose() {
    _detachSubscription.cancel();
    super.dispose();
  }

  void _onDeviceDetached(int deviceId) {
    final navigator = _browseNavigatorKey.currentState;
    if (navigator == null || !navigator.canPop()) return;
    navigator.popUntil((route) => route.isFirst);
    Navigator.of(context, rootNavigator: true).popUntil((route) => route.isFirst);
    ToastBanner.show(context, 'SSD terlepas');
  }

  @override
  Widget build(BuildContext context) {
    final activeCopies = ref.watch(activeCopyCountProvider);
    return CupertinoTabScaffold(
      tabBar: CupertinoTabBar(
        items: [
          const BottomNavigationBarItem(icon: Icon(CupertinoIcons.folder), label: 'Telusuri'),
          BottomNavigationBarItem(
            icon: _BadgedIcon(icon: CupertinoIcons.arrow_down_doc, count: activeCopies),
            label: 'Penyalinan',
          ),
        ],
      ),
      tabBuilder: (context, index) => index == 0
          ? CupertinoTabView(navigatorKey: _browseNavigatorKey, builder: (_) => const DevicesScreen())
          : CupertinoTabView(builder: (_) => const CopyScreen()),
    );
  }
}

class _BadgedIcon extends StatelessWidget {
  const _BadgedIcon({required this.icon, required this.count});

  final IconData icon;
  final int count;

  @override
  Widget build(BuildContext context) {
    if (count == 0) return Icon(icon);
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Icon(icon),
        Positioned(
          right: -8,
          top: -4,
          child: DecoratedBox(
            decoration: BoxDecoration(color: CupertinoColors.systemRed.resolveFrom(context), shape: BoxShape.circle),
            child: SizedBox.square(
              dimension: 18,
              child: Center(
                child: Text(
                  '$count',
                  style: const TextStyle(color: CupertinoColors.white, fontSize: 11, fontWeight: FontWeight.w600),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
