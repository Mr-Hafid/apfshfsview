import 'package:flutter/cupertino.dart';

import 'file_type_resolver.dart';

IconData iconForCategory(FileCategory category) => switch (category) {
      FileCategory.folder => CupertinoIcons.folder_fill,
      FileCategory.image => CupertinoIcons.photo,
      FileCategory.video => CupertinoIcons.film,
      FileCategory.audio => CupertinoIcons.music_note_2,
      FileCategory.pdf => CupertinoIcons.doc_richtext,
      FileCategory.text => CupertinoIcons.doc_text,
      FileCategory.archive => CupertinoIcons.archivebox,
      FileCategory.code => CupertinoIcons.chevron_left_slash_chevron_right,
      FileCategory.other => CupertinoIcons.doc,
    };

CupertinoDynamicColor colorForCategory(FileCategory category) => switch (category) {
      FileCategory.folder => CupertinoColors.systemBlue,
      FileCategory.image => CupertinoColors.systemOrange,
      FileCategory.video => CupertinoColors.systemPurple,
      FileCategory.audio => CupertinoColors.systemPink,
      FileCategory.pdf => CupertinoColors.systemRed,
      FileCategory.text => CupertinoColors.systemGrey,
      FileCategory.archive => CupertinoColors.systemBrown,
      FileCategory.code => CupertinoColors.systemIndigo,
      FileCategory.other => CupertinoColors.systemGrey2,
    };
