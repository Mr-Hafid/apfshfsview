import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:reader/bridge/reader_api.g.dart';
import 'package:reader/bridge/reader_repository.dart';
import 'package:reader/features/browser/browser_location.dart';
import 'package:reader/features/browser/browser_screen.dart';
import 'package:reader/features/browser/finder_sidebar.dart';

import '../support/fake_reader_repository.dart';

void main() {
  late FakeReaderRepository repository;

  setUp(() {
    repository = FakeReaderRepository(
      directories: {
        2: [
          fakeEntry(id: 11, name: 'notes.txt', size: 1500),
          fakeEntry(id: 12, name: '.DS_Store', size: 6148),
          fakeEntry(id: 13, name: 'clip.mov', size: 5000000),
          fakeEntry(id: 10, name: 'Photos', kind: NodeKindMessage.directory, childCount: 2),
        ],
        10: [fakeEntry(id: 21, name: 'beach.jpg', size: 3200000), fakeEntry(id: 22, name: 'sunset.jpg', size: 2100000)],
      },
    );
  });

  Widget buildBrowser({BrowserSidebarContext? sidebar}) => ProviderScope(
    overrides: [readerRepositoryProvider.overrideWithValue(repository)],
    child: CupertinoApp(
      home: BrowserScreen(
        volume: const VolumeContext(handle: 1, name: 'Fixture', format: 'APFS'),
        rootNodeId: 2,
        sidebar: sidebar,
      ),
    ),
  );

  testWidgets('icon view hides dotfiles and shows folders first', (tester) async {
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    expect(find.text('.DS_Store'), findsNothing);
    final folder = tester.getTopLeft(find.text('Photos'));
    final file = tester.getTopLeft(find.text('notes.txt'));
    expect(folder.dy < file.dy || ((folder.dy - file.dy).abs() < 1 && folder.dx < file.dx), isTrue);
    expect(find.text('3 item'), findsOneWidget);
  });

  testWidgets('opening a folder updates title and path, back and forward navigate history', (tester) async {
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Photos'));
    await tester.pumpAndSettle();
    expect(find.text('beach.jpg'), findsOneWidget);
    expect(find.text('Fixture'), findsOneWidget);
    expect(find.text('2 item'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('finder-back')));
    await tester.pumpAndSettle();
    expect(find.text('notes.txt'), findsOneWidget);
    expect(find.text('beach.jpg'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('finder-forward')));
    await tester.pumpAndSettle();
    expect(find.text('beach.jpg'), findsOneWidget);

    await tester.tap(find.text('Fixture'));
    await tester.pumpAndSettle();
    expect(find.text('notes.txt'), findsOneWidget);
  });

  testWidgets('select mode from the action menu counts selected items', (tester) async {
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('finder-actions')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(CupertinoActionSheetAction, 'Pilih'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('notes.txt'));
    await tester.tap(find.text('clip.mov'));
    await tester.pump();
    expect(find.text('2 dipilih'), findsOneWidget);
    expect(find.text('Salin ke HP'), findsOneWidget);
  });

  testWidgets('list view shows sizes and expands folders inline with the disclosure triangle', (tester) async {
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(CupertinoIcons.list_bullet));
    await tester.pumpAndSettle();
    expect(find.text('1,5 KB'), findsOneWidget);
    expect(find.text('beach.jpg'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('disclosure-10')));
    await tester.pumpAndSettle();
    expect(find.text('beach.jpg'), findsOneWidget);
    expect(find.text('sunset.jpg'), findsOneWidget);
    expect(tester.getTopLeft(find.text('beach.jpg')).dx, greaterThan(tester.getTopLeft(find.text('Photos')).dx));

    await tester.tap(find.byKey(const ValueKey('disclosure-10')));
    await tester.pumpAndSettle();
    expect(find.text('beach.jpg'), findsNothing);
  });

  testWidgets('wide list view shows Finder columns', (tester) async {
    tester.view.physicalSize = const Size(1100, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(CupertinoIcons.list_bullet));
    await tester.pumpAndSettle();
    for (final header in ['Nama', 'Tanggal Diubah', 'Ukuran', 'Jenis']) {
      expect(find.text(header), findsOneWidget, reason: header);
    }
    expect(find.text('Teks TXT'), findsOneWidget);
    expect(find.text('Video MOV'), findsOneWidget);
  });

  testWidgets('long press opens the action menu and its actions work', (tester) async {
    String? clipboardText;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') clipboardText = (call.arguments as Map)['text'] as String;
        return null;
      },
    );
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();

    await tester.longPress(find.text('notes.txt'));
    await tester.pumpAndSettle();
    for (final label in ['Buka', 'Salin ke HP', 'Salin Nama', 'Info', 'Pilih', 'Batal']) {
      expect(find.text(label), findsWidgets, reason: label);
    }
    await tester.tap(find.text('Salin Nama'));
    await tester.pumpAndSettle();
    expect(clipboardText, 'notes.txt');
    await tester.pump(const Duration(seconds: 4));

    await tester.longPress(find.text('notes.txt'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Salin ke HP'));
    await tester.pumpAndSettle();
    expect(find.text('Salin 1 item (1,5 KB)?'), findsOneWidget);
    await tester.tap(find.text('Salin'));
    await tester.pumpAndSettle();
    expect(repository.startedCopies, [
      [11],
    ]);
    await tester.pump(const Duration(seconds: 4));

    await tester.longPress(find.text('clip.mov'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(CupertinoActionSheetAction, 'Pilih'));
    await tester.pumpAndSettle();
    expect(find.text('1 dipilih'), findsOneWidget);
  });

  testWidgets('tablet shows the sidebar with volumes, eject, and root folders', (tester) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    var ejectCount = 0;
    await tester.pumpWidget(
      buildBrowser(
        sidebar: BrowserSidebarContext(
          deviceName: 'T7 Shield',
          volumes: [
            VolumeMessage(
              volumeHandle: 1,
              name: 'Fixture',
              format: 'APFS',
              role: null,
              usedBytes: 1,
              totalBytes: 2,
              isEncrypted: false,
              isUncleanlyUnmounted: false,
              failureCode: null,
            ),
          ],
          onSelectVolume: (_) {},
          onEject: () => ejectCount++,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('T7 Shield'), findsOneWidget);
    expect(find.text('Photos'), findsNWidgets(2));
    expect(find.text('Kembali/Maju'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('sidebar-eject')));
    expect(ejectCount, 1);
  });

  testWidgets('icon grid adds columns on wide screens instead of enlarging tiles', (tester) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    repository.directories[2] = [
      for (var index = 0; index < 30; index++) fakeEntry(id: 100 + index, name: 'file_$index.bin'),
    ];
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    final firstRowTop = tester.getTopLeft(find.text('file_0.bin')).dy;
    final itemsInFirstRow = [
      for (var index = 0; index < 30; index++)
        if (find.text('file_$index.bin').evaluate().isNotEmpty &&
            tester.getTopLeft(find.text('file_$index.bin')).dy == firstRowTop)
          index,
    ];
    expect(itemsInFirstRow.length, greaterThanOrEqualTo(10));
  });

  testWidgets('opening slides the new folder in from the right and back slides in from the left', (tester) async {
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    final notesRestingX = tester.getTopLeft(find.text('notes.txt')).dx;

    await tester.tap(find.text('Photos'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));
    expect(find.text('notes.txt'), findsOneWidget);
    final beachMovingX = tester.getTopLeft(find.text('beach.jpg')).dx;
    await tester.pumpAndSettle();
    expect(find.text('notes.txt'), findsNothing);
    expect(beachMovingX, greaterThan(tester.getTopLeft(find.text('beach.jpg')).dx));

    await tester.tap(find.byKey(const ValueKey('finder-back')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));
    expect(tester.getTopLeft(find.text('notes.txt')).dx, lessThan(notesRestingX));
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(find.text('notes.txt')).dx, notesRestingX);
  });
}
