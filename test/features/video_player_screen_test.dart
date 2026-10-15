import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:reader/bridge/reader_api.g.dart';
import 'package:reader/bridge/reader_repository.dart';
import 'package:reader/features/browser/browser_location.dart';
import 'package:reader/features/player/video_player_screen.dart';

import '../support/fake_reader_repository.dart';

double controlsOpacity(WidgetTester tester) =>
    tester.widget<AnimatedOpacity>(find.byKey(const ValueKey('player-controls'))).opacity;

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform_views,
      (call) async => call.method == 'create' ? 0 : null,
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform_views,
      null,
    );
  });

  testWidgets('controls hide while playing and come back on tap over the video', (tester) async {
    final repository = FakeReaderRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [readerRepositoryProvider.overrideWithValue(repository)],
        child: CupertinoApp(
          home: VideoPlayerScreen(
            volume: const VolumeContext(handle: 1, name: 'Fixture', format: 'APFS'),
            entry: fakeEntry(id: 13, name: 'clip.mov', size: 5000000),
            path: 'Fixture/clip.mov',
            isAudio: false,
          ),
        ),
      ),
    );
    await tester.pump();
    repository.playerStatesController.add(
      PlayerStateMessage(
        playerId: 1,
        positionMillis: 1000,
        durationMillis: 60000,
        isPlaying: true,
        isBuffering: false,
        hasEnded: false,
        errorMessage: null,
      ),
    );
    await tester.pump();
    expect(controlsOpacity(tester), 1);

    await tester.pump(const Duration(seconds: 4));
    await tester.pumpAndSettle();
    expect(controlsOpacity(tester), 0);

    await tester.tapAt(tester.getCenter(find.byType(AndroidView)));
    await tester.pumpAndSettle();
    expect(controlsOpacity(tester), 1);

    await tester.tapAt(const Offset(200, 150));
    await tester.pumpAndSettle();
    expect(controlsOpacity(tester), 0);
  });
}
