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

String joinPath(Iterable<PathSegment> segments, String name) =>
    [...segments.map((segment) => segment.name), name].join('/');
