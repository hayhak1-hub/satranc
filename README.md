# ThunderChess

A free, ad-free chess app for Android by Thunderbascraft. Play against the
computer (Stockfish, six difficulty levels) or with a friend on the same device.
Available in Turkish, German and English.

## Features (v0.1)

- Full chess rules: castling, en passant, promotion, check, checkmate, stalemate,
  50-move rule, threefold repetition, insufficient material
- Tap or drag to move, legal move hints, board flip
- Play vs computer (6 levels) or two players on one device
- Undo, resign, draw by agreement
- The current game is saved and can be continued later
- Share the game as PGN, copy the position as FEN
- Light and dark theme

## Project layout

| Module | Contents |
| --- | --- |
| `chess-core` | Pure Kotlin rules engine (move generation, FEN, SAN, PGN), tested with perft |
| `app` | Android app: Jetpack Compose UI, ViewModel, Stockfish process wrapper |
| `engine` | Stockfish 11 sources and the NDK build script |

Stockfish is shipped as `libstockfish.so` (one per ABI: `arm64-v8a`,
`armeabi-v7a`, `x86_64`) and run as a separate process that speaks UCI. The
binaries are prebuilt from `engine/stockfish` and committed under
`app/src/main/jniLibs`, so building the app needs no NDK. To rebuild them after
changing the engine sources (Linux or macOS, NDK 29.0.14206865):
`./gradlew :app:buildStockfish`.

## Building

Requirements: JDK 17+ and Android SDK platform 37 (Android Studio works as is).

```sh
./gradlew :chess-core:test        # rules + perft tests
./gradlew assembleDebug           # debug APK
./gradlew bundleRelease           # release App Bundle (unsigned)
```

To also run the engine tests, build a host Stockfish from `engine/stockfish`
(`make build ARCH=x86-64-modern`) and set `STOCKFISH_PATH` to it before
`./gradlew :app:testDebugUnitTest`.

## License

ThunderChess is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation,
either version 3 of the License, or (at your option) any later version. See
[LICENSE](LICENSE).

Third-party components:

- **Stockfish 11** (`engine/stockfish`): © the Stockfish authors, GPLv3. See
  `engine/stockfish/AUTHORS` and https://stockfishchess.org.
- **Chess pieces** (`app/src/main/res/drawable/piece_*.xml`): "cburnett" set by
  Colin M.L. Burnett, GPLv2 or later, converted to Android vector drawables.
