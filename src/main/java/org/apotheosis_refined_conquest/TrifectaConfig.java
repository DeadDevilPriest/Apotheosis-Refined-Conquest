package org.apotheosis_refined_conquest;

import net.minecraftforge.common.ForgeConfigSpec;

public class TrifectaConfig {

    public static final ForgeConfigSpec SPEC;

    // Craft roll: crafting caps at RARE. Weighted — common most likely, rare rarest.
    // Higher tiers come from fusion upgrades or loot drops, never from crafting.
    public static final ForgeConfigSpec.BooleanValue CRAFT_ROLL_ENABLED;
    public static final ForgeConfigSpec.IntValue WEIGHT_COMMON;
    public static final ForgeConfigSpec.IntValue WEIGHT_UNCOMMON;
    public static final ForgeConfigSpec.IntValue WEIGHT_RARE;

    // Fusion table costs.
    public static final ForgeConfigSpec.IntValue FUSION_XP_COST_PER_AFFIX;

    // Backpack GUI: how tall the storage grid may grow before it scrolls instead.
    public static final ForgeConfigSpec.IntValue BACKPACK_MAX_VISIBLE_ROWS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("craft_roll");
        CRAFT_ROLL_ENABLED = b.comment("Apply an Apotheosis rarity to freshly crafted gear.")
            .define("enabled", true);
        WEIGHT_COMMON = b.comment("Crafting rolls only common/uncommon/rare. Weights:")
            .defineInRange("weight_common", 70, 0, 1000);
        WEIGHT_UNCOMMON = b.defineInRange("weight_uncommon", 25, 0, 1000);
        WEIGHT_RARE = b.defineInRange("weight_rare", 5, 0, 1000);
        b.pop();

        b.push("fusion");
        FUSION_XP_COST_PER_AFFIX = b.comment("XP levels charged per transferred/removed affix in the fusion table.")
            .defineInRange("xp_cost_per_affix", 3, 0, 100);
        b.pop();

        b.push("backpack_gui");
        BACKPACK_MAX_VISIBLE_ROWS = b.comment(
                "Maximum storage rows a backpack GUI may show at once.",
                "Sophisticated Core otherwise grows the window until it fills the whole screen;",
                "rows beyond this stay reachable through the existing scroll panel — no slots are lost.")
            .defineInRange("max_visible_rows", 18, 6, 64);
        b.pop();

        SPEC = b.build();
    }
}
