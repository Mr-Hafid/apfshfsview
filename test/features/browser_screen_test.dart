import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:reader/bridge/reader_api.g.dart';
import 'package:reader/bridge/reader_repository.dart';
import 'package:reader/features/browser/browser_location.dart';
import 'package:reader/features/browser/browser_screen.dart';

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
          fakeEntry(id: 10, name: 'Photos', kind: NodeKindMessage.directory, childCount: 3),
        ],
      },
    );
  });

  Widget buildBrowser() => ProviderScope(
        overrides: [readerRepositoryProvider.overrideWithValue(repository)],
        child: const CupertinoApp(
          home: BrowserScreen(
            location: BrowserLocation(
              volume: VolumeContext(handle: 1, name: 'Fixture', format: 'APFS'),
              nodeId: 2,
              title: 'Fixture',
              trail: [PathSegment(2, 'Fixture')],
              previousTitle: 'SSD',
            ),
          ),
        ),
      );

  testWidgets('hides dotfiles by default and shows folders first', (tester) async {
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    expect(find.text('.DS_Store'), findsNothing);
    final folder = tester.getTopLeft(find.text('Photos'));
    final file = tester.getTopLeft(find.text('notes.txt'));
    expect(folder.dy < file.dy || (folder.dy == file.dy && folder.dx < file.dx), isTrue);
  });

  testWidgets('select mode counts selected items', (tester) async {
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Pilih'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('notes.txt'));
    await tester.tap(find.text('clip.mov'));
    await tester.pump();
    expect(find.text('2 dipilih'), findsOneWidget);
  });

  testWidgets('list layout shows date and size subtitle', (tester) async {
    await tester.pumpWidget(buildBrowser());
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(CupertinoIcons.list_bullet));
    await tester.pumpAndSettle();
    expect(find.textContaining('1,5 KB'), findsOneWidget);
    expect(find.textContaining('3 item'), findsOneWidget);
  });
}
