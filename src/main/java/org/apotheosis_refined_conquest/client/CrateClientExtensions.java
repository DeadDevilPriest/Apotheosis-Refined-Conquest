package org.apotheosis_refined_conquest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Client-side behaviour of the loot crates: which renderer draws them, and how
 * they sit in the hand.
 *
 * Only ever instantiated from {@code LootCacheItem.initializeClient}, so nothing
 * in here is loaded on a dedicated server.
 */
public class CrateClientExtensions implements IClientItemExtensions {

    /** Neutral hold, copied from ItemInHandRenderer.applyItemArmTransform. */
    private static final float ITEM_POS_X = 0.56F;
    private static final float ITEM_POS_Y = -0.52F;
    private static final float ITEM_POS_Z = -0.72F;
    private static final float ITEM_HEIGHT_SCALE = -0.6F;

    /**
     * Pushes the crate further from the camera than a normal held item, in model
     * pixels (16 to a block) to match how the crate itself is dimensioned.
     * It is far bulkier than a sword or a torch, so at vanilla's distance it
     * fills the corner of the screen and the lid leaves the frame as it opens.
     */
    private static final float CRATE_FORWARD_PIXELS = -4.0F;
    private static final float CRATE_FORWARD = CRATE_FORWARD_PIXELS / 16.0F;

    /**
     * Sideways nudge, in model pixels. Negative pulls the crate in toward the
     * middle of the screen and away from the holding hand's own side.
     *
     * Applied inside the arm's sign, so a left-handed player gets the mirrored
     * move rather than the crate sliding across to the wrong side.
     */
    private static final float CRATE_SIDE_PIXELS = -4.0F;
    private static final float CRATE_SIDE = CRATE_SIDE_PIXELS / 16.0F;

    private BlockEntityWithoutLevelRenderer renderer;

    /**
     * Where the held crate is drawn, in view space, for an arm of the given sign
     * (+1 right, -1 left) at full equip.
     *
     * Exposed so CrateArmRenderer can aim the free hand at the crate instead of
     * carrying its own copy of these numbers — move the crate and the reach
     * follows it.
     */
    public static float heldX(float sign) {
        return sign * (ITEM_POS_X + CRATE_SIDE);
    }

    public static float heldY() {
        return ITEM_POS_Y;
    }

    public static float heldZ() {
        return ITEM_POS_Z + CRATE_FORWARD;
    }

    /** Routes the crate to the GeckoLib renderer instead of a baked model. */
    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (this.renderer == null) {
            this.renderer = new CrateItemRenderer();
        }
        return this.renderer;
    }

    /**
     * Holds the crate still. Returning true is what removes the swing.
     *
     * ItemInHandRenderer.renderArmWithItem calls this before it decides on a
     * pose, and a true return jumps straight to renderItem — skipping the eat,
     * brush, bow and use branches AND the final else branch, which is the one
     * that ran applyItemArmTransform followed by applyItemArmAttackTransform.
     * That second call is the motion the crate was playing: the same swing a
     * hoe or shovel shows when it uses on a block, which is why the crate did
     * it despite UseAnim.NONE — UseAnim only governs the eat/drink/block poses,
     * not the attack transform.
     *
     * So the whole pose is ours now, and all it has to do is what vanilla's
     * applyItemArmTransform does: put the item in the resting hand position.
     * equipProgress is kept so the crate still rises into view when selected.
     *
     * Earlier attempts failed for reasons worth recording. Cancelling
     * RenderHandEvent logged canceled=true and changed nothing, because
     * something in this pack renders the hand past that event. A mixin forcing
     * the swingProgress argument to 0 never loaded at all: this project builds
     * no refmap, so a mixin against a vanilla (SRG-renamed) class cannot
     * resolve its target. This hook needs neither — it is a Forge API on the
     * render path itself.
     */
    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                           ItemStack stack, float partialTick,
                                           float equipProgress, float swingProgress) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(side * (ITEM_POS_X + CRATE_SIDE),
            ITEM_POS_Y + equipProgress * ITEM_HEIGHT_SCALE,
            ITEM_POS_Z + CRATE_FORWARD);
        return true;
    }
}
