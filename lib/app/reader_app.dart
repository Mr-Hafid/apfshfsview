import 'package:flutter/cupertino.dart';

import 'app_shell.dart';
import 'theme.dart';

class ReaderApp extends StatelessWidget {
  const ReaderApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const CupertinoApp(
      title: 'APFS HFS Reader',
      theme: readerTheme,
      debugShowCheckedModeBanner: false,
      home: AppShell(),
    );
  }
}
