# Taizi

A lightweight Android launcher for RG DS devices running Android 14.

**Inspired by Daijisho and Rocknix/Jelos ROM organization**.

## Features

- Rocknix-style "one folder = one system" ROM scanning
- Automatic system detection from folder names
- BIOS status checking
- Multi-disc game support (.m3u, .cue)
- Real-time library synchronization via FileObserver
- Optional Screenscraper.fr integration for box art
- WiFi and battery status indicators
- Dual-screen support (for RG DS)
- Home launcher mode (replaces stock launcher)
- Extremely lightweight (< 8MB APK)

## Requirements

- Android 7.0 (API 24) or higher (targeting Android 14/API 35)
- RG DS or similar Android handheld (ARM64)
- ROMs organized in folders: `/storage/roms/gb/`, `/storage/roms/gba/`, etc.

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK with API 35
- NDK r26+ (for ARM64)
- Gradle 8.4+

### Build Steps

1. Open this project in Android Studio
2. Let Gradle sync and download dependencies
3. Create a debug keystore or configure release signing
4. Build APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`

Or command line:
```bash
./gradlew assembleDebug
```

### First Build Note

The project uses Hilt for dependency injection and Compose for UI. On first build, Gradle will download required components. Ensure you have Google's Maven repository configured.

## Installation

1. Enable "Unknown Sources" on your RG DS device
2. Transfer the APK to the device
3. Install and open the app
4. On first launch, you'll be prompted to select your ROM root folder (e.g., `/storage/roms/`)
5. The app will scan your library and show detected systems
6. Set Taizi as your default home/launcher app if desired

## ROM Organization

Taizi follows the Rocknix/Jelos folder structure:

```
/storage/roms/
├── gb/
│   ├── game1.gb
│   └── game2.gbc
├── gba/
│   ├── pokemon.gba
│   └── hacks/
│       └── hack.gba
├── nes/
├── snes/
├── n64/
├── psx/
├── psp/
├── nds/
├── dc/
├── mame/
├── pce/
└── bios/  ← BIOS files (optional)
    ├── scph1001.bin
    └── gb_bios.bin
```

Each immediate subdirectory of `/storage/roms/` is treated as a system. Folder names are automatically mapped to known systems (case-insensitive). Unknown folders will prompt you to map them.

## Emulator Configuration

Taizi requires emulators to be installed separately. Supported emulators include:

- **RetroArch** (`com.retroarch`) - multi-system via libretro cores
- **DuckStation** (`com.github.stenzek.duckstation`) - PlayStation
- **PPSSPP** (`org.ppsspp.ppsspp`) - PSP
- **DraStic** (`com.draustinus.drastic`) - NDS
- **Flycast** (`com.flycast`) - Dreamcast

In Settings, you can configure which emulator to use for each system.

Launching uses Android intents. For RetroArch, Taizi sends the `org.libretro.RUN_GAME` intent with core and ROM path extras.

## BIOS Files

Some systems require BIOS files to function. Place BIOS files in `/storage/roms/bios/` following the structure:

```
/storage/roms/bios/
├── psx/
│   ├── scph1001.bin
│   └── scph5502.bin
├── gb/
│   └── gb_bios.bin
├── gba/
│   └── gba_bios.bin
└── dc/
    ├── dc_boot.bin
    └── dc_flash.bin
```

Taizi will check BIOS status and display warnings for missing firmware.

## Multi-Disc Games

- **.m3u files**: Automatically treated as playlist; all listed discs are presented as a single game entry
- **.cue files**: Multiple .cue files with similar names are grouped
- Disc selector available in game details

## Real-Time Updates

With FileObserver enabled, Taizi monitors your ROM folders and automatically updates the library when you add, remove, or modify ROM files (500ms debounce).Changes are detected instantly without manual rescan.

## Screenscraper Integration

To download box art and metadata:

1. Create a free account at [screenscraper.fr](https://screenscraper.fr)
2. In Taizi Settings > Scraping, enter your username and password
3. Enable scraping
4. Use "Scrape" option in system or game menus

Note: Screenscraper has rate limits. Scraping large libraries may take time.

## Dual-Screen Support (RG DS)

Taizi detects the secondary display on RG DS. Options:

- **Mirror**: Same content on both screens
- **Extended**: List on primary (bottom), details/preview on secondary (top)
- **Disabled**: Use only primary screen

Configurable in Settings.

## Performance Optimizations

- Minimal APK size (~8MB) by removing unused resources and locales
- LRU memory cache for images (50MB max)
- Images downsampled to device resolution (640×480 max)
- Background scanning on `Dispatchers.IO`
- Lazy loading with Compose LazyColumn/LazyVerticalGrid
- No large heap request

Typical memory usage: 60-100MB idle, 150-200MB with large library.

## Limitations

- No online game database queries on launch (only on manual scrape)
- No video previews (to save storage/CPU)
- No complex widget support beyond simple launcher
- Requires manual ROM organization (no auto-sorting)
- No game state management (save states are handled by emulators)

## Troubleshooting

**ROMs not showing?**
- Ensure your ROM root is correctly set in Settings
- Reboot to force a full scan
- Check that folder names match known systems (or map them in Settings)

**Game won't launch?**
- Verify the emulator app is installed
- Some emulators (e.g., DraStic) are paid - ensure you own them
- Check that the ROM file format is supported by that emulator

**No box art?**
- Place images in `imgs/{system}/` next to your ROMs (named exactly as ROM file)
- Or use Screenscraper integration

**FileObserver not working?**
- On some Android versions, inotify has limits. If you add many files at once, they may not be detected. Perform a manual rescan.

## License

MIT License - see LICENSE file.

## Credits

- Inspired by [Daijisho](https://github.com/onmyway133/daijishou) and [Rocknix/Jelos](https://rocknix.org/)
- Uses [Compose](https://developer.android.com/jetpack/compose), [Coil](https://coil-kt.github.io/coil/), [Gson](https://github.com/google/gson)
- Icons from [Material Icons Extended](https://fonts.google.com/icons)

---

**Note**: This is a work in progress. Expect bugs and missing features.
