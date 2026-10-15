import 'package:flutter/cupertino.dart';

import '../../bridge/reader_api.g.dart';
import '../file_icon.dart';
import '../file_type_resolver.dart';

class FinderEntryGlyph extends StatelessWidget {
  const FinderEntryGlyph({super.key, required this.entry, required this.size});

  final FileEntryMessage entry;
  final double size;

  @override
  Widget build(BuildContext context) {
    final category = categorizeEntry(entry);
    if (category == FileCategory.folder) return FinderFolderIcon(size: size);
    final extension = fileExtensionLabel(entry.name);
    final label = entry.kind == NodeKindMessage.symlink ? 'LINK' : extension;
    final labelColor = category == FileCategory.other || category == FileCategory.archive
        ? CupertinoColors.systemGrey.resolveFrom(context)
        : colorForCategory(category).resolveFrom(context);
    return FinderDocumentIcon(
      size: size,
      label: label,
      labelColor: labelColor,
      isArchive: category == FileCategory.archive,
      showsTextLines: category == FileCategory.text || category == FileCategory.code,
    );
  }
}

class FinderFolderIcon extends StatelessWidget {
  const FinderFolderIcon({super.key, required this.size});

  final double size;

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: size,
    child: const CustomPaint(painter: _FolderPainter()),
  );
}

class FinderDocumentIcon extends StatelessWidget {
  const FinderDocumentIcon({
    super.key,
    required this.size,
    required this.label,
    required this.labelColor,
    this.isArchive = false,
    this.showsTextLines = false,
  });

  final double size;
  final String label;
  final Color labelColor;
  final bool isArchive;
  final bool showsTextLines;

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: size,
    child: CustomPaint(
      painter: _DocumentPainter(
        label: label,
        labelColor: labelColor,
        isArchive: isArchive,
        showsTextLines: showsTextLines,
      ),
    ),
  );
}

class _FolderPainter extends CustomPainter {
  const _FolderPainter();

  static const _backColor = Color(0xFF4A9EEA);
  static const _frontTopColor = Color(0xFF8ECCFB);
  static const _frontBottomColor = Color(0xFF5AADF3);

  @override
  void paint(Canvas canvas, Size size) {
    final width = size.width;
    final height = width * 0.8;
    final top = (size.height - height) / 2;
    Offset point(double x, double y) => Offset(x * width / 100, top + y * height / 80);
    final unit = width / 100;

    final back = Path()
      ..moveTo(point(2, 14).dx, point(2, 14).dy)
      ..quadraticBezierTo(point(2, 6).dx, point(2, 6).dy, point(10, 6).dx, point(10, 6).dy)
      ..lineTo(point(34, 6).dx, point(34, 6).dy)
      ..quadraticBezierTo(point(38, 6).dx, point(38, 6).dy, point(40.5, 9).dx, point(40.5, 9).dy)
      ..lineTo(point(44, 13).dx, point(44, 13).dy)
      ..lineTo(point(90, 13).dx, point(90, 13).dy)
      ..quadraticBezierTo(point(98, 13).dx, point(98, 13).dy, point(98, 21).dx, point(98, 21).dy)
      ..lineTo(point(98, 70).dx, point(98, 70).dy)
      ..quadraticBezierTo(point(98, 78).dx, point(98, 78).dy, point(90, 78).dx, point(90, 78).dy)
      ..lineTo(point(10, 78).dx, point(10, 78).dy)
      ..quadraticBezierTo(point(2, 78).dx, point(2, 78).dy, point(2, 70).dx, point(2, 70).dy)
      ..close();
    canvas.drawPath(back, Paint()..color = _backColor);

    final frontRect = Rect.fromPoints(point(2, 22), point(98, 78));
    final front = RRect.fromRectAndRadius(frontRect, Radius.circular(6 * unit));
    canvas.drawShadow(Path()..addRRect(front), const Color(0x66000000), 1.2 * unit, false);
    canvas.drawRRect(
      front,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [_frontTopColor, _frontBottomColor],
        ).createShader(frontRect),
    );
    canvas.drawLine(
      point(5, 22.8),
      point(95, 22.8),
      Paint()
        ..color = const Color(0x66FFFFFF)
        ..strokeWidth = 0.9 * unit,
    );
  }

  @override
  bool shouldRepaint(covariant _FolderPainter oldDelegate) => false;
}

class _DocumentPainter extends CustomPainter {
  const _DocumentPainter({
    required this.label,
    required this.labelColor,
    required this.isArchive,
    required this.showsTextLines,
  });

  final String label;
  final Color labelColor;
  final bool isArchive;
  final bool showsTextLines;

  static const _pageColor = Color(0xFFFFFFFF);
  static const _edgeColor = Color(0xFFC7C7CC);
  static const _foldColor = Color(0xFFE8E8ED);
  static const _detailColor = Color(0xFFB9B9BF);

  @override
  void paint(Canvas canvas, Size size) {
    final pageWidth = size.width * 0.72;
    final pageHeight = size.width * 0.92;
    final left = (size.width - pageWidth) / 2;
    final top = (size.height - pageHeight) / 2;
    final right = left + pageWidth;
    final bottom = top + pageHeight;
    final fold = pageWidth * 0.27;
    final radius = size.width * 0.035;

    final page = Path()
      ..moveTo(left + radius, top)
      ..lineTo(right - fold, top)
      ..lineTo(right, top + fold)
      ..lineTo(right, bottom - radius)
      ..quadraticBezierTo(right, bottom, right - radius, bottom)
      ..lineTo(left + radius, bottom)
      ..quadraticBezierTo(left, bottom, left, bottom - radius)
      ..lineTo(left, top + radius)
      ..quadraticBezierTo(left, top, left + radius, top)
      ..close();
    canvas.drawShadow(page, const Color(0x55000000), size.width * 0.025, false);
    canvas.drawPath(page, Paint()..color = _pageColor);
    canvas.drawPath(
      page,
      Paint()
        ..color = _edgeColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = size.width * 0.012,
    );

    final foldPath = Path()
      ..moveTo(right - fold, top)
      ..lineTo(right - fold, top + fold - radius)
      ..quadraticBezierTo(right - fold, top + fold, right - fold + radius, top + fold)
      ..lineTo(right, top + fold)
      ..close();
    canvas.drawPath(foldPath, Paint()..color = _foldColor);
    canvas.drawPath(
      foldPath,
      Paint()
        ..color = _edgeColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = size.width * 0.012,
    );

    if (isArchive) _paintZipper(canvas, left + pageWidth / 2, top, pageWidth, pageHeight);
    if (showsTextLines) _paintTextLines(canvas, left, top, pageWidth, pageHeight);
    if (label.isNotEmpty) _paintLabel(canvas, left, bottom, pageWidth, pageHeight);
  }

  void _paintZipper(Canvas canvas, double centerX, double top, double pageWidth, double pageHeight) {
    final paint = Paint()..color = _detailColor;
    final toothWidth = pageWidth * 0.1;
    final toothHeight = pageHeight * 0.035;
    for (var index = 0; index < 8; index++) {
      final y = top + toothHeight * (index * 1.6 + 0.6);
      final x = index.isEven ? centerX - toothWidth : centerX;
      canvas.drawRect(Rect.fromLTWH(x, y, toothWidth, toothHeight), paint);
    }
    final pullTop = top + toothHeight * 13.6;
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(centerX - toothWidth * 0.9, pullTop, toothWidth * 1.8, toothHeight * 3.4),
        Radius.circular(toothWidth * 0.4),
      ),
      paint,
    );
  }

  void _paintTextLines(Canvas canvas, double left, double top, double pageWidth, double pageHeight) {
    final paint = Paint()
      ..color = _detailColor
      ..strokeWidth = pageHeight * 0.022
      ..strokeCap = StrokeCap.round;
    final startX = left + pageWidth * 0.18;
    for (var index = 0; index < 5; index++) {
      final y = top + pageHeight * (0.36 + index * 0.075);
      final endFraction = index == 4 ? 0.55 : 0.82;
      canvas.drawLine(Offset(startX, y), Offset(left + pageWidth * endFraction, y), paint);
    }
  }

  void _paintLabel(Canvas canvas, double left, double bottom, double pageWidth, double pageHeight) {
    final fontSize = pageHeight * (label.length > 4 ? 0.11 : 0.14);
    final painter = TextPainter(
      text: TextSpan(
        text: label,
        style: TextStyle(
          fontFamily: 'CupertinoSystemText',
          color: labelColor,
          fontSize: fontSize,
          fontWeight: FontWeight.w700,
          letterSpacing: fontSize * 0.04,
        ),
      ),
      textDirection: TextDirection.ltr,
      maxLines: 1,
      ellipsis: '…',
    )..layout(maxWidth: pageWidth * 0.86);
    painter.paint(canvas, Offset(left + (pageWidth - painter.width) / 2, bottom - pageHeight * 0.1 - painter.height));
  }

  @override
  bool shouldRepaint(covariant _DocumentPainter oldDelegate) =>
      oldDelegate.label != label ||
      oldDelegate.labelColor != labelColor ||
      oldDelegate.isArchive != isArchive ||
      oldDelegate.showsTextLines != showsTextLines;
}
