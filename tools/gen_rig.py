"""
Loot-crate generator, rig-based.

Supersedes gen_crates.py / gen_geo.py: parts are now authored into NAMED BONES
instead of a flat element list, which is what the animation concept needs. One
pass emits everything:

    assets/.../geo/crate/<cat>_<stage>.geo.json      GeckoLib geometry (bones)
    assets/.../models/item/crate/<cat>_<stage>.json  vanilla fallback (flattened)
    assets/.../models/item/<cat>_cache_<tier>.json   item -> geometry pointer
    assets/.../textures/item/crate/crate_atlas.png   one shared atlas
    assets/.../textures/item/crate/crate_atlas_glowmask.png   emissive mask
    assets/.../animations/crate.animation.json       idle + open per stage

Wave 1 covers stages 0-9 on the shared base rig (base + lid + per-stage extras).
Stages 10-15 keep their earlier silhouettes for now and are rebuilt in wave 2.

Coordinates: authored in vanilla space (0..16, crate centred on 8). GeckoLib is
fed the mirrored form verified against BakedModelFactory$Builtin:

    java = Vec3(-(origin.x + size.x)/16, origin.y/16, origin.z/16)
    => bedrock origin = [8 - x2, y1, z1 - 8]   pivot = [8 - px, py, pz - 8]
"""
import json
import os
import glob
import zipfile
from PIL import Image, ImageDraw

# Paths are derived from this file's location so the script travels with the repo.
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.join(REPO, "src", "main", "resources", "assets", "apotheosis_refined_conquest")
MODELS = os.path.join(ROOT, "models", "item")
CRATE_MODELS = os.path.join(MODELS, "crate")
GEO_DIR = os.path.join(ROOT, "geo", "crate")
ANIM_DIR = os.path.join(ROOT, "animations")
TEX = os.path.join(ROOT, "textures", "item", "crate")

# Body textures are composited from Minecraft's own blocks. ForgeGradle keeps the
# extracted assets here; override with TRIFECTA_CLIENT_JAR if your cache differs.
CLIENT_JAR = os.environ.get("TRIFECTA_CLIENT_JAR") or os.path.join(
    os.path.expanduser("~"), ".gradle", "caches", "forge_gradle",
    "minecraft_repo", "versions", "1.20.1", "client-extra.jar")

CATEGORIES = ["weapon", "armor", "tool", "backpack", "gem"]
TIERS = [
    "worn", "common", "uncommon", "improved",
    "rare", "exquisite", "epic", "heroic",
    "legendary", "mythic", "ancient", "primeval",
    "relic", "transcendent", "cosmic", "chaos",
]
LEGAL_ANGLES = (-45, -22.5, 0, 22.5, 45)

BODY_TOP = 9
LID_HINGE_Z = 16

ATLAS_SIZE = 128
TILE = 16
PER_ROW = ATLAS_SIZE // TILE


# ---------------------------------------------------------------- rig scaffold

class Rig:
    """Bones in authoring (vanilla) space; converted on emit."""

    def __init__(self):
        self.order = []
        self.bones = {}
        self.bone("root", [8, 0, 8], None)
        self.bone("base", [8, 0, 8], "root")
        self.bone("lid", [8, BODY_TOP, LID_HINGE_Z], "root")

    def bone(self, name, pivot, parent="base"):
        if name not in self.bones:
            self.bones[name] = {"parent": parent, "pivot": pivot, "els": []}
            self.order.append(name)
        return name

    def add(self, bone, elements):
        self.bones[bone]["els"].extend(elements if isinstance(elements, list) else [elements])

    def all_elements(self):
        out = []
        for n in self.order:
            out.extend(self.bones[n]["els"])
        return out


def part(frm, to, tex, rot=None):
    """One box. UVs come from world coordinates so texel density stays constant."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    faces = {
        "north": [x1, 16 - y2, x2, 16 - y1],
        "south": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "east":  [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        "west":  [z1, 16 - y2, z2, 16 - y1],
        "up":    [x1, z1, x2, z2],
        "down":  [x1, 16 - z2, x2, 16 - z1],
    }
    clamp = lambda v: max(0.0, min(16.0, round(v, 4)))
    el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to],
          "faces": {d: {"uv": [clamp(u) for u in uv], "texture": tex}
                    for d, uv in faces.items()}}
    if rot:
        assert rot["angle"] in LEGAL_ANGLES, f"illegal angle {rot['angle']}"
        el["rotation"] = rot
    return el


# ------------------------------------------------------------- per-stage rigs
# Each stage names its own feature set rather than inheriting blindly: the spec
# describes distinct designs, not one crate with things bolted on forever.

FEATURES = {
    0:  {"moss", "sprung", "feet_none"},
    1:  {"latch_plain"},
    2:  {"latch", "studs"},
    3:  {"latch", "straps", "feet"},
    4:  {"latch", "ribs", "feet"},
    5:  {"latch", "ornament", "feet"},
    6:  {"latch", "cross", "feet"},
    7:  {"latch", "cross", "runes", "inner_glow", "feet"},
    8:  {"skull", "chain", "spikes", "feet"},
    9:  {"skull", "chain", "spikes", "horns", "feet"},
}


def build_walls(rig, sprung):
    """Hollow plank box, upper/lower bands with a seam — from the Blockbench reference."""
    for (y1, y2) in [(0, 4), (5, BODY_TOP)]:
        rig.add("base", part([0, y1, 0], [16, y2, 1], "#body"))
        rig.add("base", part([0, y1, 15], [16, y2, 16], "#body"))
        rig.add("base", part([0, y1, 1], [1, y2, 15], "#body"))
        if not (sprung and y1 == 5):
            rig.add("base", part([15, y1, 1], [16, y2, 15], "#body"))
    rig.add("base", part([1, 0, 1], [15, 1, 8], "#body"))
    if not sprung:
        rig.add("base", part([1, 0, 8], [15, 1, 15], "#body"))

    posts = [(1, 1), (1, 14), (14, 14), (14, 1)]
    if sprung:
        posts = posts[:3]
    for x, z in posts:
        rig.add("base", part([x, 1, z], [x + 1, BODY_TOP, z + 1], "#trim"))


def build_lid(rig, feats):
    """Rounded lid: a slab plus two stepped caps, so it reads domed rather than flat."""
    rig.add("lid", part([0, BODY_TOP, 0], [16, BODY_TOP + 2, 16], "#body"))
    rig.add("lid", part([1, BODY_TOP + 2, 1], [15, BODY_TOP + 3.5, 15], "#body"))
    rig.add("lid", part([3, BODY_TOP + 3.5, 3], [13, BODY_TOP + 4.5, 13], "#body"))

    if "straps" in feats:
        for name, x in (("strap_l", 3), ("strap_r", 11)):
            rig.bone(name, [x + 1, BODY_TOP, 8], "lid")
            rig.add(name, part([x, BODY_TOP + 1.8, 0], [x + 2, BODY_TOP + 4.8, 16], "#trim"))
    if "ribs" in feats:
        for i, x in enumerate((2, 7, 12)):
            name = rig.bone(f"rib{i + 1}", [x + 1, BODY_TOP, 8], "lid")
            rig.add(name, part([x, BODY_TOP + 1.8, -0.3], [x + 2.5, BODY_TOP + 4.8, 16.3], "#trim"))
    if "ornament" in feats:
        rig.bone("ornament", [8, BODY_TOP + 4, 8], "lid")
        rig.add("ornament", part([2, BODY_TOP + 4.4, 6], [14, BODY_TOP + 5.2, 10], "#trim"))
        rig.add("ornament", part([6, BODY_TOP + 4.4, 2], [10, BODY_TOP + 5.2, 14], "#trim"))


def build_front(rig, feats):
    """Everything that lives on the front face: locks, crosses, skulls."""
    if "latch_plain" in feats:
        rig.add("base", part([7, 4.5, -0.4], [9, 7, 0.5], "#trim"))
    if "latch" in feats:
        rig.bone("latch", [8, 7, 0], "base")
        rig.add("latch", part([6.5, 4.5, -0.5], [9.5, 7.5, 0.5], "#trim"))
    if "studs" in feats:
        for x in (2, 6, 10, 13.5):
            for y in (2, 6.5):
                rig.add("base", part([x, y, -0.3], [x + 1, y + 1, 0.2], "#trim"))
    if "cross" in feats:
        rig.bone("cross", [8, 5, 0], "base")
        rig.add("cross", part([6.5, 1.5, -0.5], [9.5, 8.5, 0.3], "#glow"))
        rig.add("cross", part([3.5, 4, -0.5], [12.5, 6.5, 0.3], "#glow"))
    if "runes" in feats:
        for i, (x, y) in enumerate(((2.5, 2), (12, 2), (2.5, 6.5), (12, 6.5))):
            name = rig.bone(f"rune{i + 1}", [x + 0.75, y + 0.75, 0], "base")
            rig.add(name, part([x, y, -0.35], [x + 1.5, y + 1.5, 0.2], "#glow"))
    if "inner_glow" in feats:
        rig.bone("inner_glow", [8, 4.5, 8], "base")
        rig.add("inner_glow", part([1.2, 1.2, 1.2], [14.8, BODY_TOP - 0.2, 14.8], "#glow"))
    if "skull" in feats:
        rig.bone("skull", [8, 5, 0], "base")
        rig.add("skull", part([5, 2, -1.4], [11, 8, 0.4], "#skull"))
        rig.add("skull", part([6, 0.4, -1.1], [10, 2, 0.2], "#skull"))
        rig.add("skull", part([5.8, 4.4, -1.7], [7.4, 6, -1.2], "#glow"))
        rig.add("skull", part([8.6, 4.4, -1.7], [10.2, 6, -1.2], "#glow"))
    if "chain" in feats:
        rig.bone("chain", [8, 7.5, 0], "base")
        rig.add("chain", part([-0.4, 7.2, -0.4], [16.4, 8.4, 16.4], "#trim"))


def build_extras(rig, feats):
    if "feet" in feats:
        for x in (0.5, 13.5):
            for z in (0.5, 13.5):
                rig.add("base", part([x, -1.5, z], [x + 2, 0.2, z + 2], "#trim"))
    if "spikes" in feats:
        for i, (x, z) in enumerate(((0, 0), (14, 0), (14, 14), (0, 14))):
            name = rig.bone(f"spike{i + 1}", [x + 1, BODY_TOP, z + 1], "base")
            rig.add(name, part([x, BODY_TOP - 1, z], [x + 2, BODY_TOP + 3.5, z + 2], "#trim"))
    if "horns" in feats:
        for name, x in (("horn_l", 1), ("horn_r", 12)):
            rig.bone(name, [x + 1.5, BODY_TOP + 3, 8], "base")
            rig.add(name, part([x, BODY_TOP + 3, 6.5], [x + 3, BODY_TOP + 6, 9.5], "#trim"))
            rig.add(name, part([x + 0.5, BODY_TOP + 6, 7], [x + 2.5, BODY_TOP + 8.5, 9], "#trim"))


def category_accent(rig, category, stage):
    """Keeps the five categories apart without breaking the shared family."""
    if category == "weapon":
        rig.add("lid", part([7, BODY_TOP + 4.5, 2], [9, BODY_TOP + 5.8, 14], "#trim"))
    elif category == "tool":
        h = BODY_TOP + 4.5
        rig.bone("handle", [8, h, 8], "lid")
        rig.add("handle", part([4, h, 7], [6, h + 3, 9], "#trim"))
        rig.add("handle", part([10, h, 7], [12, h + 3, 9], "#trim"))
        rig.add("handle", part([4, h + 3, 7.5], [12, h + 4.2, 8.5], "#trim"))
    elif category == "backpack":
        rig.add("base", part([3, 0.9, -0.35], [5, BODY_TOP, 0.3], "#trim"))
        rig.add("base", part([11, 0.9, -0.35], [13, BODY_TOP, 0.3], "#trim"))
    elif category == "gem":
        rig.add("lid", part([6, BODY_TOP + 4.5, 6], [10, BODY_TOP + 6.5, 10], "#gemstone"))


def build_rig(category, stage):
    feats = FEATURES.get(stage, {"latch", "feet"})
    rig = Rig()
    build_walls(rig, "sprung" in feats)
    build_lid(rig, feats)
    build_front(rig, feats)
    build_extras(rig, feats)
    category_accent(rig, category, stage)

    # Symbol laid flat on the lid, as in the hand-made reference.
    rig.add("lid", {"from": [5, BODY_TOP + 4.51, 5], "to": [11, BODY_TOP + 4.51, 11],
                    "faces": {"up": {"uv": [1, 2, 15, 15], "texture": "#symbol"}}})
    return rig


# ------------------------------------------------------------------- materials

BODY_MATERIAL = ["mossy_oak", "oak", "oak_light", "polished", "driftwood",
                 "noble", "sacral", "heroic_wood", "gold_wood", "molten"]
TRIM_MATERIAL = ["iron_rusty", "iron", "iron_zinc", "bronze_teal", "iron_blue",
                 "silver_violet", "iron_purple", "iron_magenta", "gold", "gold_hot"]
GLOW_MATERIAL = ["none", "none", "glow_green", "glow_teal", "glow_blue",
                 "glow_violet", "glow_purple", "glow_magenta", "glow_gold", "glow_orange"]


def mat_body(stage):
    return BODY_MATERIAL[stage] if stage < 10 else "planks_chaos"


def mat_trim(stage):
    return TRIM_MATERIAL[stage] if stage < 10 else "gold"


def mat_glow(stage):
    return GLOW_MATERIAL[stage] if stage < 10 else "glow_purple"


# ------------------------------------------------------------------- textures

_cache = {}


def vanilla(name):
    if name not in _cache:
        with zipfile.ZipFile(CLIENT_JAR) as z:
            with z.open(f"assets/minecraft/textures/block/{name}.png") as fh:
                img = Image.open(fh).convert("RGBA")
                img.load()
                _cache[name] = img.crop((0, 0, 16, 16))
    return _cache[name].copy()


def shade(img, factor=1.0, tint=None):
    out = img.copy()
    px = out.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            r, g, b = r * factor, g * factor, b * factor
            if tint:
                tr, tg, tb, w = tint
                r, g, b = r * (1 - w) + tr * w, g * (1 - w) + tg * w, b * (1 - w) + tb * w
            px[x, y] = (min(int(r), 255), min(int(g), 255), min(int(b), 255), a)
    return out


def overgrow(base, moss, coverage):
    """Moss with a ragged fringe, heaviest along the top — the Forest chest."""
    out = base.copy()
    bp, mp, op = base.load(), moss.load(), out.load()
    fringe = [0, 1, 0, 2, 1, 3, 1, 0, 2, 1, 0, 1, 2, 0, 1, 1]
    for x in range(16):
        depth = coverage + fringe[x]
        for y in range(16):
            if y < depth:
                op[x, y] = mp[x, y]
            elif y < depth + 2 and (x + y) % 3 == 0:
                mr, mg, mb, _ = mp[x, y]
                br, bg, bb, ba = bp[x, y]
                op[x, y] = ((mr + br) // 2, (mg + bg) // 2, (mb + bb) // 2, ba)
    return out


def flat(rgb):
    img = Image.new("RGBA", (16, 16), rgb + (255,))
    d = ImageDraw.Draw(img)
    for y in range(0, 16, 3):
        d.line([(0, y), (15, y)],
               fill=(min(rgb[0] + 24, 255), min(rgb[1] + 24, 255), min(rgb[2] + 24, 255), 255))
    d.rectangle([0, 0, 15, 15],
                outline=(int(rgb[0] * .7), int(rgb[1] * .7), int(rgb[2] * .7), 255))
    return img


def tex_skull():
    img = shade(vanilla("bone_block_side"), 1.0, (255, 246, 214, 0.3))
    return img


def symbol(kind):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == "weapon":
        d.line([(4, 12), (11, 4)], fill=(235, 235, 245, 255), width=2)
        d.line([(3, 8), (8, 13)], fill=(90, 70, 45, 255), width=2)
    elif kind == "armor":
        d.polygon([(4, 3), (11, 3), (13, 6), (11, 13), (4, 13), (2, 6)],
                  fill=(200, 205, 215, 255), outline=(120, 125, 135, 255))
    elif kind == "tool":
        d.arc([2, 2, 13, 11], start=200, end=340, fill=(190, 195, 205, 255), width=2)
        d.line([(8, 5), (8, 14)], fill=(120, 95, 60, 255), width=2)
    elif kind == "backpack":
        d.rounded_rectangle([3, 5, 12, 14], radius=2,
                            fill=(150, 115, 80, 255), outline=(95, 70, 45, 255))
        d.arc([5, 1, 10, 8], start=180, end=360, fill=(95, 70, 45, 255), width=2)
    else:
        d.polygon([(8, 2), (13, 7), (8, 14), (3, 7)],
                  fill=(150, 235, 255, 255), outline=(70, 150, 190, 255))
    return img


# name -> (builder, emissive?)
TEXTURES = {
    # bodies, stages 0-9, following the spec's colour notes
    "mossy_oak":   (lambda: overgrow(shade(vanilla("oak_planks"), .72), vanilla("moss_block"), 4), False),
    "oak":         (lambda: vanilla("oak_planks"), False),
    "oak_light":   (lambda: shade(vanilla("oak_planks"), 1.12), False),
    "polished":    (lambda: shade(vanilla("stripped_oak_log"), 1.05), False),
    "driftwood":   (lambda: shade(vanilla("oak_planks"), 1.15, (214, 222, 210, .38)), False),
    "noble":       (lambda: vanilla("dark_oak_planks"), False),
    "sacral":      (lambda: shade(vanilla("dark_oak_planks"), .92, (70, 40, 90, .18)), False),
    "heroic_wood": (lambda: shade(vanilla("dark_oak_planks"), .95, (120, 40, 100, .2)), False),
    "gold_wood":   (lambda: shade(vanilla("dark_oak_planks"), .9, (90, 60, 20, .25)), False),
    "molten":      (lambda: shade(vanilla("dark_oak_planks"), .95, (140, 60, 15, .3)), False),
    "planks_chaos": (lambda: shade(vanilla("crimson_planks"), .9, (120, 40, 160, .35)), False),
    # trims
    "iron_rusty":    (lambda: shade(vanilla("iron_block"), .62, (110, 70, 40, .35)), False),
    "iron":          (lambda: vanilla("iron_block"), False),
    "iron_zinc":     (lambda: shade(vanilla("iron_block"), 1.0, (170, 210, 170, .18)), False),
    "bronze_teal":   (lambda: shade(vanilla("copper_block"), .95, (40, 200, 170, .35)), False),
    "iron_blue":     (lambda: shade(vanilla("iron_block"), .95, (60, 120, 200, .3)), False),
    "silver_violet": (lambda: shade(vanilla("iron_block"), 1.0, (150, 130, 210, .3)), False),
    "iron_purple":   (lambda: shade(vanilla("iron_block"), .9, (140, 70, 200, .3)), False),
    "iron_magenta":  (lambda: shade(vanilla("iron_block"), .95, (210, 80, 170, .32)), False),
    "gold":          (lambda: vanilla("gold_block"), False),
    "gold_hot":      (lambda: shade(vanilla("gold_block"), 1.0, (255, 130, 20, .3)), False),
    # emissive
    "none":         (lambda: Image.new("RGBA", (16, 16), (0, 0, 0, 0)), False),
    "glow_green":   (lambda: flat((30, 255, 0)), True),
    "glow_teal":    (lambda: flat((0, 229, 176)), True),
    "glow_blue":    (lambda: flat((0, 112, 221)), True),
    "glow_violet":  (lambda: flat((94, 123, 255)), True),
    "glow_purple":  (lambda: flat((163, 53, 238)), True),
    "glow_magenta": (lambda: flat((217, 70, 176)), True),
    "glow_gold":    (lambda: flat((255, 198, 26)), True),
    "glow_orange":  (lambda: flat((255, 128, 0)), True),
    # props
    "skull":     (tex_skull, False),
    "gemstone":  (lambda: vanilla("amethyst_block"), False),
    "symbol_weapon":   (lambda: symbol("weapon"), False),
    "symbol_armor":    (lambda: symbol("armor"), False),
    "symbol_tool":     (lambda: symbol("tool"), False),
    "symbol_backpack": (lambda: symbol("backpack"), False),
    "symbol_gem":      (lambda: symbol("gem"), False),
}

TILES = {name: i for i, name in enumerate(TEXTURES)}
assert len(TILES) <= PER_ROW * PER_ROW, f"atlas too small for {len(TILES)} tiles"


def tile_offset(name):
    i = TILES[name]
    return (i % PER_ROW) * TILE, (i // PER_ROW) * TILE


def build_atlas():
    """
    One atlas plus a glowmask. AutoGlowingGeoLayer reads the mask to decide which
    pixels ignore scene lighting, so the emissive parts are declared here rather
    than in the model.
    """
    atlas = Image.new("RGBA", (ATLAS_SIZE, ATLAS_SIZE), (0, 0, 0, 0))
    mask = Image.new("RGBA", (ATLAS_SIZE, ATLAS_SIZE), (0, 0, 0, 0))
    for name, (fn, emissive) in TEXTURES.items():
        ox, oy = tile_offset(name)
        img = fn()
        atlas.paste(img, (ox, oy))
        if emissive:
            mask.paste(img, (ox, oy))
    os.makedirs(TEX, exist_ok=True)
    atlas.save(os.path.join(TEX, "crate_atlas.png"))
    mask.save(os.path.join(TEX, "crate_atlas_glowmask.png"))
    return len(TEXTURES), sum(1 for _, e in TEXTURES.values() if e)


def resolve(tex_ref, category, stage):
    key = tex_ref.lstrip("#")
    return {"body": mat_body(stage), "trim": mat_trim(stage), "glow": mat_glow(stage),
            "symbol": f"symbol_{category}"}.get(key, key)


# --------------------------------------------------------------------- emitters

def to_cube(el, category, stage):
    x1, y1, z1 = el["from"]
    x2, y2, z2 = el["to"]
    uv = {}
    for face, data in el["faces"].items():
        ox, oy = tile_offset(resolve(data["texture"], category, stage))
        u0, v0, u1, v1 = data["uv"]
        w, h = u1 - u0, v1 - v0
        # X is mirrored by the renderer, so flip the two faces it affects.
        if face in ("north", "south"):
            uv[face] = {"uv": [ox + u1, oy + v0], "uv_size": [-w, h]}
        else:
            uv[face] = {"uv": [ox + u0, oy + v0], "uv_size": [w, h]}
    return {"origin": [round(8 - x2, 4), round(y1, 4), round(z1 - 8, 4)],
            "size": [round(x2 - x1, 4), round(y2 - y1, 4), round(z2 - z1, 4)],
            "uv": uv}


def emit_geo(rig, category, stage):
    bones = []
    for name in rig.order:
        b = rig.bones[name]
        px, py, pz = b["pivot"]
        entry = {"name": name, "pivot": [round(8 - px, 4), round(py, 4), round(pz - 8, 4)]}
        if b["parent"]:
            entry["parent"] = b["parent"]
        if b["els"]:
            entry["cubes"] = [to_cube(e, category, stage) for e in b["els"]]
        bones.append(entry)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.crate_{category}_{stage}",
                        "texture_width": ATLAS_SIZE, "texture_height": ATLAS_SIZE,
                        "visible_bounds_width": 3, "visible_bounds_height": 3.5,
                        "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}


# How the crate is held. GeckoLib does not set these itself: vanilla applies the
# item model's display transform BEFORE handing over to the custom renderer, so
# this is what actually governs the pose in hand and in the GUI.
#
# The crate's front — lock, cross, skull — is the -Z (north) face. block/block
# rotates 45 degrees on Y in first person, which turns that front AWAY from the
# camera. 180 brings it back round to face the player.
DISPLAY = {
    "gui":                   {"rotation": [30, 225, 0], "translation": [0, 0, 0],   "scale": [0.62, 0.62, 0.62]},
    "firstperson_righthand": {"rotation": [0, 180, 0],  "translation": [0, 3.5, 0], "scale": [0.34, 0.34, 0.34]},
    "firstperson_lefthand":  {"rotation": [0, 180, 0],  "translation": [0, 3.5, 0], "scale": [0.34, 0.34, 0.34]},
    "thirdperson_righthand": {"rotation": [15, 180, 0], "translation": [0, 2.5, 0], "scale": [0.32, 0.32, 0.32]},
    "thirdperson_lefthand":  {"rotation": [15, 180, 0], "translation": [0, 2.5, 0], "scale": [0.32, 0.32, 0.32]},
    "ground":                {"rotation": [0, 180, 0],  "translation": [0, 3, 0],   "scale": [0.30, 0.30, 0.30]},
    "fixed":                 {"rotation": [0, 180, 0],  "translation": [0, 0, 0],   "scale": [0.50, 0.50, 0.50]},
    "head":                  {"rotation": [0, 180, 0],  "translation": [0, 14, 0],  "scale": [1.0, 1.0, 1.0]},
}


def emit_vanilla(rig, category, stage):
    """Static fallback. Bones collapse away; the lid is drawn closed."""
    used, els = {}, []
    for el in rig.all_elements():
        e = json.loads(json.dumps(el))
        for f in e["faces"].values():
            mat = resolve(f["texture"], category, stage)
            used[mat] = f"apotheosis_refined_conquest:item/crate/{mat}"
            f["texture"] = "#" + mat
        els.append(e)
    used["particle"] = f"apotheosis_refined_conquest:item/crate/{mat_body(stage)}"
    return {"parent": "minecraft:block/block", "textures": used,
            "display": json.loads(json.dumps(DISPLAY)), "elements": els}


# ------------------------------------------------------------------ animations

def build_animations():
    """
    Idle loops run on their own controller; 'open' is fired once by triggerAnim.
    Bones a given stage does not have are simply skipped by GeckoLib, so one file
    serves all stages.

    OPEN_LENGTH matches LootCacheItem.OPEN_TICKS exactly: the lid finishes its
    travel on the same tick the contents are handed over, so the reward reads as
    the result of the crate opening rather than something that happened alongside.
    """
    OPEN_LENGTH = 1.5          # seconds; keep in step with OPEN_TICKS = 30

    def key(t, vec, easing=None):
        return (str(t), {"vector": vec, "easing": easing} if easing else vec)

    def frames(*pairs):
        return dict(pairs)

    # The latch always releases before the lid begins to move.
    latch = frames(key(0.0, [0, 0, 0]), key(0.2, [-55, 0, 0], "easeoutquad"),
                   key(OPEN_LENGTH, [-55, 0, 0]))

    anims = {
        "animation.crate.idle": {
            "loop": True, "animation_length": 4.0,
            "bones": {
                "cross":  {"scale": {"0.0": [1, 1, 1], "2.0": [1.05, 1.05, 1.05], "4.0": [1, 1, 1]}},
                "rune1":  {"scale": {"0.0": [1, 1, 1], "1.0": [1.25, 1.25, 1.25], "2.0": [1, 1, 1]}},
                "rune2":  {"scale": {"1.0": [1, 1, 1], "2.0": [1.25, 1.25, 1.25], "3.0": [1, 1, 1]}},
                "rune3":  {"scale": {"2.0": [1, 1, 1], "3.0": [1.25, 1.25, 1.25], "4.0": [1, 1, 1]}},
                "rune4":  {"scale": {"0.0": [1.25, 1.25, 1.25], "1.0": [1, 1, 1], "3.0": [1.25, 1.25, 1.25]}},
                "skull":  {"scale": {"0.0": [1, 1, 1], "1.5": [1.04, 1.04, 1.04], "3.0": [1, 1, 1]}},
                "chain":  {"rotation": {"0.0": [0, 0, 0], "2.0": [0, 1.5, 0], "4.0": [0, 0, 0]}},
                "inner_glow": {"scale": {"0.0": [1, 1, 1], "2.0": [1.02, 1.02, 1.02], "4.0": [1, 1, 1]}},
            },
        },
        "animation.crate.idle_hot": {
            "loop": True, "animation_length": 2.0,
            "bones": {
                "skull":  {"scale": {"0.0": [1, 1, 1], "0.7": [1.06, 1.06, 1.06], "1.4": [1, 1, 1]}},
                "spike1": {"rotation": {"0.0": [0, 0, 0], "0.5": [0.8, 0, 0], "1.0": [0, 0, 0]}},
                "spike2": {"rotation": {"0.25": [0, 0, 0], "0.75": [0, 0, 0.8], "1.25": [0, 0, 0]}},
                "spike3": {"rotation": {"0.5": [0, 0, 0], "1.0": [0.8, 0, 0], "1.5": [0, 0, 0]}},
                "spike4": {"rotation": {"0.75": [0, 0, 0], "1.25": [0, 0, 0.8], "1.75": [0, 0, 0]}},
                "horn_l": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 0, 1.2], "2.0": [0, 0, 0]}},
                "horn_r": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 0, -1.2], "2.0": [0, 0, 0]}},
            },
        },
        # The LID is NOT animated here — CrateItemRenderer drives it directly from
        # the hold progress (renderRecursively), which is the single source of
        # truth for its angle. Leaving a lid track in the triggered animation too
        # would fight that hook. These clips carry only the ornaments.
        "animation.crate.open": {
            "loop": False, "animation_length": OPEN_LENGTH,
            "bones": {"latch": {"rotation": latch}},
        },
        "animation.crate.open_worn": {
            "loop": False, "animation_length": OPEN_LENGTH,
            "bones": {},
        },
        "animation.crate.open_rich": {
            # Ornaments react along the way rather than all at the moment of release.
            "loop": False, "animation_length": OPEN_LENGTH,
            "bones": {
                "latch": {"rotation": latch},
                "strap_l": {"rotation": {"0.3": [0, 0, 0], "1.0": [0, 0, -6], "1.5": [0, 0, -4]}},
                "strap_r": {"rotation": {"0.3": [0, 0, 0], "1.0": [0, 0, 6], "1.5": [0, 0, 4]}},
                "rib1": {"rotation": {"0.35": [0, 0, 0], "1.1": [0, 0, -5]}},
                "rib2": {"rotation": {"0.45": [0, 0, 0], "1.2": [0, 0, 0]}},
                "rib3": {"rotation": {"0.55": [0, 0, 0], "1.3": [0, 0, 5]}},
                "ornament": {"rotation": {"0.3": [0, 0, 0], "1.4": [0, 25, 0]}},
                "cross": {"scale": {"0.0": [1, 1, 1], "0.9": [1.35, 1.35, 1.35], "1.5": [1.1, 1.1, 1.1]}},
                "skull": {"scale": {"0.0": [1, 1, 1], "0.25": [1.2, 1.2, 1.2], "0.7": [1, 1, 1],
                                     "1.2": [1.15, 1.15, 1.15], "1.5": [1, 1, 1]}},
                "chain": {"rotation": {"0.0": [0, 0, 0], "0.25": [0, 4, 0], "0.5": [0, -3, 0],
                                        "0.8": [0, 1.5, 0], "1.2": [0, 0, 0]}},
                "inner_glow": {"scale": {"0.0": [1, 1, 1], "1.2": [1.1, 1.18, 1.1], "1.5": [1.04, 1.06, 1.04]}},
            },
        },
    }
    return {"format_version": "1.8.0", "animations": anims}


# ------------------------------------------------------------------------ main

def main():
    for d in (CRATE_MODELS, GEO_DIR, ANIM_DIR, TEX):
        os.makedirs(d, exist_ok=True)

    keep = {n + ".png" for n in TEXTURES} | {"crate_atlas.png", "crate_atlas_glowmask.png"}
    for p in glob.glob(os.path.join(TEX, "*.png")):
        if os.path.basename(p) not in keep:
            os.remove(p)

    tiles, emissive = build_atlas()
    print(f"atlas: {tiles} tiles, {emissive} emissive -> crate_atlas(+_glowmask).png")

    # The vanilla fallback needs the individual tiles as separate files too.
    for name, (fn, _) in TEXTURES.items():
        fn().save(os.path.join(TEX, name + ".png"))

    stats = {}
    for category in CATEGORIES:
        for stage in range(16):
            rig = build_rig(category, stage)
            stats[(category, stage)] = (len(rig.order), len(rig.all_elements()))
            with open(os.path.join(GEO_DIR, f"{category}_{stage}.geo.json"), "w", encoding="utf-8") as f:
                json.dump(emit_geo(rig, category, stage), f, indent=2)
            with open(os.path.join(CRATE_MODELS, f"{category}_{stage}.json"), "w", encoding="utf-8") as f:
                json.dump(emit_vanilla(rig, category, stage), f, indent=2)
            # "builtin/entity" is what makes vanilla hand the item to our custom
            # renderer — exactly how vanilla wires up chests and shulker boxes.
            # Pointing at the baked geometry instead bakes an ordinary model, and
            # ItemRenderer then draws the quads itself and never asks for a
            # custom renderer at all, so GeckoLib is never involved.
            with open(os.path.join(MODELS, f"{category}_cache_{TIERS[stage]}.json"), "w", encoding="utf-8") as f:
                json.dump({"parent": "builtin/entity",
                           "textures": {"particle": f"apotheosis_refined_conquest:item/crate/{mat_body(stage)}"},
                           "display": json.loads(json.dumps(DISPLAY))}, f, indent=2)
    print(f"models: {len(stats)} geo + {len(stats)} vanilla + {len(stats)} item")

    with open(os.path.join(ANIM_DIR, "crate.animation.json"), "w", encoding="utf-8") as f:
        json.dump(build_animations(), f, indent=2)
    print("animations: 5")

    print("\nbones / elements (armor):")
    for s in range(10):
        b, e = stats[("armor", s)]
        print(f"  {s:2d} {TIERS[s]:12s} bones={b:2d} elements={e:3d}")


if __name__ == "__main__":
    main()
