# Haruhikage Addon

An addon for the Ornithe Carpet project. Contains features mainly for singleplayer falling block development, mostly ported from `carpetmod112`

## Depends on
Who would have guessed, [ornithe-carpet](https://github.com/CrazyHPi/ornithe-carpet) and fabric loader >= 0.15.0

Download ornithe [here](https://ornithemc.net/download/)

## Main Features
- Player phase and Unload phase logging
- Async threads start/end logging
- Chunk population logging
- `chunkTrack` command (I couln't get chunk debug to work so this is my workaround lmao)
- `search` command to check clustering (copied `loadedChunks search`)
- `loadedChunks` command for inspecting the chunk hashmap
  - **NOTE**: Ornithe patches the fastutils version used by the game to `it.unimi.dsi:fastutil:8.5.9`,
    which prevents the chunk hashmap from downsizing below 16384.
    Change `patches/it.unimi.dsi.fastutil.json` to `7.1.0` for the vanilla version.
- `disableTerrainPopulation`, useful when designing contraptions with unpopulated chunks
- `palette` command to debug the subchunk palette (mostly copied from carpet112)
