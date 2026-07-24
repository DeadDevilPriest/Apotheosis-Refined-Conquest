package org.apotheosis_refined_conquest;

import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemItem;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Applies the configured base rarity (default: common) to freshly crafted gear.
 * Crafting can never produce higher tiers — those come from the fusion table
 * or from loot drops.
 */
public class CraftRollHandler {

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;
        if (!TrifectaConfig.CRAFT_ROLL_ENABLED.get()) return;

        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;
        if (LootCategory.forItem(crafted).isNone()) return;           // not affixable gear
        if (AffixHelper.getRarity(crafted).isBound()) return;         // already affixed
        if (gridHasAffixedGearOrGem(event.getInventory())) {
            TrifectaAffixes.LOGGER.info("[CraftRoll] {} skipped: grid contains affixed gear or a gem", crafted.getItem());
            return;
        }

        LootRarity rarity = rollCraftRarity(player);
        if (rarity == null) {
            TrifectaAffixes.LOGGER.warn("[CraftRoll] no craft rarity resolvable");
            return;
        }

        LootController.createLootItem(crafted, rarity, player.getRandom());
        TrifectaAffixes.LOGGER.info("[CraftRoll] applied {} to crafted {}", rarity, crafted.getItem());
    }

    private static boolean gridHasAffixedGearOrGem(Container grid) {
        if (grid == null) return false;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof GemItem) return true;
            if (AffixHelper.getRarity(stack).isBound()) return true;
        }
        return false;
    }

    /**
     * Weighted roll capped at RARE. Crafting must never produce the high tiers —
     * those come from fusion upgrades and loot drops. Weights are the per-tier
     * drop rates from the rarity table, so the relative odds stay consistent
     * with everything else in the mod.
     */
    private static LootRarity rollCraftRarity(Player player) {
        int total = 0;
        for (RarityTier tier : RarityTier.values()) {
            if (tier.getOrdinal() <= RarityTier.RARE.getOrdinal()) total += tier.getWeight();
        }
        if (total <= 0) return null;

        int roll = player.getRandom().nextInt(total);
        for (RarityTier tier : RarityTier.values()) {
            if (tier.getOrdinal() > RarityTier.RARE.getOrdinal()) continue;
            roll -= tier.getWeight();
            if (roll < 0) return tier.resolve();
        }
        return RarityTier.COMMON.resolve();
    }
}
