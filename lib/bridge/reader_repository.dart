import 'dart:async';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'reader_api.g.dart';

abstract class ReaderRepository {
  Future<List<UsbDeviceMessage>> listDevices();
  Future<bool> requestPermission(int deviceId);
  Future<DeviceOpenResult> openDevice(int deviceId);
  Future<void> closeDevice(int deviceId);
  Future<int> rootNodeId(int volumeHandle);
  Future<List<FileEntryMessage>> listDirectory(int volumeHandle, int nodeId);
  Future<FileEntryMessage> stat(int volumeHandle, int nodeId);
  Future<Uint8List> readPrefix(int volumeHandle, int nodeId, int length);
  Future<Uint8List?> thumbnail(int volumeHandle, int nodeId, int maxDimension);
  Future<String> exportToPreviewCache(int volumeHandle, int nodeId);
  Future<String?> savedDestinationName();
  Future<String?> pickDestination();
  Future<int> startCopy(int volumeHandle, List<int> nodeIds);
  Future<void> cancelCopy(int jobId);
  Future<int> createPlayer(int volumeHandle, int nodeId);
  Future<void> playerPlay(int playerId);
  Future<void> playerPause(int playerId);
  Future<void> playerSeekTo(int playerId, int positionMillis);
  Future<void> disposePlayer(int playerId);
  Stream<UsbDeviceMessage> get deviceAttached;
  Stream<int> get deviceDetached;
  Stream<CopyProgressMessage> get copyProgress;
  Stream<PlayerStateMessage> get playerStates;
}

class PigeonReaderRepository implements ReaderRepository, ReaderEventsApi {
  PigeonReaderRepository() {
    ReaderEventsApi.setUp(this);
  }

  static const _maxChunkBytes = 16 * 1024 * 1024;

  final _hostApi = ReaderHostApi();
  final _deviceAttached = StreamController<UsbDeviceMessage>.broadcast();
  final _deviceDetached = StreamController<int>.broadcast();
  final _copyProgress = StreamController<CopyProgressMessage>.broadcast();
  final _playerStates = StreamController<PlayerStateMessage>.broadcast();

  @override
  Future<List<UsbDeviceMessage>> listDevices() => _hostApi.listDevices();

  @override
  Future<bool> requestPermission(int deviceId) => _hostApi.requestPermission(deviceId);

  @override
  Future<DeviceOpenResult> openDevice(int deviceId) => _hostApi.openDevice(deviceId);

  @override
  Future<void> closeDevice(int deviceId) => _hostApi.closeDevice(deviceId);

  @override
  Future<int> rootNodeId(int volumeHandle) => _hostApi.rootNodeId(volumeHandle);

  @override
  Future<List<FileEntryMessage>> listDirectory(int volumeHandle, int nodeId) =>
      _hostApi.listDirectory(volumeHandle, nodeId);

  @override
  Future<FileEntryMessage> stat(int volumeHandle, int nodeId) => _hostApi.stat(volumeHandle, nodeId);

  @override
  Future<Uint8List> readPrefix(int volumeHandle, int nodeId, int length) async {
    final builder = BytesBuilder(copy: false);
    var offset = 0;
    while (offset < length) {
      final chunk = await _hostApi.readBytes(volumeHandle, nodeId, offset, min(_maxChunkBytes, length - offset));
      if (chunk.isEmpty) break;
      builder.add(chunk);
      offset += chunk.length;
    }
    return builder.takeBytes();
  }

  @override
  Future<Uint8List?> thumbnail(int volumeHandle, int nodeId, int maxDimension) =>
      _hostApi.thumbnail(volumeHandle, nodeId, maxDimension);

  @override
  Future<String> exportToPreviewCache(int volumeHandle, int nodeId) =>
      _hostApi.exportToPreviewCache(volumeHandle, nodeId);

  @override
  Future<String?> savedDestinationName() => _hostApi.savedDestinationName();

  @override
  Future<String?> pickDestination() => _hostApi.pickDestination();

  @override
  Future<int> startCopy(int volumeHandle, List<int> nodeIds) => _hostApi.startCopy(volumeHandle, nodeIds);

  @override
  Future<void> cancelCopy(int jobId) => _hostApi.cancelCopy(jobId);

  @override
  Future<int> createPlayer(int volumeHandle, int nodeId) => _hostApi.createPlayer(volumeHandle, nodeId);

  @override
  Future<void> playerPlay(int playerId) => _hostApi.playerPlay(playerId);

  @override
  Future<void> playerPause(int playerId) => _hostApi.playerPause(playerId);

  @override
  Future<void> playerSeekTo(int playerId, int positionMillis) => _hostApi.playerSeekTo(playerId, positionMillis);

  @override
  Future<void> disposePlayer(int playerId) => _hostApi.disposePlayer(playerId);

  @override
  Stream<UsbDeviceMessage> get deviceAttached => _deviceAttached.stream;

  @override
  Stream<int> get deviceDetached => _deviceDetached.stream;

  @override
  Stream<CopyProgressMessage> get copyProgress => _copyProgress.stream;

  @override
  Stream<PlayerStateMessage> get playerStates => _playerStates.stream;

  @override
  void onDeviceAttached(UsbDeviceMessage device) => _deviceAttached.add(device);

  @override
  void onDeviceDetached(int deviceId) => _deviceDetached.add(deviceId);

  @override
  void onCopyProgress(CopyProgressMessage progress) => _copyProgress.add(progress);

  @override
  void onPlayerState(PlayerStateMessage state) => _playerStates.add(state);
}

final readerRepositoryProvider = Provider<ReaderRepository>((ref) => PigeonReaderRepository());
