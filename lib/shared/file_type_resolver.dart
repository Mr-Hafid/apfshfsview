import '../bridge/reader_api.g.dart';

enum FileCategory { folder, image, video, audio, pdf, text, archive, code, other }

const _categoriesByExtension = <String, FileCategory>{
  'jpg': FileCategory.image,
  'jpeg': FileCategory.image,
  'png': FileCategory.image,
  'gif': FileCategory.image,
  'webp': FileCategory.image,
  'heic': FileCategory.image,
  'heif': FileCategory.image,
  'bmp': FileCategory.image,
  'mp4': FileCategory.video,
  'mov': FileCategory.video,
  'm4v': FileCategory.video,
  'mkv': FileCategory.video,
  'webm': FileCategory.video,
  '3gp': FileCategory.video,
  'avi': FileCategory.video,
  'mp3': FileCategory.audio,
  'm4a': FileCategory.audio,
  'aac': FileCategory.audio,
  'wav': FileCategory.audio,
  'flac': FileCategory.audio,
  'ogg': FileCategory.audio,
  'opus': FileCategory.audio,
  'pdf': FileCategory.pdf,
  'txt': FileCategory.text,
  'md': FileCategory.text,
  'csv': FileCategory.text,
  'log': FileCategory.text,
  'json': FileCategory.text,
  'xml': FileCategory.text,
  'yaml': FileCategory.text,
  'yml': FileCategory.text,
  'ini': FileCategory.text,
  'conf': FileCategory.text,
  'srt': FileCategory.text,
  'html': FileCategory.text,
  'htm': FileCategory.text,
  'dart': FileCategory.code,
  'kt': FileCategory.code,
  'java': FileCategory.code,
  'swift': FileCategory.code,
  'py': FileCategory.code,
  'js': FileCategory.code,
  'c': FileCategory.code,
  'h': FileCategory.code,
  'cpp': FileCategory.code,
  'sh': FileCategory.code,
  'go': FileCategory.code,
  'rs': FileCategory.code,
  'zip': FileCategory.archive,
  'rar': FileCategory.archive,
  '7z': FileCategory.archive,
  'tar': FileCategory.archive,
  'gz': FileCategory.archive,
  'xz': FileCategory.archive,
  'dmg': FileCategory.archive,
};

const _previewableCategories = {
  FileCategory.image,
  FileCategory.video,
  FileCategory.audio,
  FileCategory.pdf,
  FileCategory.text,
  FileCategory.code,
};

FileCategory categorize({required String name, required NodeKindMessage kind}) {
  if (kind == NodeKindMessage.directory) return FileCategory.folder;
  final dotIndex = name.lastIndexOf('.');
  if (dotIndex <= 0 || dotIndex == name.length - 1) return FileCategory.other;
  return _categoriesByExtension[name.substring(dotIndex + 1).toLowerCase()] ?? FileCategory.other;
}

FileCategory categorizeEntry(FileEntryMessage entry) => categorize(name: entry.name, kind: entry.kind);

bool isPreviewable(FileCategory category) => _previewableCategories.contains(category);

String categoryLabel(FileCategory category) => switch (category) {
  FileCategory.folder => 'Folder',
  FileCategory.image => 'Gambar',
  FileCategory.video => 'Video',
  FileCategory.audio => 'Audio',
  FileCategory.pdf => 'Dokumen PDF',
  FileCategory.text => 'Teks',
  FileCategory.archive => 'Arsip',
  FileCategory.code => 'Kode',
  FileCategory.other => 'File',
};

String fileExtensionLabel(String name) {
  final dotIndex = name.lastIndexOf('.');
  if (dotIndex <= 0 || dotIndex == name.length - 1) return '';
  return name.substring(dotIndex + 1).toUpperCase();
}

String entryKindLabel(FileEntryMessage entry) {
  final category = categorizeEntry(entry);
  if (category == FileCategory.folder) return 'Folder';
  if (entry.kind == NodeKindMessage.symlink) return 'Alias';
  final extension = fileExtensionLabel(entry.name);
  final label = categoryLabel(category);
  if (extension.isEmpty || label.endsWith(extension)) return label;
  return category == FileCategory.other ? 'File $extension' : '$label $extension';
}
