import '../../bridge/reader_api.g.dart';

class VolumeContext {
  const VolumeContext({required this.handle, required this.name, required this.format});

  final int handle;
  final String name;
  final String format;
}

class PathSegment {
  const PathSegment(this.nodeId, this.name);

  final int nodeId;
  final String name;
}

class BrowserLocation {
  const BrowserLocation({
    required this.volume,
    required this.nodeId,
    required this.title,
    required this.trail,
    required this.previousTitle,
  });

  final VolumeContext volume;
  final int nodeId;
  final String title;
  final List<PathSegment> trail;
  final String previousTitle;

  BrowserLocation child(FileEntryMessage folder) => BrowserLocation(
        volume: volume,
        nodeId: folder.nodeId,
        title: folder.name,
        trail: [...trail, PathSegment(folder.nodeId, folder.name)],
        previousTitle: title,
      );

  String pathOf(FileEntryMessage entry) => [...trail.map((segment) => segment.name), entry.name].join('/');
}

String browserRouteName(int nodeId) => 'browser:$nodeId';
