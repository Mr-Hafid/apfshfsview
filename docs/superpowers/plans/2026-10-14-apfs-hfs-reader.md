# APFS & HFS+ Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aplikasi Android (Flutter, UI Cupertino) untuk membaca SSD external APFS/HFS+ lewat USB OTG secara read-only dan tanpa akses internet.

**Architecture:** Parser sistem file murni Kotlin di modul JVM `android/reader-core` (bisa di-test di Mac memakai image disk dari `hdiutil`). Modul `android/app` menyambungkan libaums (USB), Media3 (streaming), SAF (salin), dan Pigeon (bridge). Flutter hanya UI Cupertino.

**Tech Stack:** Flutter 3.47.2 (`~/fvm/versions/stable/bin/flutter`), Dart 3.13, Kotlin, JUnit 5, libaums `me.jahnen.libaums:core:0.10.0`, AndroidX Media3, Pigeon, flutter_riverpod, pdfx.

**Spec:** `docs/superpowers/specs/2026-10-14-apfs-hfs-reader-design.md`

## Global Constraints

- Tidak ada komentar dalam kode apa pun (`//`, `/* */`, `#`, docstring). Nama harus self-documenting.
- Tidak menjalankan `git add`, `git commit`, `git push`. Folder ini bukan repo git. Tidak ada langkah commit.
- Nama file selalu bahasa Inggris. Teks UI bahasa Indonesia.
- Perintah Flutter: `FLUTTER=~/fvm/versions/stable/bin/flutter`, lalu `$FLUTTER ...` dan `$FLUTTER pub run ...`/`dart` dari `~/fvm/versions/stable/bin/dart`.
- Application ID dan namespace Android: `com.apfshfsview.reader`. Package core: `com.apfshfsview.core`.
- `minSdk 26`, Java/Kotlin target JVM 17 di semua modul.
- Release build: merged manifest dilarang berisi `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`. Debug build boleh memiliki `INTERNET` (wajib untuk hot reload Flutter). Data asli hanya dibuka dengan release build.
- Tidak ada SDK analytics/crash/iklan/Firebase.
- Tidak ada jalur kode yang menulis ke SSD. `BlockDevice` hanya punya `read`.
- Driver USB: libaums `ScsiBlockDevice` (READ(10)), jadi alamat sektor dibatasi `< 2^32` (SSD 512-byte sektor ≤ 2 TB). Pembacaan di atas batas melempar `DeviceRangeUnsupportedException` (kode `device_too_large`).
- UI hanya `package:flutter/cupertino.dart` + `cupertino_icons`. `AndroidView` (widgets) diperbolehkan.
- Fixture disimpan di `tools/fixtures/` sebagai `.dmg` format UDRW (raw, GPT di LBA 1, tanpa konversi).

## Penyesuaian terhadap spec

1. Fixture tidak dikonversi `UDTO`. `hdiutil create -format UDRW` sudah raw.
2. Case-folding HFS+ (`FastUnicodeCompare`) tidak diperlukan. Listing memakai pencarian `(parentID, "")` lalu iterasi leaf, dan lookup nama hanya dipakai untuk `iNode<n>` (ASCII).
3. Ada host API tambahan `thumbnail()` dan `rootNodeId()`, serta field `childCount` di `FileNode`.
4. Pengecekan izin internet hanya di build release (alasan di Global Constraints).
5. SSD > 2 TB tidak didukung karena pilihan driver libaums penuh.

## Review Focus

1. SSD dicabut saat menjelajah atau menyalin → UI kembali ke "Lokasi" dengan toast "SSD terlepas", file tujuan yang belum selesai dihapus. Diuji di Task 15 (AppShell) dan Task 14 (CopyEngine menghapus target saat gagal).
2. Nama file berisi `/` atau sudah ada di folder tujuan → disanitasi dan diberi sufiks ` (1)`. Diuji di Task 4 (`UniqueNamesTest`).
3. Preview teks dipotong 2 MB di tengah karakter UTF-8 multibyte → tetap tampil sebagai UTF-8, bukan Latin-1. Diuji di Task 15 (`text_decoding_test.dart`).
4. File 0 byte dan file sparse (lubang extent) → dibaca sebagai 0 byte / nol tanpa error. Diuji di Task 4 (`ExtentFileReaderTest`) dan Task 7/9 (fixture `zero_bytes.bin`).
5. Bagian disk di atas 2^32 sektor → error jelas `device_too_large`, bukan data salah. Diuji di Task 3 (`SectorReadPlannerTest`).

## File Map

```
tools/make_fixtures.sh
tools/fixtures/                        (hasil script, diabaikan)
android/settings.gradle.kts            (modify: include reader-core)
android/app/build.gradle.kts           (modify)
android/app/src/main/AndroidManifest.xml (replace)
android/app/src/main/res/xml/usb_device_filter.xml
android/app/src/main/res/xml/data_extraction_rules.xml
android/reader-core/build.gradle.kts
android/reader-core/src/main/kotlin/com/apfshfsview/core/
  binary/BigEndian.kt, binary/LittleEndian.kt
  block/BlockDevice.kt, block/FileBlockDevice.kt, block/CachedBlockDevice.kt,
  block/PartitionBlockDevice.kt, block/SectorReadPlanner.kt
  fs/FileNode.kt, fs/FileSystem.kt, fs/FileReader.kt, fs/ReaderExceptions.kt,
  fs/ExtentFileReader.kt, fs/ReadAheadFileReader.kt, fs/ByteArrayFileReader.kt, fs/NameNormalizer.kt
  naming/UniqueNames.kt
  partition/GptReader.kt
  fs/decmpfs/DecmpfsHeader.kt, fs/decmpfs/Decmpfs.kt, fs/decmpfs/ZlibResourceForkReader.kt
  fs/hfsplus/HfsForkData.kt, HfsPlusVolumeHeader.kt, HfsBTree.kt, HfsCatalog.kt,
             HfsExtentsOverflow.kt, HfsAttributes.kt, HfsPlusFileSystem.kt
  fs/apfs/Fletcher64.kt, NxSuperblock.kt, ApfsBTree.kt, ApfsObjectMap.kt,
          ApfsVolumeSuperblock.kt, ApfsRecords.kt, ApfsFileSystem.kt, ApfsContainer.kt
  volume/VolumeProbe.kt
android/reader-core/src/test/kotlin/com/apfshfsview/core/...
android/app/src/main/kotlin/com/apfshfsview/reader/
  MainActivity.kt
  usb/LibaumsBlockDevice.kt, usb/UsbDeviceManager.kt
  session/ReaderSessions.kt
  bridge/ReaderApi.g.kt (generated), bridge/ReaderHostApiImpl.kt, bridge/BackgroundRunner.kt,
  bridge/ErrorMapping.kt, bridge/MessageMapping.kt, bridge/DeviceOpener.kt
  preview/PreviewCache.kt, preview/ThumbnailRenderer.kt
  media/VolumeDataSource.kt, media/PlayerRegistry.kt, media/VolumePlayerViewFactory.kt
  export/DestinationStore.kt, export/DestinationPicker.kt, export/CopyJobs.kt,
  export/CopyEngine.kt, export/CopyForegroundService.kt
pigeons/reader_api.dart
lib/main.dart
lib/app/theme.dart, lib/app/reader_app.dart, lib/app/app_shell.dart
lib/bridge/reader_api.g.dart (generated), lib/bridge/reader_repository.dart
lib/shared/format_bytes.dart, format_date.dart, format_duration.dart, file_type_resolver.dart,
           file_icon.dart, hidden_files.dart, error_messages.dart, text_decoding.dart, natural_compare.dart
lib/shared/widgets/toast_banner.dart, thin_progress_bar.dart, empty_state.dart, entry_thumbnail.dart
lib/shared/thumbnail_cache.dart
lib/features/devices/devices_screen.dart
lib/features/volumes/volumes_screen.dart
lib/features/browser/browser_location.dart, browser_preferences.dart, entry_sorting.dart,
           browser_screen.dart, entry_grid_tile.dart, entry_list_tile.dart, path_bar.dart, selection_toolbar.dart
lib/features/info/file_info_screen.dart
lib/features/preview/image_preview_screen.dart, text_preview_screen.dart, pdf_preview_screen.dart
lib/features/player/video_player_screen.dart
lib/features/copy/copy_jobs.dart, copy_flow.dart, copy_screen.dart
test/support/fake_reader_repository.dart
test/shared/*.dart, test/features/*.dart
```

---

## Status Eksekusi (2026-10-14)

Dieksekusi langsung (native) mengikuti File Map dan Global Constraints di atas, tanpa daftar task berkode lengkap.

- `reader-core`: 59 test JUnit lulus (HFS+, HFSX, APFS, APFS case-sensitive, APFS terenkripsi, decmpfs, GPT, cache, planner sektor, nama unik).
- Flutter: 19 test lulus, `flutter analyze` bersih.
- `flutter build apk --release` berhasil. APK release tanpa `INTERNET`. Task `verifyReleaseNoNetworkPermission` terbukti menggagalkan build saat `INTERNET` dimasukkan.
- Belum diuji di HP dan SSD asli.
- Temuan: `ditto --hfsCompression` di macOS modern memakai LZFSE/LZVN, jadi file seperti itu tampil sebagai "kompresi tidak didukung".
