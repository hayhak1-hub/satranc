# Stockfish 19 binaries

`app/src/main/jniLibs/<abi>/libstockfish19.so` are Stockfish 19 executables
(named like libraries so that Android extracts them to `nativeLibraryDir`,
the only place an app may execute files from on Android 10+).

- Source: https://github.com/official-stockfish/Stockfish, tag `sf_19`
- License: GNU General Public License v3 (see the Stockfish repository; the
  corresponding source is the tag above, unmodified)
- Built by the GitHub Actions workflow
  [`.github/workflows/stockfish19.yml`](../../.github/workflows/stockfish19.yml)
  with NDK 29.0.14206865, API level 26:
  - arm64-v8a: `ARCH=armv8`
  - armeabi-v7a: `ARCH=armv7-neon`
  - x86_64: `ARCH=x86-64-sse41-popcnt`
  - `COMP=ndk EXTRACXXFLAGS=-DNNUE_EMBEDDING_OFF EXTRALDFLAGS=-Wl,-z,max-page-size=16384`, stripped

The binaries do **not** contain the NNUE network. The app downloads the network
file `nn-1a298aa575a0.nnue` (98,511,183 bytes, SHA-256
`1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2`) from the
Stockfish project when the user selects Stockfish 19, verifies it and passes it
with `setoption name EvalFile value <path>`. Only data is downloaded, never code.

To rebuild: run the workflow from the Actions tab (or `gh workflow run stockfish19.yml`),
then copy the files from the `libstockfish19` artifact into `app/src/main/jniLibs/`.
