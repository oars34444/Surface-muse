# Surface Muse

A client-side Fabric mod for Minecraft 26.3 (Wilderness Bound) — a building-inspiration
companion that pairs nicely with Litematica.

## What it does

Look at any wall, floor, or ceiling and press **K** (rebindable in Controls → Surface Muse).
A menu opens with design suggestions for that surface type:

- Each suggestion has a **preview grid rendered with real block sprites** — the base
  block is the one you looked at, plus like-for-like detail and accent blocks chosen by
  the ranking engine (see below). Each suggestion also shows *why* the detail block was
  picked (same block family / matching texture style / matching color band) with a fit
  percentage.
- Browse with arrow keys, scroll wheel, or clicking the list.
- Press **P** to smart-place the selected design directly onto the surface (see below),
  or Enter/click to print the block palette to chat — or drop it into a Litematic.
- Browse with arrow keys, scroll wheel, or clicking the list.

## How smart placement works

Pressing **P** with a design selected doesn't dumbly stamp blocks — `SmartPlacer`:

1. **Discovers the region** — scans from the clicked block along the surface tangent and
   down to the surface base to learn how far the wall/floor actually extends (capped 12×12),
   so a 4-block strip gets a 4-block design, not a floating stamp.
2. **Anchors the pattern** — designs are vertically anchored to the surface's bottom edge
   (wainscot bands sit at the base as drawn) and tile modulo their period, so applying the
   design on adjacent tiles stays seamless.
3. **Replaces only like-for-like cells** — a cell is painted only if its current block is
   structural material of the same family as your base block. Air, glass, doors, chests,
   trapdoors, stairs, fences and other functional blocks are never touched: windows and
   doorways simply show through the design.
4. **Writes only detail/accent cells** — cells the pattern marks as base (`p`) are left
   exactly as you built them.
5. **Checks materials first** — in survival the full requirement is counted and validated
   before anything changes; a missing material cancels the whole placement with an itemized
   shortage. In survival, blocks are consumed from your inventory; in creative it's free.
6. **Stays in sync** — singleplayer places through the integrated server's level; on
   servers, creative players can place via `setblock` commands (silently no-ops without
   permission).

## Pairing with Litematica

Surface Muse is client-side only, like Litematica, so it works in singleplayer and on any
server without installation. A natural workflow:

1. Pick a design above and smart-place it, or note its block palette.
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

## How the suggestion engine works

`SuggestionEngine` scores every block in `PalettePool` (a ~170-entry vanilla pool,
26.3-inclusive, that self-heals if ids change) against the surface you looked at:

1. **Token similarity** — ids are split into words and compared with Jaccard overlap, so
   `oak_planks` recognizes `oak_log`/`stripped_oak_log` with no hand-written rules.
2. **Perceptual color distance** — each block's representative color is converted to
   CIE-Lab and compared with a weighted deltaE (a soft plateau so exact matches don't
   drown out other signals). Detail blocks favor closeness; accents favor mid-range
   contrast so they pop instead of vanishing.
3. **Material class affinity** — ids are classified (bricks, planks, polished, glass,
   metal, light, copper...) with a hand-tuned complement table; same-class and known-good
   cross-class pairs score higher.
4. **Marginal-relevance diversification** — picks are re-ranked so each subsequent
   suggestion adds a new token or finish style; you get six meaningfully different
   palettes, not six shades of the same brick.

## Adding your own designs

- `DesignData.java` — pattern grids using chars `p` (base), `s` (detail), `a` (accent),
  organized per surface kind (wall/floor/ceiling).
- `PalettePool.java` — extend the candidate pool with more block ids.
- `SuggestionEngine.java` — tune scoring weights, material classes, or the color table.
