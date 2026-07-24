package org.apotheosis_refined_conquest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.apotheosis_refined_conquest.LootCacheItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import javax.annotation.Nullable;

/**
 * Draws the loot crates. One instance is shared by every cache item — the model
 * resource is resolved per stack, so a single renderer covers all 80.
 *
 * The glow layer reads crate_atlas_glowmask.png and renders those pixels at full
 * brightness regardless of scene lighting.
 *
 * The lid is driven HERE rather than by the triggered animation. A network
 * trigger has to find the right animation instance on the client, which depends
 * on the stack's GeckoLib id having arrived first; driving the angle from the
 * player's remaining use ticks needs no network round trip at all and cannot
 * drift out of step with the hold.
 */
public class CrateItemRenderer extends GeoItemRenderer<LootCacheItem> {

    /** Matches the lid's final angle in the animations. */
    private static final float LID_OPEN_DEGREES = 65.0F;

    public CrateItemRenderer() {
        super(new CrateGeoModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /**
     * The lid angle is applied HERE, not in preRender.
     *
     * GeoItemRenderer.actuallyRender calls handleAnimations, which rewrites every
     * bone from the animation state — and that happens AFTER preRender, so an
     * angle set there is overwritten before anything is drawn. renderRecursively
     * walks the bones after animations have been applied, so this sticks.
     */
    @Override
    public void renderRecursively(PoseStack poseStack, LootCacheItem animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if ("lid".equals(bone.getName())) {
            applyLidProgress(animatable, bone, partialTick);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
            isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * Opens the lid in step with how long the crate has been held.
     *
     * Restricted to the hand perspectives and to the crate actually being used,
     * so the copies sitting in the inventory grid stay shut.
     */
    private void applyLidProgress(LootCacheItem animatable, GeoBone lid, float partialTick) {
        if (!isHeldPerspective(this.renderPerspective)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) return;
        if (player.getUseItem().getItem() != animatable) return;  // a different tier is held

        float remaining = Math.max(0.0F, player.getUseItemRemainingTicks() - partialTick);
        float progress = Mth.clamp(1.0F - remaining / LootCacheItem.OPEN_TICKS, 0.0F, 1.0F);

        // The lid stays shut until the free hand has actually reached it, then
        // covers its full travel in the time that is left. Without this delay it
        // swung open while the hand was still on its way, which read as the
        // crate opening by itself.
        float lidProgress = Mth.clamp(
            (progress - LootCacheItem.REACH_PHASE) / (1.0F - LootCacheItem.REACH_PHASE), 0.0F, 1.0F);

        // easeOutCubic — slow to start moving, settling gently into the open angle.
        float inv = 1.0F - lidProgress;
        float eased = 1.0F - inv * inv * inv;

        // POSITIVE rotation. This crate is Z-mirrored relative to a vanilla chest
        // (hinge at z=16, lock at z=0, the reverse of vanilla), so the sign that
        // opens a vanilla lid dips this one's front edge INTO the body instead.
        // A negative angle was doing exactly that — the "clips inward then swings"
        // artefact. Positive lifts the front edge up and back, as it should.
        lid.setRotX(eased * LID_OPEN_DEGREES * Mth.DEG_TO_RAD);
    }

    private static boolean isHeldPerspective(@Nullable ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    /** The lid is a child of root, so a recursive search is needed. */
    @Nullable
    private static GeoBone findBone(BakedGeoModel model, String name) {
        for (GeoBone top : model.topLevelBones()) {
            GeoBone hit = search(top, name);
            if (hit != null) return hit;
        }
        return null;
    }

    @Nullable
    private static GeoBone search(GeoBone bone, String name) {
        if (name.equals(bone.getName())) return bone;
        for (GeoBone child : bone.getChildBones()) {
            GeoBone hit = search(child, name);
            if (hit != null) return hit;
        }
        return null;
    }
}
