import 'package:flutter/cupertino.dart';

class ThinProgressBar extends StatelessWidget {
  const ThinProgressBar({super.key, required this.fraction, this.height = 4});

  final double fraction;
  final double height;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(height / 2),
      child: SizedBox(
        height: height,
        child: Stack(
          children: [
            Positioned.fill(child: ColoredBox(color: CupertinoColors.systemFill.resolveFrom(context))),
            Align(
              alignment: Alignment.centerLeft,
              child: FractionallySizedBox(
                widthFactor: fraction.isFinite ? fraction.clamp(0.0, 1.0) : 0,
                heightFactor: 1,
                child: ColoredBox(color: CupertinoTheme.of(context).primaryColor),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
