import 'package:flutter/cupertino.dart';

import 'browser_preferences.dart';
import 'finder_palette.dart';

class FinderToolbar extends StatelessWidget {
  const FinderToolbar({
    super.key,
    required this.title,
    required this.canGoForward,
    required this.onBack,
    required this.onForward,
    required this.layout,
    required this.onLayoutChanged,
    required this.onSelect,
    required this.onActions,
    required this.showsLabels,
    required this.isSelecting,
    required this.selectedCount,
    required this.allSelected,
    required this.onToggleSelectAll,
    required this.onDoneSelecting,
  });

  final String title;
  final bool canGoForward;
  final VoidCallback onBack;
  final VoidCallback onForward;
  final BrowserLayout layout;
  final ValueChanged<BrowserLayout> onLayoutChanged;
  final VoidCallback onSelect;
  final VoidCallback onActions;
  final bool showsLabels;
  final bool isSelecting;
  final int selectedCount;
  final bool allSelected;
  final VoidCallback onToggleSelectAll;
  final VoidCallback onDoneSelecting;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: FinderPalette.window(context),
      child: Padding(
        padding: EdgeInsets.fromLTRB(12, MediaQuery.paddingOf(context).top + 8, 12, 8),
        child: SizedBox(
          height: showsLabels ? 54 : 40,
          child: isSelecting ? _buildSelectionRow(context) : _buildNormalRow(context),
        ),
      ),
    );
  }

  Widget _buildSelectionRow(BuildContext context) {
    return Row(
      children: [
        CupertinoButton(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          onPressed: onToggleSelectAll,
          child: Text(allSelected ? 'Batal Pilih' : 'Pilih Semua'),
        ),
        Expanded(
          child: Center(
            child: Text('$selectedCount dipilih', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
          ),
        ),
        CupertinoButton(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          onPressed: onDoneSelecting,
          child: const Text('Selesai', style: TextStyle(fontWeight: FontWeight.w700)),
        ),
      ],
    );
  }

  Widget _buildNormalRow(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Labeled(
          label: showsLabels ? 'Kembali/Maju' : null,
          child: _NavigationCapsule(canGoForward: canGoForward, onBack: onBack, onForward: onForward),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: SizedBox(
            height: 36,
            child: Align(
              alignment: Alignment.centerLeft,
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 200),
                layoutBuilder: (current, previous) =>
                    Stack(alignment: Alignment.centerLeft, children: [...previous, ?current]),
                child: Text(
                  title,
                  key: ValueKey(title),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(width: 8),
        _Labeled(
          label: showsLabels ? 'Tampilan' : null,
          child: _ViewPill(layout: layout, onChanged: onLayoutChanged),
        ),
        if (showsLabels) ...[
          const SizedBox(width: 10),
          _Labeled(
            label: 'Pilih',
            child: FinderCircleButton(icon: CupertinoIcons.checkmark_circle, onPressed: onSelect),
          ),
        ],
        const SizedBox(width: 10),
        _Labeled(
          label: showsLabels ? 'Aksi' : null,
          child: FinderCircleButton(
            key: const ValueKey('finder-actions'),
            icon: CupertinoIcons.ellipsis,
            onPressed: onActions,
          ),
        ),
      ],
    );
  }
}

class _Labeled extends StatelessWidget {
  const _Labeled({required this.label, required this.child});

  final String? label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final text = label;
    if (text == null) return child;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        child,
        const SizedBox(height: 3),
        Text(text, style: TextStyle(fontSize: 11, color: FinderPalette.secondaryText(context))),
      ],
    );
  }
}

class FinderCircleButton extends StatelessWidget {
  const FinderCircleButton({super.key, required this.icon, required this.onPressed});

  final IconData icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onPressed,
      child: Container(
        width: 36,
        height: 36,
        decoration: FinderPalette.controlDecoration(context, radius: BorderRadius.circular(18)),
        child: Icon(icon, size: 19, color: CupertinoColors.label.resolveFrom(context)),
      ),
    );
  }
}

class _NavigationCapsule extends StatelessWidget {
  const _NavigationCapsule({required this.canGoForward, required this.onBack, required this.onForward});

  final bool canGoForward;
  final VoidCallback onBack;
  final VoidCallback onForward;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 36,
      padding: const EdgeInsets.all(2),
      decoration: FinderPalette.controlDecoration(context, radius: BorderRadius.circular(18)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _NavigationArrow(
            key: const ValueKey('finder-back'),
            icon: CupertinoIcons.chevron_left,
            isEnabled: true,
            onTap: onBack,
          ),
          _NavigationArrow(
            key: const ValueKey('finder-forward'),
            icon: CupertinoIcons.chevron_right,
            isEnabled: canGoForward,
            onTap: onForward,
          ),
        ],
      ),
    );
  }
}

class _NavigationArrow extends StatefulWidget {
  const _NavigationArrow({super.key, required this.icon, required this.isEnabled, required this.onTap});

  final IconData icon;
  final bool isEnabled;
  final VoidCallback onTap;

  @override
  State<_NavigationArrow> createState() => _NavigationArrowState();
}

class _NavigationArrowState extends State<_NavigationArrow> {
  bool _isPressed = false;

  void _setPressed(bool value) {
    if (widget.isEnabled && _isPressed != value) setState(() => _isPressed = value);
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) => _setPressed(true),
      onTapUp: (_) => _setPressed(false),
      onTapCancel: () => _setPressed(false),
      onTap: widget.isEnabled ? widget.onTap : null,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 90),
        width: 36,
        height: 32,
        decoration: BoxDecoration(
          color: _isPressed ? FinderPalette.pressed(context) : null,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Icon(
          widget.icon,
          size: widget.isEnabled ? 20 : 18,
          color: widget.isEnabled ? FinderPalette.arrowEnabled(context) : FinderPalette.arrowDisabled(context),
          shadows: widget.isEnabled ? [Shadow(color: FinderPalette.arrowEnabled(context), blurRadius: 0.4)] : null,
        ),
      ),
    );
  }
}

class _ViewPill extends StatelessWidget {
  const _ViewPill({required this.layout, required this.onChanged});

  final BrowserLayout layout;
  final ValueChanged<BrowserLayout> onChanged;

  @override
  Widget build(BuildContext context) {
    Widget segment(BrowserLayout value, IconData icon) {
      final isSelected = value == layout;
      return GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => onChanged(value),
        child: Container(
          width: 40,
          height: 30,
          decoration: BoxDecoration(
            color: isSelected ? FinderPalette.segmentSelected(context) : null,
            borderRadius: BorderRadius.circular(15),
          ),
          child: Icon(icon, size: 18, color: CupertinoColors.label.resolveFrom(context)),
        ),
      );
    }

    return Container(
      height: 36,
      padding: const EdgeInsets.all(3),
      decoration: FinderPalette.controlDecoration(context, radius: BorderRadius.circular(18)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          segment(BrowserLayout.grid, CupertinoIcons.square_grid_2x2),
          segment(BrowserLayout.list, CupertinoIcons.list_bullet),
        ],
      ),
    );
  }
}
