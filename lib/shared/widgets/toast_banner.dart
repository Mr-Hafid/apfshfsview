import 'package:flutter/cupertino.dart';

class ToastBanner {
  static const _visibleDuration = Duration(seconds: 3);

  static void show(BuildContext context, String message) {
    final overlay = Overlay.maybeOf(context, rootOverlay: true);
    if (overlay == null) return;
    final entry = OverlayEntry(builder: (_) => _ToastCapsule(message: message));
    overlay.insert(entry);
    Future<void>.delayed(_visibleDuration, () {
      if (entry.mounted) entry.remove();
    });
  }
}

class _ToastCapsule extends StatelessWidget {
  const _ToastCapsule({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: 24,
      right: 24,
      bottom: MediaQuery.paddingOf(context).bottom + 72,
      child: IgnorePointer(
        child: Center(
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: CupertinoColors.darkBackgroundGray.withValues(alpha: 0.92),
              borderRadius: BorderRadius.circular(22),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
              child: Text(
                message,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: CupertinoColors.white,
                  fontSize: 15,
                  decoration: TextDecoration.none,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
