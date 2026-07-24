package org.apotheosis_refined_conquest.mixin;

import org.apotheosis_refined_conquest.ForcedAffixes;
import org.apotheosis_refined_conquest.TrifectaAffixes;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Restores affixes that the fusion table deliberately moved across weapon classes.
 *
 * getAffixesImpl filters every affix through canApplyTo against the item's OWN
 * LootCategory, so an axe-only affix on a sword is discarded on every read — the
 * NBT keeps it, but the game never sees it. This re-adds exactly those affixes
 * that {@link ForcedAffixes} marked, leaving Apotheosis' own filtering intact for
 * everything else.
 *
 * Safe by construction: the affix classes do not re-check the category when their
 * effects fire (AttributeAffix.addModifiers only rejects LootCategory.NONE), so a
 * restored affix behaves exactly as it would on its native item type.
 */
@Mixin(value = AffixHelper.class, remap = false)
public class AffixHelperMixin {

    @Inject(method = "getAffixesImpl", at = @At("RETURN"), cancellable = true, require = 1)
    private static void trifecta$restoreForcedAffixes(
        ItemStack stack,
        CallbackInfoReturnable<Map<DynamicHolder<? extends Affix>, AffixInstance>> cir) {

        try {
            Set<ResourceLocation> forced = ForcedAffixes.get(stack);
            if (forced.isEmpty()) return;

            Map<DynamicHolder<? extends Affix>, AffixInstance> current = cir.getReturnValue();
            if (current == null) return;

            CompoundTag data = stack.getTagElement("affix_data");
            if (data == null || !data.contains("affixes")) return;
            CompoundTag levels = data.getCompound("affixes");

            DynamicHolder<LootRarity> rarity = AffixHelper.getRarity(stack);
            if (!rarity.isBound()) rarity = RarityRegistry.getMinRarity();

            // Copy lazily — the returned map may be an immutable empty map.
            Map<DynamicHolder<? extends Affix>, AffixInstance> restored = null;

            for (ResourceLocation id : forced) {
                String key = id.toString();
                if (!levels.contains(key)) continue;

                DynamicHolder<Affix> holder = AffixRegistry.INSTANCE.holder(id);
                if (!holder.isBound()) continue;
                if (current.containsKey(holder)) continue; // Apotheosis kept it anyway

                if (restored == null) restored = new HashMap<>(current);
                restored.put(holder, new AffixInstance(holder, stack, rarity, levels.getFloat(key)));
            }

            if (restored != null) cir.setReturnValue(restored);
        } catch (Throwable t) {
            TrifectaAffixes.LOGGER.warn("[AffixHelperMixin] restoring forced affixes failed", t);
        }
    }
}
