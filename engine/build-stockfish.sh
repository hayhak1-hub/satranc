#!/usr/bin/env bash
# Builds the bundled Stockfish 11 sources (GPLv3) into Android executables.
# Output: <out>/<abi>/libstockfish.so (an executable named like a library so
# that Android extracts it into nativeLibraryDir, where it may be executed).
#
# Usage: build-stockfish.sh <ndk-dir> <out-dir> [minSdk]
set -euo pipefail

NDK="$1"
OUT="$2"
API="${3:-26}"
SRC="$(cd "$(dirname "$0")/stockfish" && pwd)"

HOST_TAG="linux-x86_64"
case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
esac
TC="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"

SRCS=(benchmark bitbase bitboard endgame evaluate main material misc movegen
      movepick pawns position psqt search thread timeman tt uci ucioption
      syzygy/tbprobe)

COMMON=(-std=c++11 -O3 -DNDEBUG -fno-exceptions -flto -fPIE -pthread -Wall)
LDFLAGS=(-pie -flto -static-libstdc++ -pthread -Wl,-z,max-page-size=16384 -s)

build() {
  local abi="$1" triple="$2"; shift 2
  local dir="$OUT/$abi"
  mkdir -p "$dir"
  local files=()
  for s in "${SRCS[@]}"; do files+=("$SRC/$s.cpp"); done
  echo "Building Stockfish for $abi"
  "$TC/${triple}${API}-clang++" "${COMMON[@]}" "$@" "${files[@]}" "${LDFLAGS[@]}" \
    -o "$dir/libstockfish.so"
}

ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64}"
for abi in $ABIS; do
  case "$abi" in
    arm64-v8a)   build arm64-v8a aarch64-linux-android -DIS_64BIT -DUSE_POPCNT ;;
    armeabi-v7a) build armeabi-v7a armv7a-linux-androideabi -mfpu=neon -mfloat-abi=softfp ;;
    x86_64)      build x86_64 x86_64-linux-android -DIS_64BIT -DUSE_POPCNT -msse4.2 -mpopcnt ;;
    *) echo "unknown ABI $abi" >&2; exit 1 ;;
  esac
done
