# Perf Boost

A Fabric mod for Minecraft 1.21.10 that adds a toggleable performance stats HUD and optional performance tweaks.

## Features

- **Stats HUD** — Overlay in the top-right showing:
  - FPS and frame time
  - RAM usage
  - Entity and chunk counts (in-world)
  - Ping (multiplayer)
  - CPU usage (when available)
  - CPU temperature (when available)
- **Performance mode** — Optional tweaks (e.g. particle limits) when enabled.
- **Keybinds** (rebindable in Options → Controls → Key Binds, under **Perf Boost**):
  - **Toggle Perf HUD** — Default: `P`
  - **Toggle Performance Mode** — Default: `O`

## Requirements

- **Minecraft:** 1.21.10
- **Fabric Loader:** ≥ 0.16.0
- **Fabric API**
- **Java:** 21+

## Installation

1. Install [Fabric](https://fabricmc.net/use/) for your Minecraft version.
2. Download the latest `perfboost-1.0.0.jar` from [Releases](../../releases) (or build from source).
3. Place the JAR in your `.minecraft/mods` folder.
4. Launch the game with the Fabric profile.

## Building from source

```bash
./gradlew build
```

The built JAR is in `build/libs/` (use the remapped JAR, e.g. `perfboost-1.0.0.jar`).

## License

MIT
