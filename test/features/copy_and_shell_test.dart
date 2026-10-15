import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:reader/app/app_shell.dart';
import 'package:reader/bridge/reader_api.g.dart';
import 'package:reader/bridge/reader_repository.dart';
import 'package:reader/features/copy/copy_screen.dart';

import '../support/fake_reader_repository.dart';

void main() {
  testWidgets('copy screen shows running progress and finished jobs', (tester) async {
    final repository = FakeReaderRepository();
    await tester.pumpWidget(ProviderScope(
      overrides: [readerRepositoryProvider.overrideWithValue(repository)],
      child: const CupertinoApp(home: CopyScreen()),
    ));
    await tester.pump();
    repository.copyProgressController
      ..add(fakeProgress(jobId: 1, state: 'running', copiedFiles: 3, totalFiles: 10, copiedBytes: 1000, totalBytes: 4000))
      ..add(fakeProgress(jobId: 2, state: 'completed', copiedFiles: 5, copiedBytes: 2000000));
    await tester.pumpAndSettle();
    expect(find.textContaining('3 dari 10 file'), findsOneWidget);
    expect(find.text('5 file · 2,0 MB'), findsOneWidget);
  });

  testWidgets('unplugging the SSD returns to the location list with a toast', (tester) async {
    final repository = FakeReaderRepository(
      devices: [UsbDeviceMessage(deviceId: 7, productName: 'T7 Shield', manufacturerName: 'Samsung', hasPermission: true)],
      openResult: DeviceOpenResult(capacityBytes: 1, volumes: const [], unrecognizedPartitionTypes: const []),
    );
    await tester.pumpWidget(ProviderScope(
      overrides: [readerRepositoryProvider.overrideWithValue(repository)],
      child: const CupertinoApp(home: AppShell()),
    ));
    await tester.pumpAndSettle();
    await tester.tap(find.text('T7 Shield'));
    await tester.pumpAndSettle();
    expect(find.text('Format tidak didukung'), findsOneWidget);
    repository.devices = [];
    repository.deviceDetachedController.add(7);
    await tester.pumpAndSettle();
    expect(find.text('Format tidak didukung'), findsNothing);
    expect(find.text('SSD terlepas'), findsOneWidget);
    expect(repository.closedDevices, contains(7));
    await tester.pump(const Duration(seconds: 4));
  });
}
