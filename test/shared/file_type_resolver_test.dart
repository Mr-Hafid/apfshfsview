import 'package:flutter_test/flutter_test.dart';
import 'package:reader/bridge/reader_api.g.dart';
import 'package:reader/shared/file_type_resolver.dart';
import 'package:reader/shared/hidden_files.dart';
import 'package:reader/features/browser/entry_subtitle.dart';

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

  test('kind labels avoid repeating the extension', () {
    FileEntryMessage file(String name) => FileEntryMessage(
      nodeId: 1,
      parentId: 2,
      name: name,
      kind: NodeKindMessage.file,
      size: 0,
      childCount: -1,
      createdAtMillis: 0,
      modifiedAtMillis: 0,
      isCompressed: false,
      isReadable: true,
    );
    expect(entryKindLabel(file('invoice.pdf')), 'Dokumen PDF');
    expect(entryKindLabel(file('clip.MOV')), 'Video MOV');
    expect(entryKindLabel(file('laporan.docx')), 'File DOCX');
    expect(entryKindLabel(file('README')), 'File');
    expect(fileExtensionLabel('archive.tar.gz'), 'GZ');
  });

  test('folders without a known child count are labelled as folders', () {
    FileEntryMessage folder(int childCount) => FileEntryMessage(
      nodeId: 1,
      parentId: 2,
      name: 'Foto',
      kind: NodeKindMessage.directory,
      size: 0,
      childCount: childCount,
      createdAtMillis: 0,
      modifiedAtMillis: 0,
      isCompressed: false,
      isReadable: true,
    );
    expect(folderContentLabel(folder(12)), '12 item');
    expect(folderContentLabel(folder(-1)), 'Folder');
  });
}
