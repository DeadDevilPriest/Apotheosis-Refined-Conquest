package org.apotheosis_refined_conquest.mixin;

import org.apotheosis_refined_conquest.BackpackCapacityBridge;
import org.apotheosis_refined_conquest.TrifectaAffixes;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Sophisticated Backpacks itself account for our capacity affix.
 *
 * Rather than pushing a slot count in from outside via setSlotNumbers — which
 * rebuilds and migrates the backpack inventory and duplicated items whenever it
 * happened at the wrong moment — we simply add the bonus to the number SB reads.
 *
 * Consequences:
 *  - We never mutate a backpack inventory, so the duplication failure class is
 *    structurally impossible.
 *  - It applies on every path automatically: crafting table, smithing table,
 *    loot, and the fusion table.
 *  - Tier upgrades are correct for free: SB resolves the new tier's base and we
 *    add the bonus on top of whatever it computed.
 *
 * remap = false because the target is another mod's class, not Minecraft, so the
 * names are identical at runtime and must not be run through SRG mapping.
 */
@Mixin(value = BackpackWrapper.class, remap = false)
public abstract class BackpackWrapperMixin {

    // require = 1: if this target ever stops matching, fail LOUDLY at startup
    // instead of silently doing nothing (which is what happened before).
    @Inject(method = "getNumberOfInventorySlots", at = @At("RETURN"), cancellable = true, require = 1)
    private void trifecta$addCapacityBonus(CallbackInfoReturnable<Integer> cir) {
        try {
            ItemStack backpack = ((IBackpackWrapper) this).getBackpack();
            if (backpack == null || backpack.isEmpty()) return;

            int bonus = BackpackCapacityBridge.computeBonusSlots(backpack);
            if (bonus > 0) {
                int base = cir.getReturnValueI();
                // DEBUG: this runs on every inventory handler creation.
                TrifectaAffixes.LOGGER.debug("[BackpackMixin] {} base={} bonus={}",
                    backpack.getItem(), base, bonus);
                cir.setReturnValue(base + bonus);
            }
        } catch (Throwable t) {
            TrifectaAffixes.LOGGER.warn("[BackpackMixin] failed", t);
        }
    }
}
