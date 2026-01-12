# ZBLuaPerfMon

A Project Zomboid mod that provides a real-time On-Screen Display (OSD) and logging for monitoring Lua function performance.

## ☕ Support the Developer

If you find this mod useful, consider supporting the developer with a coffee!

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/zed_0xff)

## What It Does

ZBLuaPerfMon hooks into the game's Lua execution engine to track the execution time of Lua functions. It provides a real-time OSD showing the most time-consuming functions and can log detailed statistics to the console. This is an essential tool for modders to identify and optimize performance bottlenecks in their Lua scripts.

## Features

- ✅ **Real-time OSD**: View top time-consuming Lua functions directly in-game.
- ✅ **Detailed Logging**: Periodic logging of performance statistics to the console.
- ✅ **Configurable OSD**: Customize the number of functions shown, minimum time threshold, update interval, and visual appearance.
- ✅ **Smart Filtering**: Categorizes functions by source (Mod, Game, or Internal).
- ✅ **Mod Options Integration**: Easily configure all settings through the game's mod options menu.
- ✅ **Toggleable OSD**: Bind a key to quickly show/hide the performance monitor.

## Requirements

- **[ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy)** - Required framework for Java bytecode manipulation.

## Installation

1. **Prerequisites**: You must have [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) installed and configured first.
2. **Enable the mod**: Enable ZBLuaPerfMon in the Project Zomboid mod manager.

## Usage

### On-Screen Display (OSD)

By default, the OSD shows the top 10 most time-consuming Lua functions in the last 3 seconds. Each entry displays:
- **Type**: Where the function comes from (Mod name, `GAME`, or `LuaPerfMon` itself).
- **Total**: Total execution time in milliseconds within the current window.
- **Avg**: Average execution time per call in milliseconds.
- **Count**: Number of calls within the current window.
- **Function**: The source file and line number of the Lua function.

### Key Bindings

You can bind a key in the game's key bindings menu (under the "LuaPerfMon" category) to toggle the OSD visibility.

### Configuration

All settings can be adjusted via the "Mod Options" menu:
- **OSD Settings**: Position (X, Y), transparency, background transparency, number of entries, and minimum time threshold.
- **Performance Settings**: Window size, update interval, and minimum execution time to track.
- **Logging Settings**: Enable/disable console logging and set the log interval.

## How It Works

ZBLuaPerfMon uses [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) to patch `zombie.Lua.LuaCaller.protectedCall()` and other core Lua execution methods. It uses high-precision nanosecond timing to measure execution duration and categorizes functions by parsing their source paths.

## Building

1. Navigate to the Java project directory:
   ```bash
   cd 42/media/java/client
   ```

2. Build the JAR:
   ```bash
   gradle build
   ```

3. The JAR will be created at `build/libs/client.jar`.

## Links

- **GitHub Repository**: https://github.com/zed-0xff/ZBLuaPerfMon
- **ZombieBuddy Framework**: https://github.com/zed-0xff/ZombieBuddy - The framework this mod is built on

## Related Mods

Other mods built with ZombieBuddy:

- **[ZBBetterFPS](https://github.com/zed-0xff/ZBBetterFPS)**: Optimizes FPS by reducing render distance
- **[ZBetterWorkshopUpload](https://github.com/zed-0xff/ZBetterWorkshopUpload)**: Filters unwanted files from Steam Workshop uploads and provides upload previews
- **[ZBMacOSHideMenuBar](https://github.com/zed-0xff/ZBMacOSHideMenuBar)**: Fixes the macOS menu bar issue in borderless windowed mode
- **[ZBHelloWorld](https://github.com/zed-0xff/ZBHelloWorld)**: A simple example mod demonstrating patches-only mods and UI patching

## License

See [LICENSE.txt](LICENSE.txt) file for details.

## Author

zed-0xff

## Disclaimer

This mod modifies core game Lua execution behavior. While designed for performance monitoring, the overhead of tracking every Lua call may slightly impact game performance when enabled. Use at your own risk.
