# APFS & HFS+ Reader — Design Spec

Tanggal: 2026-10-14
Status: Draft, menunggu review

## 1. Tujuan

Aplikasi Android pribadi (Flutter) untuk membaca SSD external berformat APFS atau HFS+ lewat USB OTG, sebagai pengganti aplikasi pihak ketiga (APFS/HFS+ Reader). Motivasi utama adalah privasi: data tidak boleh bisa dibaca atau dikirim ke pihak lain.

### Kriteria sukses
- SSD dicolok via OTG → partisi/volume terdeteksi → folder bisa dijelajah.
- Foto, teks, dan PDF bisa di-preview di dalam aplikasi.
- Video/audio bisa diputar langsung dari SSD, termasuk seek.
- File/folder bisa disalin ke penyimpanan HP.
- Aplikasi tidak memiliki izin `INTERNET` dan tidak pernah menulis ke SSD.

### Di luar cakupan (v1)
- iOS.
- APFS terenkripsi (kalau terdeteksi, tampilkan pesan "volume terenkripsi tidak didukung").
- Menulis/mengubah/menghapus apa pun di SSD.
- "Open with" ke aplikasi lain, pencarian file.
- Snapshot APFS, directory hard link HFS+ (Time Machine), resource fork.
- Kompresi decmpfs LZVN/LZFSE (v1 hanya zlib dan data inline; file lain tampil dengan label "kompresi tidak didukung").

## 2. Batasan Privasi (wajib)

1. Merged manifest tidak boleh berisi `android.permission.INTERNET` maupun `ACCESS_NETWORK_STATE`. Ada task Gradle `verifyNoNetworkPermission` yang berjalan setelah `processReleaseManifest`/`processDebugManifest` dan menggagalkan build kalau izin tersebut muncul (termasuk yang dibawa plugin).
2. Tidak ada SDK analytics, crash reporting, iklan, atau Firebase.
3. Lapisan block device hanya mengekspos operasi baca. Tidak ada jalur kode yang mengirim perintah SCSI WRITE.
4. Penyalinan ke HP memakai Storage Access Framework (folder dipilih user). Tidak meminta `MANAGE_EXTERNAL_STORAGE` atau `READ/WRITE_EXTERNAL_STORAGE`.
5. Preview memakai memori atau `cacheDir/preview/`. Folder ini dikosongkan saat volume ditutup, saat USB dicabut, dan saat aplikasi start.
6. `android:allowBackup="false"` dan `android:exported` hanya untuk activity utama.

## 3. Arsitektur

Pendekatan: inti di Kotlin, UI di Flutter, komunikasi lewat Pigeon.

```
┌──────────────── Flutter (UI) ─────────────────────┐
│ DevicesScreen → VolumesScreen → BrowserScreen     │
│ ImagePreviewScreen · TextPreviewScreen ·          │
│ PdfPreviewScreen · VideoPlayerScreen (AndroidView)│
│ CopyScreen · FileInfoScreen (Cupertino)           │
└──────────────┬────────────────────────────────────┘
               │ Pigeon (HostApi + FlutterApi)
┌──────────────┴──────── Kotlin core ───────────────┐
│ usb/        UsbDeviceManager, LibaumsBlockDevice  │
│ block/      BlockDevice, CachedBlockDevice,       │
│             PartitionBlockDevice                  │
│ partition/  GptReader                             │
│ fs/common   FileSystem, FileNode, FileReader      │
│ fs/hfsplus  HfsPlusFileSystem                     │
│ fs/apfs     ApfsContainer, ApfsVolume             │
│ fs/decmpfs  DecmpfsReader                         │
│ media/      VolumeDataSource (Media3)             │
│ export/     CopyForegroundService                 │
│ bridge/     Pigeon implementations                │
└───────────────────────────────────────────────────┘
```

### 3.1 Lapisan block

```kotlin
interface BlockDevice {
    val blockSize: Int
    val blockCount: Long
    fun read(byteOffset: Long, destination: ByteArray, destinationOffset: Int, length: Int)
}
```

- `LibaumsBlockDevice`: membungkus `BlockDeviceDriver` dari libaums (`me.jahnen.libaums:core`). Hanya memakai lapisan SCSI/block, tidak memakai FAT. Panggilan dibuat aligned ke sektor.
- `CachedBlockDevice`: LRU cache per chunk 64 KiB, maksimum 32 MiB, dibagi untuk semua pembacaan metadata dan file.
- `PartitionBlockDevice`: view dengan offset dan panjang untuk satu partisi.
- `FileBlockDevice` (khusus test): membaca dari file image di JVM.

Semua akses ke perangkat USB diserialkan lewat satu `Mutex` karena transfer bulk tidak boleh berjalan paralel.

### 3.2 Partisi

`GptReader` membaca LBA 1 (header GPT) dan entri partisi. Type GUID yang dikenali:
- APFS: `7C3457EF-0000-11AA-AA11-00306543ECAC`
- HFS+: `48465300-0000-11AA-AA11-00306543ECAC`

Partisi lain (EFI, dll.) disembunyikan. Kalau GPT tidak ada, seluruh disk dicoba sebagai HFS+ (signature `H+`/`HX` di offset 1024) lalu APFS (magic `NXSB` di blok 0).

### 3.3 Abstraksi file system

```kotlin
interface FileSystem {
    val volumeName: String
    val formatLabel: String
    fun root(): FileNode
    fun listDirectory(nodeId: Long): List<FileNode>
    fun stat(nodeId: Long): FileNode
    fun openReader(nodeId: Long): FileReader
}

data class FileNode(
    val id: Long,
    val parentId: Long,
    val name: String,
    val kind: NodeKind,
    val size: Long,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
    val isCompressed: Boolean,
    val isReadable: Boolean,
)

enum class NodeKind { FILE, DIRECTORY, SYMLINK }

interface FileReader : Closeable {
    val size: Long
    fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int
}
```

`FileReader` membaca acak dari posisi mana pun. Preview, salin, dan streaming video memakai komponen yang sama.

### 3.4 HFS+ / HFSX

- Volume header di offset 1024. Signature `H+` (versi 4) atau `HX` (versi 5).
- Catalog file: B-tree. Key = parentID + nama UTF-16. Untuk HFS+ pembandingan memakai case-folding Apple (tabel `FastUnicodeCompare`), untuk HFSX pembandingan biner.
- Extents: 8 extent pertama dari fork data di catalog record, sisanya dari extents overflow B-tree.
- File hard link (`hlnk`/`hfs+`) di-resolve ke inode di `\0\0\0\0HFS+ Private Data`.
- Folder private dan metadata (`.journal`, `.journal_info_block`, `.HFS+ Private Directory Data\r`) disembunyikan.
- Journal tidak dibaca. Kalau bit "journaled" aktif dan volume tidak di-unmount bersih, tampilkan peringatan "volume mungkin tidak konsisten" tapi tetap dibuka.
- Nama: UTF-16 NFD dari disk dinormalisasi ke NFC untuk ditampilkan.

### 3.5 APFS (tanpa enkripsi)

- Baca NX superblock di blok 0, lalu cari superblock terbaru yang valid di checkpoint descriptor area (xid tertinggi, checksum Fletcher-64 valid).
- Container object map (omap) → resolve OID volume di `nx_fs_oid[]`.
- Untuk setiap volume: baca `apfs_superblock`. Kalau flag enkripsi aktif (`APFS_FS_UNENCRYPTED` tidak diset), volume ditandai terenkripsi dan tidak bisa dibuka.
- Volume omap → root file-system B-tree.
- Record yang dipakai: `INODE`, `DIR_REC` (termasuk varian hashed name untuk volume normalization-insensitive), `FILE_EXTENT`, `DSTREAM_ID`, `XATTR`, `SIBLING_LINK`.
- Setiap object yang dibaca diverifikasi checksum Fletcher-64-nya. Kalau gagal, lempar `CorruptStructureException`.
- Root folder volume = inode 2. Folder `private-dir` (inode 3) disembunyikan.
- Snapshot diabaikan, yang ditampilkan adalah state live.
- Role volume (System, Data, Preboot, VM, Recovery) ditampilkan sebagai label di daftar volume.

### 3.6 Kompresi decmpfs

Dipakai di HFS+ dan APFS lewat xattr `com.apple.decmpfs` dan resource fork / xattr `com.apple.ResourceFork`.
- v1 mendukung type 3 (zlib inline), type 4 (zlib di resource fork), dan data tidak terkompresi inline.
- Type lain → `FileNode.isReadable = false` dan UI menampilkan label "kompresi tidak didukung".
- Reader zlib di resource fork memakai tabel chunk 64 KiB sehingga seek tetap efisien.

### 3.7 Streaming media

`VolumeDataSource` mengimplementasikan `androidx.media3.datasource.DataSource`:
- `open(dataSpec)` membuka `FileReader` berdasarkan URI internal `volume://<volumeHandle>/<nodeId>`.
- `read()` meneruskan ke `FileReader.read()`.
- Pembacaan dari USB memakai read-ahead 1 MiB supaya pemutaran lancar.

Pemutar memakai `ExoPlayer` + `PlayerView` yang ditampilkan di Flutter lewat `AndroidView` (platform view). Tidak ada server HTTP lokal.

### 3.8 Penyalinan

- User memilih folder tujuan via `ACTION_OPEN_DOCUMENT_TREE`. URI disimpan dengan `takePersistableUriPermission` supaya tidak perlu memilih ulang.
- `CopyForegroundService` (foreground service type `dataSync`) menyalin secara rekursif dengan buffer 1 MiB dan notifikasi progres. Izin yang dipakai: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`.
- Konflik nama: tambahkan sufiks ` (1)`, ` (2)`, dst.
- Batal: file yang belum selesai di tujuan dihapus.
- Timestamp modified tidak dipertahankan karena SAF tidak mendukung.

## 4. Bridge (Pigeon)

```dart
@HostApi()
abstract class ReaderHostApi {
  List<UsbDeviceInfo> listDevices();
  @async bool requestPermission(int deviceId);
  @async List<VolumeInfo> openDevice(int deviceId);
  void closeDevice(int deviceId);
  @async List<FileEntry> listDirectory(int volumeHandle, int nodeId);
  @async FileEntry stat(int volumeHandle, int nodeId);
  @async Uint8List readBytes(int volumeHandle, int nodeId, int offset, int length);
  @async String exportToPreviewCache(int volumeHandle, int nodeId);
  String? savedDestinationName();
  @async bool pickDestination();
  void playerPlay(int playerId);
  void playerPause(int playerId);
  void playerSeekTo(int playerId, int positionMillis);
  int startCopy(int volumeHandle, List<int> nodeIds);
  void cancelCopy(int copyJobId);
  int createPlayer(int volumeHandle, int nodeId);
  void disposePlayer(int playerId);
}

@FlutterApi()
abstract class ReaderEventsApi {
  void onDeviceAttached(UsbDeviceInfo device);
  void onDeviceDetached(int deviceId);
  void onCopyProgress(CopyProgress progress);
  void onPlayerState(PlayerState state);
}
```

- Operasi I/O berjalan di `Dispatchers.IO` dan tidak pernah di main thread.
- `readBytes` dibatasi 16 MiB per panggilan. Preview gambar besar dibaca bertahap lalu digabung di Dart.
- `exportToPreviewCache` hanya dipakai untuk PDF, karena `PdfRenderer` butuh file descriptor.

## 5. Desain UI (Flutter)

Gaya: widget Cupertino (gaya Apple) dari `package:flutter/cupertino.dart`, meniru aplikasi **Files** di iOS/iPadOS. Root app memakai `CupertinoApp` dengan `CupertinoThemeData`, mengikuti terang/gelap sistem, warna aksen `CupertinoColors.systemBlue`, dan warna latar `systemGroupedBackground`. Ikon memakai `CupertinoIcons`. Widget Material tidak dipakai di UI, kecuali yang wajib secara teknis (misalnya `AndroidView` untuk pemutar video).

Pemetaan pola Material ke Cupertino:

| Kebutuhan | Widget Cupertino |
|---|---|
| App bar | `CupertinoSliverNavigationBar` (large title) di dalam `CustomScrollView` |
| Daftar & pengaturan | `CupertinoListSection.insetGrouped` + `CupertinoListTile` |
| Menu urutkan / aksi | `showCupertinoModalPopup` + `CupertinoActionSheet` |
| Long-press | `CupertinoContextMenu` (preview + aksi) |
| Dialog konfirmasi | `CupertinoAlertDialog` |
| Toggle list/grid | `CupertinoSlidingSegmentedControl` |
| Switch | `CupertinoSwitch` |
| Loading | `CupertinoActivityIndicator` |
| Tombol | `CupertinoButton` / `CupertinoButton.filled` |
| Tab bawah | `CupertinoTabScaffold` + `CupertinoTabBar` |
| Snackbar / toast | Widget `ToastBanner` sendiri (overlay kapsul di bawah, auto-hide 3 detik) karena Cupertino tidak menyediakan snackbar |
| Bottom sheet | Halaman modal `CupertinoPageRoute(fullscreenDialog: true)` atau `showCupertinoModalPopup` dengan panel bulat |

### 5.0 Kerangka navigasi
`CupertinoTabScaffold` dengan dua tab:
- **Telusuri** (`CupertinoIcons.folder`): DevicesScreen → VolumesScreen → BrowserScreen, setiap tab punya `CupertinoTabView` dengan navigator sendiri.
- **Penyalinan** (`CupertinoIcons.arrow_down_doc`): riwayat dan progres salin yang sedang berjalan.

Transisi halaman memakai `CupertinoPageRoute` (slide dari kanan, swipe-back dari tepi kiri tetap aktif).

### 5.1 DevicesScreen (beranda tab Telusuri)
- Large title "Lokasi", sama seperti layar Browse di aplikasi Files.
- Saat tidak ada perangkat: empty state di tengah dengan ikon `CupertinoIcons.device_desktop` berukuran besar, judul "Colokkan SSD lewat OTG", dan subjudul abu-abu "Data hanya dibaca. Aplikasi ini tidak punya akses internet."
- Saat ada perangkat: `CupertinoListSection.insetGrouped` berjudul "Perangkat". Setiap baris `CupertinoListTile` berisi ikon drive, nama produk, subjudul `vendor · kapasitas`, dan chevron. Kalau izin USB belum diberikan, trailing berupa `CupertinoButton` kecil "Izinkan".
- Section kedua "Privasi" berisi satu baris info dengan ikon `CupertinoIcons.lock_shield` dan teks "Offline · Read-only".
- Perangkat terdeteksi otomatis lewat broadcast attach/detach, dan aplikasi bisa dibuka langsung saat SSD dicolok (intent filter `USB_DEVICE_ATTACHED`).

### 5.2 VolumesScreen
- Large title = nama perangkat.
- `CupertinoListSection.insetGrouped` per partisi/container. Setiap volume adalah satu baris: ikon `CupertinoIcons.folder_fill` biru, nama volume, subjudul `APFS · Data · 120 GB dari 500 GB`, dan progress bar tipis (custom, warna `systemBlue` di atas `systemFill`) di bawah teks.
- Volume terenkripsi: baris redup, ikon `CupertinoIcons.lock_fill`, subjudul "Terenkripsi — tidak didukung", tidak bisa diketuk.
- Volume tidak bersih: ikon `CupertinoIcons.exclamationmark_triangle_fill` oranye di trailing. Saat diketuk, muncul `CupertinoAlertDialog` peringatan sebelum dibuka.

### 5.3 BrowserScreen (inti)
- **Navigation bar**: `CupertinoSliverNavigationBar` dengan large title = nama folder, tombol back berlabel nama folder induk (bawaan Cupertino), dan trailing berisi dua tombol: "Pilih" dan ikon `CupertinoIcons.ellipsis_circle`.
- **Path bar**: di bawah large title ada baris path yang bisa di-scroll horizontal (`Volume › Folder › Subfolder`). Setiap segmen adalah `CupertinoButton` kecil untuk lompat langsung.
- **Menu ellipsis** (`CupertinoActionSheet`): Urutkan berdasarkan Nama / Tanggal / Ukuran (aktif ditandai `CupertinoIcons.checkmark`, ketuk lagi untuk membalik urutan), "Tampilkan File Tersembunyi" / "Sembunyikan File Tersembunyi", dan Batal.
- **Toggle tampilan**: `CupertinoSlidingSegmentedControl` (ikon `square_grid_2x2` / `list_bullet`) di bawah path bar.
- **Tampilan grid** (default, seperti Files): 3 kolom (portrait) / 5 kolom (landscape). Folder memakai ikon folder biru besar. Gambar memakai thumbnail sudut membulat (dibuat on-demand, di-cache di memori). Tipe lain memakai ikon dokumen dengan label ekstensi. Di bawahnya nama (2 baris, rata tengah), tanggal, dan ukuran dalam teks kecil abu-abu.
- **Tampilan list**: `CupertinoListTile` dengan ikon/thumbnail 40 pt, nama, subjudul `tanggal · ukuran` (folder: `n item`), dan chevron untuk folder. Garis pemisah inset gaya iOS.
- Folder selalu di atas. File tersembunyi (`.DS_Store`, `._*`, `.Spotlight-V100`, `.fseventsd`, `.Trashes`, dll.) disembunyikan secara default.
- **Interaksi**:
  - Tap folder → masuk. Tap file → buka preview/pemutar sesuai tipe. Tipe yang tidak bisa di-preview → buka FileInfoScreen.
  - Long-press → `CupertinoContextMenu` berisi preview item dan aksi "Salin ke HP", "Info", dan "Pilih".
  - Tombol "Pilih" → mode seleksi: lingkaran centang di setiap item, navigation bar berubah menjadi "Pilih Semua" (kiri) dan "Selesai" (kanan), dan muncul toolbar bawah dengan judul "n dipilih" serta tombol "Salin ke HP" dan "Info".
  - Swipe dari tepi kiri atau tombol back sistem → naik satu folder.
- Loading: `CupertinoActivityIndicator` di tengah. Folder kosong: teks abu-abu "Folder ini kosong".
- Folder besar (>2.000 item) dimuat sekaligus dari Kotlin lalu dirender pakai `SliverGrid`/`SliverList` builder.

### 5.4 Preview
Semua preview dibuka sebagai `CupertinoPageRoute(fullscreenDialog: true)` dengan tombol "Selesai" di kiri dan tombol aksi (`CupertinoIcons.square_arrow_down` untuk salin, `CupertinoIcons.info`) di kanan, meniru Quick Look.
- **ImagePreviewScreen**: latar putih/hitam mengikuti tema (tap untuk menyembunyikan bar dan beralih ke hitam penuh), pinch-zoom (`InteractiveViewer`), swipe kiri/kanan antar gambar di folder yang sama (`PageView`), dan judul nama file + `n dari m`. Format: JPEG, PNG, GIF, WebP, HEIC (via decoder Android). Batas 64 MiB per gambar.
- **TextPreviewScreen**: font monospace (`Menlo` fallback ke `monospace`), nomor baris abu-abu, toggle wrap di menu. Membaca maksimal 2 MiB pertama. Kalau lebih besar, tampil banner kuning tipis "Hanya 2 MB pertama ditampilkan". Deteksi encoding UTF-8 dengan fallback Latin-1.
- **PdfPreviewScreen**: render halaman pakai `PdfRenderer` Android (lewat plugin `pdfx`, dicek tidak membawa izin internet), scroll vertikal dengan bayangan halaman, dan kapsul `halaman x dari y` di bawah.
- **VideoPlayerScreen**: latar hitam, `PlayerView` Media3 via `AndroidView` dengan kontrol bawaan dimatikan, lalu kontrol dibuat sendiri dengan gaya iOS di Flutter: tombol play/pause, skip ±15 detik (`CupertinoIcons.gobackward_15` / `goforward_15`), `CupertinoSlider` untuk seek, waktu berjalan/sisa, dan tombol Selesai. Kontrol auto-hide setelah 3 detik. Perintah kontrol dikirim ke Kotlin lewat Pigeon (`play`, `pause`, `seekTo`, plus event posisi/durasi lewat `ReaderEventsApi`). Audio memakai layar yang sama dengan ikon `CupertinoIcons.music_note_2` besar sebagai artwork.

### 5.5 Salin ke HP
- Kalau belum ada folder tujuan, buka picker SAF. Kalau sudah ada, tampilkan `CupertinoAlertDialog` "Salin n item (ukuran total) ke <nama folder>?" dengan aksi "Salin", "Ganti Folder", dan "Batal".
- Setelah mulai, user tetap di folder yang sama. Muncul `ToastBanner` "Menyalin n item…" dan badge angka di tab **Penyalinan**.
- **CopyScreen** (tab Penyalinan): `CupertinoListSection.insetGrouped` "Sedang berjalan" berisi nama file saat ini, progress bar total (byte), `x dari y file`, kecepatan, dan tombol "Batal" merah (dengan konfirmasi `CupertinoAlertDialog` destructive). Section "Selesai" berisi riwayat sesi ini (jumlah file, ukuran, folder tujuan, status). Riwayat tidak disimpan permanen.
- Progres tetap berjalan di notifikasi foreground service walaupun aplikasi ditinggal.

### 5.6 FileInfoScreen
Modal `CupertinoPageRoute(fullscreenDialog: true)` berisi ikon/thumbnail besar di atas dan nama file, lalu `CupertinoListSection.insetGrouped` berisi baris label–nilai: Jenis, Ukuran (human readable + bytes), Dibuat, Diubah, Lokasi (path lengkap), Format volume, dan Kompresi.

### 5.7 Struktur kode Flutter
```
lib/
  main.dart
  app/theme.dart, app/router.dart
  shared/widgets/toast_banner.dart, shared/widgets/thin_progress_bar.dart
  bridge/reader_api.g.dart (Pigeon), bridge/reader_repository.dart
  features/devices/, features/volumes/, features/browser/,
  features/preview/, features/player/, features/copy/
  shared/file_type_resolver.dart, shared/format_bytes.dart, shared/widgets/
```
State management: `flutter_riverpod`. Navigasi: `CupertinoTabScaffold` + `CupertinoTabView` (navigator per tab) dengan `CupertinoPageRoute`, tanpa `go_router`.

## 6. Penanganan Error

| Kondisi | Perilaku |
|---|---|
| Izin USB ditolak | Kartu perangkat tetap menampilkan tombol "Izinkan akses" |
| USB dicabut saat dipakai | Semua handle ditutup, cache preview dihapus, kembali ke DevicesScreen, ToastBanner "SSD terlepas" |
| Tidak ada partisi yang dikenali | VolumesScreen menampilkan "Format tidak didukung" + daftar type GUID yang ditemukan |
| Checksum / struktur rusak | Error per folder/file: "Data rusak, tidak bisa dibaca". Folder lain tetap bisa dijelajah |
| Volume terenkripsi | Tidak bisa dibuka, pesan jelas |
| Kompresi tidak didukung | File tampil, tapi preview/salin dinonaktifkan dengan label |
| Gagal tulis ke folder tujuan | Copy berhenti, notifikasi error dengan nama file |
| Timeout transfer USB | Retry 2 kali, lalu error "Perangkat tidak merespons" |

Exception Kotlin dipetakan ke kode error Pigeon (`usb_detached`, `permission_denied`, `corrupt_structure`, `unsupported_encryption`, `unsupported_compression`, `io_error`), lalu UI menerjemahkannya ke pesan bahasa Indonesia.

## 7. Testing

### 7.1 Fixture
Script `tools/make_fixtures.sh` (dijalankan di macOS) membuat image disk:
- `hfsplus.dmg` dan `hfsx.dmg` (`hdiutil create -layout GPTSPUD -fs HFS+` / `HFSX`)
- `apfs.dmg` (`-fs APFS`) dan `apfs_case_sensitive.dmg` (`-fs "Case-sensitive APFS"`)
- `apfs_encrypted.dmg` (untuk memastikan deteksi dan penolakan)

Isinya: folder bertingkat, nama Unicode (emoji, aksen NFD), file 0 byte, file besar terfragmentasi (>8 extent untuk menguji extents overflow HFS+), hard link, symlink, file terkompresi zlib (`ditto --hfsCompression`), dan folder dengan >1.000 file. Script juga menghasilkan `manifest.json` berisi path, ukuran, dan SHA-256 setiap file sebagai acuan.

Image dikonversi ke raw (`hdiutil convert -format UDTO`) supaya bisa dibaca `FileBlockDevice`.

### 7.2 Unit test Kotlin (JVM)
- `GptReader`, Fletcher-64, B-tree HFS+ dan APFS, case-folding HFS+.
- Untuk setiap fixture: telusuri seluruh tree dan cocokkan dengan `manifest.json` (path, ukuran, SHA-256 isi file).
- `FileReader` dengan pembacaan acak (offset di batas extent dan chunk).
- Deteksi volume terenkripsi.

### 7.3 Test Flutter
- Widget test BrowserScreen (grid, list, mode Pilih), VolumesScreen, dan CopyScreen dengan fake `ReaderRepository`.
- Unit test `file_type_resolver` dan `format_bytes`.

### 7.4 Verifikasi build
- Task `verifyNoNetworkPermission` berjalan di setiap build.

### 7.5 Manual
Checklist di HP asli dengan SSD APFS dan HFS+: deteksi, jelajah, preview tiap tipe, video 4K dengan seek, salin folder besar, cabut USB saat menyalin.

## 8. Konfigurasi Proyek

- Flutter stable terbaru, Dart 3.
- Android: `minSdk 26`, `targetSdk` terbaru, Kotlin + coroutines.
- Application ID: `com.apfshfsview.reader`.
- Dependensi native: `me.jahnen.libaums:core`, `androidx.media3:media3-exoplayer`, `androidx.media3:media3-ui`.
- Dependensi Flutter: `pigeon` (dev), `flutter_riverpod`, `pdfx`. UI hanya memakai `package:flutter/cupertino.dart` dan `cupertino_icons`.
- Kode tanpa komentar, nama yang self-documenting (sesuai aturan coding user).
