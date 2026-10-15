import 'dart:async';
import 'dart:math';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/error_messages.dart';
import '../../shared/format_duration.dart';
import '../browser/browser_location.dart';

const volumePlayerViewType = 'com.apfshfsview.reader/volume-player';

class VideoPlayerScreen extends ConsumerStatefulWidget {
  const VideoPlayerScreen({
    super.key,
    required this.volume,
    required this.entry,
    required this.path,
    required this.isAudio,
  });

  final VolumeContext volume;
  final FileEntryMessage entry;
  final String path;
  final bool isAudio;

  @override
  ConsumerState<VideoPlayerScreen> createState() => _VideoPlayerScreenState();
}

class _VideoPlayerScreenState extends ConsumerState<VideoPlayerScreen> {
  static const _skipMillis = 15000;
  static const _controlsTimeout = Duration(seconds: 3);
  static const _timeStyle = TextStyle(color: CupertinoColors.white, fontSize: 13, fontFeatures: [FontFeature.tabularFigures()]);

  late final ReaderRepository _repository;
  StreamSubscription<PlayerStateMessage>? _stateSubscription;
  Timer? _hideTimer;
  int? _playerId;
  PlayerStateMessage? _state;
  Object? _startError;
  double? _dragPositionMillis;
  bool _controlsVisible = true;

  @override
  void initState() {
    super.initState();
    _repository = ref.read(readerRepositoryProvider);
    _stateSubscription = _repository.playerStates.listen(_onPlayerState);
    _start();
  }

  Future<void> _start() async {
    try {
      final playerId = await _repository.createPlayer(widget.volume.handle, widget.entry.nodeId);
      if (!mounted) {
        await _repository.disposePlayer(playerId);
        return;
      }
      setState(() => _playerId = playerId);
      _scheduleHide();
    } catch (error) {
      if (mounted) setState(() => _startError = error);
    }
  }

  void _onPlayerState(PlayerStateMessage state) {
    if (!mounted || state.playerId != _playerId) return;
    setState(() => _state = state);
    if (state.hasEnded) setState(() => _controlsVisible = true);
  }

  void _scheduleHide() {
    _hideTimer?.cancel();
    _hideTimer = Timer(_controlsTimeout, () {
      if (mounted && (_state?.isPlaying ?? false)) setState(() => _controlsVisible = false);
    });
  }

  void _toggleControls() {
    setState(() => _controlsVisible = !_controlsVisible);
    if (_controlsVisible) _scheduleHide();
  }

  void _togglePlayback() {
    final playerId = _playerId;
    if (playerId == null) return;
    if (_state?.isPlaying ?? false) {
      _repository.playerPause(playerId);
    } else {
      _repository.playerPlay(playerId);
    }
    _scheduleHide();
  }

  void _seekBy(int deltaMillis) {
    final playerId = _playerId;
    final state = _state;
    if (playerId == null || state == null) return;
    _repository.playerSeekTo(playerId, (state.positionMillis + deltaMillis).clamp(0, max(state.durationMillis, 0)));
    _scheduleHide();
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    _stateSubscription?.cancel();
    final playerId = _playerId;
    if (playerId != null) _repository.disposePlayer(playerId);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final playerId = _playerId;
    final state = _state;
    final errorText = _startError != null
        ? describeReaderError(_startError!)
        : state?.errorMessage != null
            ? 'Media tidak bisa diputar (${state!.errorMessage}).'
            : null;
    final isLoading = errorText == null && (playerId == null || state == null || state.isBuffering);
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.black,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _toggleControls,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (playerId != null && !widget.isAudio)
              AndroidView(
                viewType: volumePlayerViewType,
                creationParams: <String, Object>{'playerId': playerId},
                creationParamsCodec: const StandardMessageCodec(),
              ),
            if (widget.isAudio)
              const Center(child: Icon(CupertinoIcons.music_note_2, size: 120, color: Color(0x55FFFFFF))),
            if (errorText != null)
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(32),
                  child: Text(errorText, textAlign: TextAlign.center, style: const TextStyle(color: CupertinoColors.white)),
                ),
              )
            else if (isLoading)
              const Center(child: CupertinoActivityIndicator(color: CupertinoColors.white, radius: 14)),
            AnimatedOpacity(
              opacity: _controlsVisible ? 1 : 0,
              duration: const Duration(milliseconds: 200),
              child: IgnorePointer(ignoring: !_controlsVisible, child: _buildControls(context, state)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildControls(BuildContext context, PlayerStateMessage? state) {
    final duration = max(state?.durationMillis ?? 0, 0);
    final position = _dragPositionMillis ?? (state?.positionMillis ?? 0).toDouble();
    final sliderMax = max(duration, 1).toDouble();
    final isPlaying = state?.isPlaying ?? false;
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0x99000000), Color(0x00000000), Color(0x00000000), Color(0x99000000)],
          stops: [0, 0.2, 0.75, 1],
        ),
      ),
      child: SafeArea(
        child: Column(
          children: [
            Row(
              children: [
                CupertinoButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Selesai', style: TextStyle(color: CupertinoColors.white, fontWeight: FontWeight.w600)),
                ),
                Expanded(
                  child: Text(
                    widget.entry.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: CupertinoColors.white),
                  ),
                ),
                const SizedBox(width: 16),
              ],
            ),
            const Spacer(),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                CupertinoButton(
                  onPressed: () => _seekBy(-_skipMillis),
                  child: const Icon(CupertinoIcons.gobackward_15, color: CupertinoColors.white, size: 34),
                ),
                const SizedBox(width: 24),
                CupertinoButton(
                  onPressed: _togglePlayback,
                  child: Icon(
                    isPlaying ? CupertinoIcons.pause_fill : CupertinoIcons.play_fill,
                    color: CupertinoColors.white,
                    size: 48,
                  ),
                ),
                const SizedBox(width: 24),
                CupertinoButton(
                  onPressed: () => _seekBy(_skipMillis),
                  child: const Icon(CupertinoIcons.goforward_15, color: CupertinoColors.white, size: 34),
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: Row(
                children: [
                  Text(formatPlaybackTime(position.round()), style: _timeStyle),
                  Expanded(
                    child: CupertinoSlider(
                      value: position.clamp(0, sliderMax).toDouble(),
                      max: sliderMax,
                      activeColor: CupertinoColors.white,
                      onChangeStart: (_) => _hideTimer?.cancel(),
                      onChanged: (value) => setState(() => _dragPositionMillis = value),
                      onChangeEnd: (value) {
                        final playerId = _playerId;
                        if (playerId != null) _repository.playerSeekTo(playerId, value.round());
                        setState(() => _dragPositionMillis = null);
                        _scheduleHide();
                      },
                    ),
                  ),
                  Text('-${formatPlaybackTime(max(duration - position.round(), 0))}', style: _timeStyle),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
