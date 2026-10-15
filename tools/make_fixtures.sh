#!/bin/bash
set -euo pipefail

FIXTURES_DIR="$(cd "$(dirname "$0")" && pwd)/fixtures"
IMAGE_SIZE="96m"
VOLUME_NAME="Fixture"
ATTACH_LOG="$FIXTURES_DIR/.attach.log"

attach_image() {
  hdiutil attach -nobrowse -noverify "$1" > "$ATTACH_LOG"
  ATTACHED_DEVICE="$(head -1 "$ATTACH_LOG" | awk '{print $1}')"
  MOUNT_POINT="$(grep -o '/Volumes/.*' "$ATTACH_LOG" | tail -1 | sed 's/[[:space:]]*$//')"
}

detach_image() {
  hdiutil detach "$ATTACHED_DEVICE" -quiet || hdiutil detach "$ATTACHED_DEVICE" -force -quiet
}

block_name() {
  printf '%s/filler/block_%05d.bin' "$1" "$2"
}

fragment_large_file() {
  local root="$1" index=0 even
  while dd if=/dev/urandom of="$(block_name "$root" "$index")" bs=65536 count=1 2>/dev/null; do
    index=$((index + 1))
  done
  rm -f "$(block_name "$root" "$index")"
  for even in $(seq 0 2 $((index - 1))); do
    rm -f "$(block_name "$root" "$even")"
  done
  sync
  dd if=/dev/urandom of="$root/fragmented.bin" bs=65536 count=$((index / 4)) 2>/dev/null
}

populate_volume() {
  local root="$1/data" index compressible="$1/compressible_source.txt"
  mkdir -p "$root/nested/level2/level3" "$root/unicode" "$root/links" "$root/many" "$root/filler" "$root/empty_folder"
  printf 'hello apfs hfs reader\n' > "$root/hello.txt"
  : > "$root/zero_bytes.bin"
  printf 'deep file\n' > "$root/nested/level2/level3/deep.txt"
  printf 'cafe\n' > "$root/unicode/café.txt"
  printf 'ramen\n' > "$root/unicode/🍜 ramen.txt"
  printf 'mixed case\n' > "$root/MixedCase.TXT"
  dd if=/dev/urandom of="$root/random_1mb.bin" bs=1024 count=1024 2>/dev/null
  ln "$root/hello.txt" "$root/links/hardlink_to_hello.txt"
  ln -s ../hello.txt "$root/links/symlink_to_hello.txt"
  for index in $(seq 1 1200); do
    printf 'item %d\n' "$index" > "$root/many/item_$(printf '%04d' "$index").txt"
  done
  for index in $(seq 1 4000); do
    printf 'line %d of a very compressible text file\n' "$index"
  done > "$compressible"
  ditto --hfsCompression "$compressible" "$root/compressed_by_ditto.txt"
  rm "$compressible"
  fragment_large_file "$root"
}

write_manifest() {
  local root="$1/data" output="$2"
  (
    cd "$root"
    find . -mindepth 1 | LC_ALL=C sort | while IFS= read -r path; do
      relative="${path#./}"
      if [ -L "$path" ]; then
        printf 'symlink\t-\t-\t0\t%s\n' "$relative"
      elif [ -d "$path" ]; then
        printf 'dir\t-\t-\t0\t%s\n' "$relative"
      else
        flags="$(stat -f '%f' "$path")"
        compressed=0
        if (( (flags & 32) != 0 )); then compressed=1; fi
        printf 'file\t%s\t%s\t%s\t%s\n' "$(stat -f '%z' "$path")" "$(shasum -a 256 "$path" | cut -d' ' -f1)" "$compressed" "$relative"
      fi
    done
  ) > "$output"
}

create_fixture() {
  local name="$1" filesystem="$2" image="$FIXTURES_DIR/$1.dmg"
  rm -f "$image"
  hdiutil create -size "$IMAGE_SIZE" -fs "$filesystem" -layout GPTSPUD -volname "$VOLUME_NAME" "$image" >/dev/null
  attach_image "$image"
  populate_volume "$MOUNT_POINT"
  write_manifest "$MOUNT_POINT" "$FIXTURES_DIR/$name.tsv"
  detach_image
  echo "created $image"
}

create_encrypted_fixture() {
  local image="$FIXTURES_DIR/apfs_encrypted.dmg" volume_device attempt
  rm -f "$image"
  hdiutil create -size 64m -fs APFS -layout GPTSPUD -volname Secret "$image" >/dev/null
  attach_image "$image"
  printf 'secret\n' > "$MOUNT_POINT/secret.txt"
  volume_device="$(grep '/Volumes/' "$ATTACH_LOG" | awk '{print $1}')"
  diskutil apfs encryptVolume "$volume_device" -user disk -passphrase "fixture-passphrase" >/dev/null
  for attempt in $(seq 1 60); do
    if diskutil apfs list | grep -A14 "$volume_device" | grep -q 'FileVault: *Yes$'; then break; fi
    sleep 1
  done
  detach_image
  echo "created $image"
}

mkdir -p "$FIXTURES_DIR"
if [ "${1:-all}" != "encrypted" ]; then
  create_fixture hfsplus "HFS+"
  create_fixture hfsx "HFSX"
  create_fixture apfs "APFS"
  create_fixture apfs_case_sensitive "Case-sensitive APFS"
fi
create_encrypted_fixture
rm -f "$ATTACH_LOG"
