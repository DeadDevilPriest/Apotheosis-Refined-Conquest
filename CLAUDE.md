# apotheosis_refined_conquest

Forge 1.20.1 mod for the *NeepMeat – Trifecta* modpack. Extends Apotheosis with
sixteen rarity tiers, a fusion table, loot crates and a backpack-capacity affix.

- Package: `org.apotheosis_refined_conquest`
- Deploy target: `C:\Users\finba\curseforge\minecraft\Instances\NeepMeat - Trifecta\mods\`
- Build: `.\gradlew.bat build` → `build\libs\apotheosis_refined_conquest-0.1.0.jar`

**Only deploy while Minecraft is closed.** Overwriting the jar on a running game
caused a `ClassNotFoundException` crash once.

---

## Where things are

### Core

| File | What it does |
|---|---|
| `RarityTier.java` | The sixteen tiers. Central table: ordinal, id, namespace, colour, drop weight, bonus multiplier. Almost everything reads from here. |
| `ModRegistry.java` | Registers 10 materials, 80 caches, the fusion table, the creative tab. |
| `TrifectaAffixes.java` | Mod entry point; setup, GeckoLib sync registration. |
| `TrifectaConfig.java` | Common config: craft-roll weights, fusion XP cost, backpack GUI row cap. |
| `CraftRollHandler.java` | Rolls a rarity on freshly crafted gear. Crafting caps at RARE by design — higher tiers only via fusion upgrades or loot. |

### Fusion table

| File | What it does |
|---|---|
| `AffixOps.java` | Transfer / remove / upgrade affixes. Holds the stat-stacking maths and the cross-melee transfer rule. |
| `FusionTableMenu.java` | Server-side logic. Input slots are hidden; interaction runs through `panelClicked`. |
| `client/FusionTableScreen.java` | The GUI: rotating armour-stand previews, drag-and-drop stat chips, per-column scrolling. |
| `ForcedAffixes.java` | Marks affixes deliberately moved across weapon classes, so `AffixHelperMixin` can restore them. |

### Loot crates

| File | What it does |
|---|---|
| `LootCacheItem.java` | The crate item. Hold-to-open (`OPEN_TICKS = 30`), rolls the reward in `finishUsingItem`, drives the GeckoLib animations. |
| `CacheCategory.java` | Weapon / armour / tool / backpack / gem, and which `LootCategory` each accepts. |
| `client/CrateGeoModel.java` | Maps a crate item to its geo model, the shared atlas and the animation file. |
| `client/CrateItemRenderer.java` | GeckoLib renderer plus the emissive glow layer. |

### Mixins (`mixin/`)

All use return-value interception — read, never mutate foreign state.

| Mixin | Target | Why |
|---|---|---|
| `LootRarityMixin` | `LootRarity.mapCodec` | Wraps per-rarity value maps so the ten added tiers inherit *and scale* affix values. |
| `AttributeAffixMixin` | `AttributeAffix.<init>` | `AttributeAffix` precomputes a transient map that bypasses the codec — the only affix class that does. |
| `AffixHelperMixin` | `AffixHelper.getAffixesImpl` | Apotheosis re-validates every affix against the item's category on *every read*; this restores the ones the fusion table moved on purpose. |
| `BackpackWrapperMixin` | `BackpackWrapper.getNumberOfInventorySlots` | Adds capacity-affix slots at the source, so Sophisticated Backpacks itself stays consistent. |
| `StorageScreenBaseMixin` | `StorageScreenBase` | Caps how tall a backpack GUI may grow; the rest scrolls. |

Mixin configs must be listed in the jar manifest (`MixinConfigs` in `build.gradle`) —
`[[mixins]]` in `mods.toml` alone is not enough on Forge 47.

---

## Generated assets — do not hand-edit

`tools/gen_rig.py` emits **all** crate assets in one pass:

```
python tools/gen_rig.py
```

| Output | Count |
|---|---|
| `assets/…/geo/crate/<cat>_<stage>.geo.json` | 80 GeckoLib rigs |
| `assets/…/models/item/crate/<cat>_<stage>.json` | 80 vanilla fallbacks |
| `assets/…/models/item/<cat>_cache_<tier>.json` | 80 item pointers |
| `assets/…/textures/item/crate/crate_atlas.png` | 1 shared atlas (+ glowmask) |
| `assets/…/animations/crate.animation.json` | idle + open clips |

Editing the JSON directly is pointless — the next run overwrites it. Change the
generator instead. That is also what makes iteration affordable: a systematic
error is one line plus one run, not eighty manual fixes.

**Two constants must be kept in step by hand:** `LootCacheItem.OPEN_TICKS` (30)
and `OPEN_LENGTH` (1.5 s) in the generator's `build_animations`. The lid is meant
to finish opening exactly as the reward is granted.

### Verified constraints (don't re-derive these)

- GeckoLib 4.8.3 mirrors the X axis — confirmed in `BakedModelFactory$Builtin`:
  `java = Vec3(-(origin.x + size.x)/16, origin.y/16, origin.z/16)`.
  Hence `bedrock origin = [8 - x2, y1, z1 - 8]`, and north/south faces get a
  negative `uv_size` so their textures are not mirrored.
- Vanilla 1.20.1 rejects element rotations outside `-45/-22.5/0/22.5/45`, and
  requires `axis` + `angle` + `origin`. Blockbench's free three-axis rotation
  does not load.
- The pose in hand comes from the item model's `display` block, **not** from the
  GeckoLib renderer — vanilla applies it before delegating to the custom renderer.
- Body textures are composited out of Minecraft's own `client-extra.jar`. Fine
  for a private pack; would need redrawing before publishing.

---

## Status

Done: rarity system, fusion table, caches, backpack capacity, crate stages 0–9
with hold-to-open animation and emissive glow.

Open:
- Crate stages 10–15 still use the older silhouettes (wave 2: gears, glyph ring,
  ribcage, fragments, star field, mimic maw).
- Mob drops by enemy rarity — not started.
- Integration affixes for Clanging Howl, Born in Chaos, Biomancy, Forbidden &
  Arcanus, Iron's Spellbooks — not started.
- Planned: crates should offer a choice of three items instead of one random
  reward (see the `openCrate` path in `LootCacheItem`).
- `kubejs/server_scripts/RandomAffixes.js` in the pack is superseded by this mod
  and can be deleted once everything is verified.
