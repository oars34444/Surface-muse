# Surface Muse

A client-side Fabric mod for Minecraft 26.3 (Wilderness Bound) — a building-inspiration
companion that pairs nicely with Litematica.

## What it does

Look at any wall, floor, or ceiling and press **K** (rebindable in Controls → Surface Muse).
A menu opens with design suggestions for that surface type:

- Each suggestion has a **preview grid rendered with real block sprites** — the base
  block is the one you looked at, plus like-for-like detail and accent blocks the mod picks
  to match its material family (plank, brick, deepslate/tuff, sandstone/terracotta…).
- Browse with arrow keys, scroll wheel, or clicking the list.
- Press Enter (or click a suggestion) to print the full block palette to chat so you can
  grab the blocks and build — or drop them into a Litematica schematic palette.

## Pairing with Litematica

Surface Muse is client-side only, like Litematica, so it works in singleplayer and on any
server without installation. A natural workflow:

1. Pick a design above and note its block palette.
2. In Litematica, make a small selection of your wall/floor area.
3. Use Litematica's **Replace Blocks** tool or a saved schematic to apply just the trim
   blocks, then hand-place the accents.

## Building

Requires **JDK 25** and **Gradle 9.7+** (Minecraft 26.x needs Java 25; the new Loom
needs Gradle 9.7). No Gradle wrapper is committed — generate it locally:

```bash
gradle wrapper --gradle-version 9.7.1
./gradlew build
```

The jar appears in `build/libs/surface-muse-0.1.0.jar`. Drop it in `mods/` together with
Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, and (optionally) Litematica.

## Version pins

- Minecraft `26.3`
- Fabric Loader `0.20.1`, Fabric API `0.166.2+26.3`
- Loom `1.19-SNAPSHOT` (plugin id `net.fabricmc.fabric-loom`)
- Since 26.1 the game jar is unobfuscated: no mappings dependency, no `modImplementation`.

## Adding your own designs

All designs and block-family mappings live in
`src/main/java/dev/surfacemuse/client/DesignData.java`:

- `designsFor(...)` — pattern grids using chars `p` (base), `s` (detail), `a` (accent).
- `secondaryCandidates(...)` / `accentCandidates(...)` — which like-for-like blocks pair
  with a base block.
