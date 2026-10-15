import 'package:flutter/cupertino.dart';

import '../../bridge/reader_api.g.dart';
import '../../shared/widgets/finder_icons.dart';
import 'finder_palette.dart';

class BrowserSidebarContext {
  const BrowserSidebarContext({
    required this.deviceName,
    required this.volumes,
    required this.onSelectVolume,
    required this.onEject,
  });

  final String deviceName;
  final List<VolumeMessage> volumes;
  final ValueChanged<VolumeMessage> onSelectVolume;
  final VoidCallback onEject;
}

class FinderSidebar extends StatelessWidget {
  const FinderSidebar({
    super.key,
    required this.sidebar,
    required this.currentVolumeHandle,
    required this.folders,
    required this.activeFolderId,
    required this.onSelectFolder,
  });

  static const width = 236.0;

  final BrowserSidebarContext sidebar;
  final int currentVolumeHandle;
  final List<FileEntryMessage> folders;
  final int? activeFolderId;
  final ValueChanged<FileEntryMessage> onSelectFolder;

  @override
  Widget build(BuildContext context) {
    final secondary = FinderPalette.secondaryText(context);
    final padding = MediaQuery.paddingOf(context);
    Widget sectionTitle(String title) => Padding(
      padding: const EdgeInsets.fromLTRB(14, 16, 14, 6),
      child: Text(
        title,
        style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: secondary),
      ),
    );
    return Padding(
      padding: EdgeInsets.fromLTRB(10, padding.top + 10, 0, padding.bottom + 10),
      child: Container(
        width: width,
        decoration: BoxDecoration(
          color: FinderPalette.sidebar(context),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: FinderPalette.controlBorder(context), width: 0.6),
          boxShadow: const [BoxShadow(color: Color(0x1F000000), blurRadius: 18, offset: Offset(0, 4))],
        ),
        child: ListView(
          padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 6),
          children: [
            sectionTitle(sidebar.deviceName),
            for (final volume in sidebar.volumes)
              _SidebarItem(
                leading: Icon(
                  volume.isEncrypted ? CupertinoIcons.lock_fill : CupertinoIcons.tray_2_fill,
                  size: 17,
                  color: secondary,
                ),
                label: volume.name,
                isActive: volume.volumeHandle == currentVolumeHandle,
                isEnabled: volume.volumeHandle != null,
                trailing: volume.volumeHandle == currentVolumeHandle
                    ? GestureDetector(
                        key: const ValueKey('sidebar-eject'),
                        behavior: HitTestBehavior.opaque,
                        onTap: sidebar.onEject,
                        child: Padding(
                          padding: const EdgeInsets.all(4),
                          child: Icon(CupertinoIcons.eject, size: 16, color: secondary),
                        ),
                      )
                    : null,
                onTap: () => sidebar.onSelectVolume(volume),
              ),
            if (folders.isNotEmpty) sectionTitle('Folder'),
            for (final folder in folders)
              _SidebarItem(
                leading: const FinderFolderIcon(size: 18),
                label: folder.name,
                isActive: folder.nodeId == activeFolderId,
                isEnabled: true,
                onTap: () => onSelectFolder(folder),
              ),
          ],
        ),
      ),
    );
  }
}

class _SidebarItem extends StatelessWidget {
  const _SidebarItem({
    required this.leading,
    required this.label,
    required this.isActive,
    required this.isEnabled,
    required this.onTap,
    this.trailing,
  });

  final Widget leading;
  final String label;
  final bool isActive;
  final bool isEnabled;
  final VoidCallback onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: isEnabled && !isActive ? onTap : null,
      child: Opacity(
        opacity: isEnabled ? 1 : 0.45,
        child: Container(
          height: 34,
          padding: const EdgeInsets.symmetric(horizontal: 8),
          decoration: BoxDecoration(
            color: isActive ? FinderPalette.selectionTile(context) : null,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            children: [
              SizedBox(width: 22, child: Center(child: leading)),
              const SizedBox(width: 8),
              Expanded(
                child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 14)),
              ),
              ?trailing,
            ],
          ),
        ),
      ),
    );
  }
}
