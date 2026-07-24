package org.apotheosis_refined_conquest;

import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;

/**
 * Reads the capacity bonus off a backpack's affixes.
 *
 * This class only ever READS. The slot count itself is produced inside
 * Sophisticated Backpacks via BackpackWrapperMixin, which adds this bonus to the
 * value SB computes. Nothing here mutates a backpack inventory, so resizing can
 * no longer shift or duplicate stored items.
 */
public final class BackpackCapacityBridge {

    private BackpackCapacityBridge() {}

    /** Extra slots granted by capacity affixes on this stack, or 0. */
    public static int computeBonusSlots(ItemStack backpack) {
        if (!(backpack.getItem() instanceof BackpackItem)) return 0;
        int bonus = 0;
        try {
            for (AffixInstance inst : AffixHelper.streamAffixes(backpack).toList()) {
                if (!inst.affix().isBound()) continue;
                if (inst.affix().get() instanceof BackpackCapacityAffix capacity) {
                    LootRarity rarity = inst.rarity().isBound() ? inst.rarity().get() : null;
                    if (rarity == null) continue;
                    bonus += capacity.getBonusSlots(backpack, rarity, inst.level());
                }
            }
        } catch (Throwable t) {
            // Called from a mixin on a hot path — never propagate.
            return 0;
        }
        return Math.max(0, bonus);
    }
}
