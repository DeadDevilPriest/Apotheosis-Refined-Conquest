package org.apotheosis_refined_conquest.mixin;

import com.mojang.serialization.Codec;
import org.apotheosis_refined_conquest.RarityFallbackMap;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Gives every per-rarity value map a fallback, so rarities added by this mod
 * still receive affix values.
 *
 * LootRarity.mapCodec is the single point every such map flows through: the 13
 * value-carrying affix types use it either directly or via GemBonus.VALUES_CODEC,
 * and so do affixes contributed by other mods. Wrapping the decoded map here
 * therefore covers the whole affix system at once — including anything added
 * later — instead of needing one mixin per affix class.
 *
 * This only replaces a returned Codec with an equivalent one whose decode step
 * wraps the map; nothing in Apotheosis is mutated.
 */
@Mixin(value = LootRarity.class, remap = false)
public class LootRarityMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "mapCodec", at = @At("RETURN"), cancellable = true, require = 1)
    private static void trifecta$addRarityFallback(Codec valueCodec, CallbackInfoReturnable<Codec> cir) {
        Codec<Map<LootRarity, Object>> original = cir.getReturnValue();
        if (original == null) return;
        // xmap: decoded maps gain the fallback, encoding passes through unchanged.
        cir.setReturnValue(original.xmap(RarityFallbackMap::wrap, map -> map));
    }
}
