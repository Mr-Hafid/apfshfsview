import 'dart:async';
import 'dart:typed_data';

import 'package:reader/bridge/reader_api.g.dart';
import 'package:reader/bridge/reader_repository.dart';

FileEntryMessage fakeEntry({
  required int id,
  required String name,
  NodeKindMessage kind = NodeKindMessage.file,
  int size = 0,
  int childCount = -1,
  int modifiedAtMillis = 0,
  bool isReadable = true,
}) =>
    FileEntryMessage(
      nodeId: id,
      parentId: 2,
      name: name,
      kind: kind,
      size: size,
      childCount: childCount,
      createdAtMillis: 0,
      modifiedAtMillis: modifiedAtMillis,
      isCompressed: !isReadable,
      isReadable: isReadable,
    );

CopyProgressMessage fakeProgress({
  required int jobId,
  required String state,
  int copiedFiles = 0,
  int totalFiles = 0,
  int copiedBytes = 0,
  int totalBytes = 0,
}) =>
    CopyProgressMessage(
      jobId: jobId,
      currentName: 'photo.jpg',
      copiedBytes: copiedBytes,
      totalBytes: totalBytes,
      copiedFiles: copiedFiles,
      totalFiles: totalFiles,
      skippedFiles: 0,
      bytesPerSecond: 0,
      state: state,
      errorMessage: null,
      destinationName: 'Download',
    );

class FakeReaderRepository implements ReaderRepository {
  FakeReaderRepository({
    this.devices = const [],
    this.openResult,
    Map<int, List<FileEntryMessage>>? directories,
  }) : directories = directories ?? {};

  List<UsbDeviceMessage> devices;
  DeviceOpenResult? openResult;
  final Map<int, List<FileEntryMessage>> directories;
  final closedDevices = <int>[];
  final startedCopies = <List<int>>[];
  final deviceAttachedController = StreamController<UsbDeviceMessage>.broadcast();
  final deviceDetachedController = StreamController<int>.broadcast();
  final copyProgressController = StreamController<CopyProgressMessage>.broadcast();
  final playerStatesController = StreamController<PlayerStateMessage>.broadcast();

  @override
  Future<List<UsbDeviceMessage>> listDevices() async => devices;

  @override
  Future<bool> requestPermission(int deviceId) async => true;

  @override
  Future<DeviceOpenResult> openDevice(int deviceId) async => openResult!;

  @override
  Future<void> closeDevice(int deviceId) async => closedDevices.add(deviceId);

  @override
  Future<int> rootNodeId(int volumeHandle) async => 2;

  @override
  Future<List<FileEntryMessage>> listDirectory(int volumeHandle, int nodeId) async => directories[nodeId] ?? const [];

  @override
  Future<FileEntryMessage> stat(int volumeHandle, int nodeId) async =>
      directories.values.expand((entries) => entries).firstWhere((entry) => entry.nodeId == nodeId);

  @override
  Future<Uint8List> readPrefix(int volumeHandle, int nodeId, int length) async => Uint8List(0);

  @override
  Future<Uint8List?> thumbnail(int volumeHandle, int nodeId, int maxDimension) async => null;

  @override
  Future<String> exportToPreviewCache(int volumeHandle, int nodeId) async => '/tmp/preview.pdf';

  @override
  Future<String?> savedDestinationName() async => 'Download';

  @override
  Future<String?> pickDestination() async => 'Download';

  @override
  Future<int> startCopy(int volumeHandle, List<int> nodeIds) async {
    startedCopies.add(nodeIds);
    return startedCopies.length;
  }

  @override
  Future<void> cancelCopy(int jobId) async {}

  @override
  Future<int> createPlayer(int volumeHandle, int nodeId) async => 1;

  @override
  Future<void> playerPlay(int playerId) async {}

  @override
  Future<void> playerPause(int playerId) async {}

  @override
  Future<void> playerSeekTo(int playerId, int positionMillis) async {}

  @override
  Future<void> disposePlayer(int playerId) async {}

  @override
  Stream<UsbDeviceMessage> get deviceAttached => deviceAttachedController.stream;

  @override
  Stream<int> get deviceDetached => deviceDetachedController.stream;

  @override
  Stream<CopyProgressMessage> get copyProgress => copyProgressController.stream;

  @override
  Stream<PlayerStateMessage> get playerStates => playerStatesController.stream;
}
