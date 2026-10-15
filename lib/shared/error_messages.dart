import 'package:flutter/services.dart';

String describeErrorCode(String code, {String? fallback}) => switch (code) {
  'usb_detached' => 'SSD terlepas.',
  'permission_denied' => 'Izin akses USB ditolak.',
  'corrupt_structure' => 'Data rusak, tidak bisa dibaca.',
  'unsupported_encryption' => 'Volume terenkripsi tidak didukung.',
  'unsupported_compression' => 'Kompresi file ini tidak didukung.',
  'device_too_large' => 'Bagian SSD di atas 2 TB tidak bisa dibaca driver ini.',
  'device_not_responding' => 'Perangkat tidak merespons.',
  'node_not_found' => 'File tidak ditemukan.',
  'no_destination' => 'Folder tujuan belum dipilih.',
  _ => fallback ?? 'Terjadi kesalahan saat membaca SSD.',
};

String describeReaderError(Object error) {
  if (error is PlatformException) return describeErrorCode(error.code, fallback: error.message);
  return 'Terjadi kesalahan saat membaca SSD.';
}
