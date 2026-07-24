package org.apotheosis_refined_conquest.client;

import org.apotheosis_refined_conquest.LootCacheItem;
import org.apotheosis_refined_conquest.TrifectaAffixes;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Points GeckoLib at the right crate for a given cache item.
 *
 * The geometry is chosen by category and rarity ordinal — 80 files, one per
 * combination — while the texture is a SINGLE shared atlas. That matters in
 * inventories: however many crates are on screen, GeckoLib binds one texture.
 */
public class CrateGeoModel extends GeoModel<LootCacheItem> {

    private static final ResourceLocation ATLAS =
        new ResourceLocation(TrifectaAffixes.MODID, "textures/item/crate/crate_atlas.png");
    private static final ResourceLocation ANIMATIONS =
        new ResourceLocation(TrifectaAffixes.MODID, "animations/crate.animation.json");

    @Override
    public ResourceLocation getModelResource(LootCacheItem crate) {
        return new ResourceLocation(TrifectaAffixes.MODID,
            "geo/crate/" + crate.getCategory().getId() + "_" + crate.getTier().getOrdinal() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LootCacheItem crate) {
        return ATLAS;
    }

    @Override
    public ResourceLocation getAnimationResource(LootCacheItem crate) {
        return ANIMATIONS;
    }
}
