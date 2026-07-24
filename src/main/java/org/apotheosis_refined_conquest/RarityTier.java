package org.apotheosis_refined_conquest;

import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The sixteen rarity tiers.
 *
 * Six are Apotheosis' own; the other ten are added by this mod. Existing tiers
 * keep their colours (the sole exception being ancient, which swapped its
 * "rainbow" with chaos on request).
 *
 * The bonus multiplier is calibrated against Apotheosis' own gem scaling, which
 * is the reference for how strongly a bonus grows with rarity:
 *
 *   common 1.0 · uncommon 1.88 · rare 2.88 · epic 4.0 · mythic 5.25 · ancient 7.38
 *
 * The added tiers interpolate between and extrapolate beyond those points, so a
 * value inherited from a neighbouring tier is scaled by the ratio of the two
 * multipliers instead of being copied unchanged — see {@link RarityFallbackMap}.
 */
public enum RarityTier {

    WORN        ( 0, "worn",         "apotheosis_refined_conquest", "#6B5D4F", 3000, 0.60F),
    COMMON      ( 1, "common",       "apotheosis",       "#808080", 2800, 1.00F),
    UNCOMMON    ( 2, "uncommon",     "apotheosis",       "#33FF33", 1800, 1.88F),
    IMPROVED    ( 3, "improved",     "apotheosis_refined_conquest", "#00E5B0", 1200, 2.35F),
    RARE        ( 4, "rare",         "apotheosis",       "#5555FF",  600, 2.88F),
    EXQUISITE   ( 5, "exquisite",    "apotheosis_refined_conquest", "#5E7BFF",  300, 3.40F),
    EPIC        ( 6, "epic",         "apotheosis",       "#BB00BB",  150, 4.00F),
    HEROIC      ( 7, "heroic",       "apotheosis_refined_conquest", "#D946B0",   70, 4.45F),
    LEGENDARY   ( 8, "legendary",    "apotheosis_refined_conquest", "#FFC61A",   35, 4.85F),
    MYTHIC      ( 9, "mythic",       "apotheosis",       "#ED7014",   15, 5.25F),
    ANCIENT     (10, "ancient",      "apotheosis",       "#C77B3B",   10, 7.38F),
    PRIMEVAL    (11, "primeval",     "apotheosis_refined_conquest", "#8A6E4B",    6, 9.20F),
    RELIC       (12, "relic",        "apotheosis_refined_conquest", "#EDE4D3",    3, 11.50F),
    TRANSCENDENT(13, "transcendent", "apotheosis_refined_conquest", "#B8F5FF",    1, 14.30F),
    COSMIC      (14, "cosmic",       "apotheosis_refined_conquest", "#1A0B2E",    1, 17.80F),
    CHAOS       (15, "chaos",        "apotheosis_refined_conquest", "rainbow",    1, 22.20F);

    private final int ordinal;
    private final String id;
    private final String namespace;
    private final String color;
    private final int weight;
    private final float bonusMultiplier;

    RarityTier(int ordinal, String id, String namespace, String color, int weight, float bonusMultiplier) {
        this.ordinal = ordinal;
        this.id = id;
        this.namespace = namespace;
        this.color = color;
        this.weight = weight;
        this.bonusMultiplier = bonusMultiplier;
    }

    public int getOrdinal() {
        return this.ordinal;
    }

    public String getId() {
        return this.id;
    }

    public String getNamespace() {
        return this.namespace;
    }

    /** Datapack colour value; may be the special string "rainbow". */
    public String getColorValue() {
        return this.color;
    }

    public int getWeight() {
        return this.weight;
    }

    /** How strongly bonuses scale at this tier, relative to common = 1.0. */
    public float getBonusMultiplier() {
        return this.bonusMultiplier;
    }

    /** True for the tiers this mod introduces (they need their own material item). */
    public boolean isCustom() {
        return "apotheosis_refined_conquest".equals(this.namespace);
    }

    public ResourceLocation getRarityId() {
        return new ResourceLocation(this.namespace, this.id);
    }

    public String getMaterialId() {
        return this.namespace + ":" + this.id + "_material";
    }

    /** Tint for OUR item names; "rainbow" has no hex equivalent and renders neutral. */
    public TextColor getColor() {
        TextColor parsed = TextColor.parseColor(this.color);
        return parsed != null ? parsed : TextColor.fromRgb(0xFFFFFF);
    }

    @Nullable
    public LootRarity resolve() {
        DynamicHolder<LootRarity> holder = RarityRegistry.INSTANCE.holder(getRarityId());
        return holder.isBound() ? holder.get() : null;
    }

    /** Look a tier up from an Apotheosis rarity, by registry id. */
    @Nullable
    public static RarityTier byRarity(@Nullable LootRarity rarity) {
        if (rarity == null) return null;
        ResourceLocation id = RarityRegistry.INSTANCE.getKey(rarity);
        if (id == null) return null;
        for (RarityTier tier : values()) {
            if (tier.getRarityId().equals(id)) return tier;
        }
        return null;
    }
}
