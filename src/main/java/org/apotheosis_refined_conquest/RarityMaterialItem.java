package org.apotheosis_refined_conquest;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Crafting material for one of the tiers this mod adds, mirroring Apotheosis'
 * own per-rarity materials so cache recipes look the same across all tiers.
 */
public class RarityMaterialItem extends Item {

    private final RarityTier tier;

    public RarityMaterialItem(Properties properties, RarityTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(style -> style.withColor(this.tier.getColor()));
    }
}
