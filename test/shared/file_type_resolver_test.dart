import 'package:flutter_test/flutter_test.dart';
import 'package:reader/bridge/reader_api.g.dart';
import 'package:reader/shared/file_type_resolver.dart';
import 'package:reader/shared/hidden_files.dart';

void main() {
  test('categories come from extension and folder kind', () {
    expect(categorize(name: 'IMG_0001.HEIC', kind: NodeKindMessage.file), FileCategory.image);
    expect(categorize(name: 'clip.mov', kind: NodeKindMessage.file), FileCategory.video);
    expect(categorize(name: 'Photos.jpg', kind: NodeKindMessage.directory), FileCategory.folder);
    expect(categorize(name: 'README', kind: NodeKindMessage.file), FileCategory.other);
    expect(categorize(name: '.bashrc', kind: NodeKindMessage.file), FileCategory.other);
  });

  test('only viewable categories are previewable', () {
    expect(isPreviewable(FileCategory.pdf), isTrue);
    expect(isPreviewable(FileCategory.code), isTrue);
    expect(isPreviewable(FileCategory.archive), isFalse);
  });

  test('macOS metadata files are hidden', () {
    expect(isHiddenName('.DS_Store'), isTrue);
    expect(isHiddenName('._photo.jpg'), isTrue);
    expect(isHiddenName('photo.jpg'), isFalse);
  });
}
