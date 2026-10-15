import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:reader/bridge/reader_api.g.dart';
import 'package:reader/bridge/reader_repository.dart';
import 'package:reader/features/devices/devices_screen.dart';
import 'package:reader/features/volumes/volumes_screen.dart';

import '../support/fake_reader_repository.dart';

Widget wrap(FakeReaderRepository repository, Widget child) => ProviderScope(
      overrides: [readerRepositoryProvider.overrideWithValue(repository)],
      child: CupertinoApp(home: child),
    );

VolumeMessage volume({int? handle, required String name, bool encrypted = false, String? failureCode}) => VolumeMessage(
      volumeHandle: handle,
      name: name,
      format: 'APFS',
      role: 'Data',
      usedBytes: 120000000000,
      totalBytes: 500000000000,
      isEncrypted: encrypted,
      isUncleanlyUnmounted: false,
      failureCode: failureCode,
    );

void main() {
  final device = UsbDeviceMessage(deviceId: 7, productName: 'T7 Shield', manufacturerName: 'Samsung', hasPermission: true);

  testWidgets('empty device list asks to plug in the SSD', (tester) async {
    await tester.pumpWidget(wrap(FakeReaderRepository(), const DevicesScreen()));
    await tester.pumpAndSettle();
    expect(find.text('Colokkan SSD lewat OTG'), findsOneWidget);
  });

  testWidgets('device without permission shows grant button', (tester) async {
    final repository = FakeReaderRepository(devices: [
      UsbDeviceMessage(deviceId: 7, productName: 'T7 Shield', manufacturerName: 'Samsung', hasPermission: false),
    ]);
    await tester.pumpWidget(wrap(repository, const DevicesScreen()));
    await tester.pumpAndSettle();
    expect(find.text('T7 Shield'), findsOneWidget);
    expect(find.text('Izinkan'), findsOneWidget);
  });

  testWidgets('volumes list readable, encrypted, and unsupported partitions', (tester) async {
    final repository = FakeReaderRepository(
      openResult: DeviceOpenResult(
        capacityBytes: 500000000000,
        volumes: [
          volume(handle: 1, name: 'Data'),
          volume(name: 'Secret', encrypted: true, failureCode: 'unsupported_encryption'),
        ],
        unrecognizedPartitionTypes: const [],
      ),
    );
    await tester.pumpWidget(wrap(repository, VolumesScreen(device: device)));
    await tester.pumpAndSettle();
    expect(find.text('APFS · Data · 120 GB dari 500 GB'), findsOneWidget);
    expect(find.text('Terenkripsi — tidak didukung'), findsOneWidget);
  });

  testWidgets('device without known partitions explains the format', (tester) async {
    final repository = FakeReaderRepository(
      openResult: DeviceOpenResult(capacityBytes: 1, volumes: const [], unrecognizedPartitionTypes: const ['EBD0A0A2-B9E5-4433-87C0-68B6B72699C7']),
    );
    await tester.pumpWidget(wrap(repository, VolumesScreen(device: device)));
    await tester.pumpAndSettle();
    expect(find.text('Format tidak didukung'), findsOneWidget);
    expect(find.textContaining('EBD0A0A2'), findsOneWidget);
  });
}
