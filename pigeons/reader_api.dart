import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/bridge/reader_api.g.dart',
    kotlinOut: 'android/app/src/main/kotlin/com/apfshfsview/reader/bridge/ReaderApi.g.kt',
    kotlinOptions: KotlinOptions(package: 'com.apfshfsview.reader.bridge'),
  ),
)
enum NodeKindMessage { file, directory, symlink }

class UsbDeviceMessage {
  UsbDeviceMessage({
    required this.deviceId,
    required this.productName,
    required this.manufacturerName,
    required this.hasPermission,
  });

  int deviceId;
  String productName;
  String manufacturerName;
  bool hasPermission;
}

class VolumeMessage {
  VolumeMessage({
    required this.volumeHandle,
    required this.name,
    required this.format,
    required this.role,
    required this.usedBytes,
    required this.totalBytes,
    required this.isEncrypted,
    required this.isUncleanlyUnmounted,
    required this.failureCode,
  });

  int? volumeHandle;
  String name;
  String format;
  String? role;
  int usedBytes;
  int totalBytes;
  bool isEncrypted;
  bool isUncleanlyUnmounted;
  String? failureCode;
}

class DeviceOpenResult {
  DeviceOpenResult({
    required this.capacityBytes,
    required this.volumes,
    required this.unrecognizedPartitionTypes,
  });

  int capacityBytes;
  List<VolumeMessage> volumes;
  List<String> unrecognizedPartitionTypes;
}

class FileEntryMessage {
  FileEntryMessage({
    required this.nodeId,
    required this.parentId,
    required this.name,
    required this.kind,
    required this.size,
    required this.childCount,
    required this.createdAtMillis,
    required this.modifiedAtMillis,
    required this.isCompressed,
    required this.isReadable,
  });

  int nodeId;
  int parentId;
  String name;
  NodeKindMessage kind;
  int size;
  int childCount;
  int createdAtMillis;
  int modifiedAtMillis;
  bool isCompressed;
  bool isReadable;
}

class CopyProgressMessage {
  CopyProgressMessage({
    required this.jobId,
    required this.currentName,
    required this.copiedBytes,
    required this.totalBytes,
    required this.copiedFiles,
    required this.totalFiles,
    required this.skippedFiles,
    required this.bytesPerSecond,
    required this.state,
    required this.errorMessage,
    required this.destinationName,
  });

  int jobId;
  String currentName;
  int copiedBytes;
  int totalBytes;
  int copiedFiles;
  int totalFiles;
  int skippedFiles;
  int bytesPerSecond;
  String state;
  String? errorMessage;
  String destinationName;
}

class PlayerStateMessage {
  PlayerStateMessage({
    required this.playerId,
    required this.positionMillis,
    required this.durationMillis,
    required this.isPlaying,
    required this.isBuffering,
    required this.hasEnded,
    required this.errorMessage,
  });

  int playerId;
  int positionMillis;
  int durationMillis;
  bool isPlaying;
  bool isBuffering;
  bool hasEnded;
  String? errorMessage;
}

@HostApi()
abstract class ReaderHostApi {
  List<UsbDeviceMessage> listDevices();

  @async
  bool requestPermission(int deviceId);

  @async
  DeviceOpenResult openDevice(int deviceId);

  void closeDevice(int deviceId);

  int rootNodeId(int volumeHandle);

  @async
  List<FileEntryMessage> listDirectory(int volumeHandle, int nodeId);

  @async
  FileEntryMessage stat(int volumeHandle, int nodeId);

  @async
  Uint8List readBytes(int volumeHandle, int nodeId, int offset, int length);

  @async
  Uint8List? thumbnail(int volumeHandle, int nodeId, int maxDimension);

  @async
  String exportToPreviewCache(int volumeHandle, int nodeId);

  String? savedDestinationName();

  @async
  String? pickDestination();

  @async
  int startCopy(int volumeHandle, List<int> nodeIds);

  void cancelCopy(int jobId);

  int createPlayer(int volumeHandle, int nodeId);

  void playerPlay(int playerId);

  void playerPause(int playerId);

  void playerSeekTo(int playerId, int positionMillis);

  void disposePlayer(int playerId);
}

@FlutterApi()
abstract class ReaderEventsApi {
  void onDeviceAttached(UsbDeviceMessage device);

  void onDeviceDetached(int deviceId);

  void onCopyProgress(CopyProgressMessage progress);

  void onPlayerState(PlayerStateMessage state);
}
