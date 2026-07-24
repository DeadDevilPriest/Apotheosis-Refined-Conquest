package org.apotheosis_refined_conquest.mixin;

import org.apotheosis_refined_conquest.RarityFallbackMap;
import dev.shadowsoffire.apotheosis.adventure.affix.AttributeAffix;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Extends the rarity fallback to attribute affixes.
 *
 * AttributeAffix is the only affix type that does not read its per-rarity values
 * directly: the constructor precomputes a transient "modifiers" map, and
 * addModifiers/getDescription read from that instead. Wrapping the values map
 * (see LootRarityMixin) therefore had no effect here — items on the added tiers
 * received ability and effect affixes but no attributes at all.
 *
 * Wrapping the precomputed map as well closes that gap: a tier without its own
 * entry now resolves to the nearest defined one, exactly like every other affix.
 */
@Mixin(value = AttributeAffix.class, remap = false)
public abstract class AttributeAffixMixin {

    @Mutable
    @Shadow
    @Final
    protected Map<LootRarity, ?> modifiers;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void trifecta$wrapModifiers(CallbackInfo ci) {
        if (this.modifiers != null) {
            this.modifiers = (Map) RarityFallbackMap.wrap((Map) this.modifiers);
        }
    }
}
