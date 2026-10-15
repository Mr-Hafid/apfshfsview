import 'package:flutter/cupertino.dart';

class FinderPalette {
  const FinderPalette._();

  static const _window = CupertinoDynamicColor.withBrightness(color: Color(0xFFFFFFFF), darkColor: Color(0xFF000000));
  static const _stripe = CupertinoDynamicColor.withBrightness(color: Color(0xFFF4F5F5), darkColor: Color(0xFF141416));
  static const _sidebar = CupertinoDynamicColor.withBrightness(color: Color(0xFFF2F2F4), darkColor: Color(0xFF1C1C1E));
  static const _control = CupertinoDynamicColor.withBrightness(color: Color(0xFFFFFFFF), darkColor: Color(0xFF2C2C2E));
  static const _controlBorder = CupertinoDynamicColor.withBrightness(
    color: Color(0x1A000000),
    darkColor: Color(0x26FFFFFF),
  );
  static const _selectionTile = CupertinoDynamicColor.withBrightness(
    color: Color(0xFFE3E3E8),
    darkColor: Color(0xFF2C2C2E),
  );
  static const _segmentSelected = CupertinoDynamicColor.withBrightness(
    color: Color(0xFFE6E6EB),
    darkColor: Color(0xFF48484A),
  );
  static const _pressed = CupertinoDynamicColor.withBrightness(color: Color(0xFFE5E5EA), darkColor: Color(0xFF48484A));
  static const _arrowEnabled = CupertinoDynamicColor.withBrightness(
    color: Color(0xFF1D1D1F),
    darkColor: Color(0xFFF5F5F7),
  );
  static const _arrowDisabled = CupertinoDynamicColor.withBrightness(
    color: Color(0x33000000),
    darkColor: Color(0x38FFFFFF),
  );

  static Color window(BuildContext context) => _window.resolveFrom(context);
  static Color stripe(BuildContext context) => _stripe.resolveFrom(context);
  static Color sidebar(BuildContext context) => _sidebar.resolveFrom(context);
  static Color control(BuildContext context) => _control.resolveFrom(context);
  static Color controlBorder(BuildContext context) => _controlBorder.resolveFrom(context);
  static Color selectionTile(BuildContext context) => _selectionTile.resolveFrom(context);
  static Color segmentSelected(BuildContext context) => _segmentSelected.resolveFrom(context);
  static Color pressed(BuildContext context) => _pressed.resolveFrom(context);
  static Color arrowEnabled(BuildContext context) => _arrowEnabled.resolveFrom(context);
  static Color arrowDisabled(BuildContext context) => _arrowDisabled.resolveFrom(context);
  static Color accent(BuildContext context) => CupertinoTheme.of(context).primaryColor;
  static Color secondaryText(BuildContext context) => CupertinoColors.secondaryLabel.resolveFrom(context);
  static Color hairline(BuildContext context) => CupertinoColors.separator.resolveFrom(context);

  static BoxDecoration controlDecoration(BuildContext context, {required BorderRadius radius}) => BoxDecoration(
    color: control(context),
    borderRadius: radius,
    border: Border.all(color: controlBorder(context), width: 0.6),
    boxShadow: const [BoxShadow(color: Color(0x14000000), blurRadius: 6, offset: Offset(0, 1))],
  );
}
