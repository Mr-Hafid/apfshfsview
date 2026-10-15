import 'dart:async';
import 'dart:collection';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/reader_repository.dart';

class ThumbnailCache {
  ThumbnailCache(this._repository);

  static const _maxEntries = 400;
  static const _maxConcurrentLoads = 3;
  static const _thumbnailDimension = 256;

  final ReaderRepository _repository;
  final _cache = <String, Uint8List?>{};
  final _inFlight = <String, Future<Uint8List?>>{};
  final _waiting = Queue<Completer<void>>();
  var _activeLoads = 0;

  Future<Uint8List?> load(int volumeHandle, int nodeId) {
    final key = '$volumeHandle:$nodeId';
    if (_cache.containsKey(key)) {
      final value = _cache.remove(key);
      _cache[key] = value;
      return Future.value(value);
    }
    return _inFlight.putIfAbsent(key, () => _fetch(key, volumeHandle, nodeId));
  }

  Future<Uint8List?> _fetch(String key, int volumeHandle, int nodeId) async {
    await _acquireSlot();
    try {
      final bytes = await _repository.thumbnail(volumeHandle, nodeId, _thumbnailDimension);
      _remember(key, bytes);
      return bytes;
    } catch (_) {
      _remember(key, null);
      return null;
    } finally {
      _releaseSlot();
      _inFlight.remove(key);
    }
  }

  Future<void> _acquireSlot() async {
    if (_activeLoads < _maxConcurrentLoads) {
      _activeLoads++;
      return;
    }
    final turn = Completer<void>();
    _waiting.add(turn);
    await turn.future;
  }

  void _releaseSlot() {
    if (_waiting.isNotEmpty) {
      _waiting.removeFirst().complete();
    } else {
      _activeLoads--;
    }
  }

  void _remember(String key, Uint8List? bytes) {
    _cache[key] = bytes;
    if (_cache.length > _maxEntries) _cache.remove(_cache.keys.first);
  }
}

final thumbnailCacheProvider = Provider<ThumbnailCache>((ref) => ThumbnailCache(ref.watch(readerRepositoryProvider)));
