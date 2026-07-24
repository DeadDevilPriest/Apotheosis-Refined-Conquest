# Apotheosis Refined Conquest

Refines the Apotheosis system with new rarities, mechanics, blocks, and
integrations — affix fusion, rarity-safe upgrades, animated loot crates, and gem
crafting.

> **Status: early development.** Some textures are placeholders and will be
> replaced with original art before a public distribution.

## Features

- **Affix fusion** — move affixes between gear, including across melee weapon
  types that vanilla Apotheosis keeps apart.
- **Rarity-safe upgrades** — upgrade paths that respect each rarity's clamp.
- **Loot crates** — animated crates with a distinct model per category and
  rarity; they open in hand.
- **Gem crafting** — roll a gem at a chosen rarity.
- **Backpack capacity affix** — optional, active only when Sophisticated
  Backpacks is installed.

## Requirements

- Minecraft 1.20.1
- Forge 47+
- [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) (with Placebo)
- [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 4.x
- *(optional)* [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) — enables the backpack capacity affix

## Installation

Drop the jar into your `mods/` folder alongside the required dependencies above.

## Building from source

```bash
./gradlew build
```

The built jar lands in `build/libs/`.

## Releases

Tagging a GitHub Release (`vX.Y.Z`) builds the mod at that version and attaches
the jar automatically. See `.github/workflows/release.yml`.

## License

Licensed under the [GNU General Public License v3.0](LICENSE).

## Credits

- Author: **DeadDevilPriest**
- Built on [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) by Shadows_of_Fire.
