import 'package:flutter/widgets.dart';

const readableContentWidth = 720.0;

EdgeInsets readableInsets(BuildContext context) {
  final width = MediaQuery.sizeOf(context).width;
  final inset = width > readableContentWidth ? (width - readableContentWidth) / 2 : 0.0;
  return EdgeInsets.symmetric(horizontal: inset);
}
