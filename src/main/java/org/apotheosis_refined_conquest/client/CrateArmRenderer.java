package org.apotheosis_refined_conquest.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.apotheosis_refined_conquest.LootCacheItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Draws both arms while a crate is held.
 *
 * Vanilla renders NO player arm for a non-empty hand — renderPlayerArm is only
 * reached in the stack.isEmpty() branch of renderArmWithItem. In this pack the
 * arm you normally see comes from Punchy, and blacklisting the crates to stop
 * Punchy's use animation took the arm with it. So the arms have to come from
 * here.
 *
 * The event is NOT cancelled. Cancelling would take the crate away too; drawing
 * additively leaves vanilla to render the crate itself right afterwards, with
 * the still pose from CrateClientExtensions.
 *
 * BUILT IN STEPS. In now:
 *   free hand rests in the holding hand's pose, mirrored on X            done
 *   1. move it along X only, linearly, to the midpoint between the hands done
 *   2. global rotation on both arms, yaw / pitch / roll                  <-- here
 *   3. rotation together with leading the hand to the lid
 *
 * Anything belonging to a later step is deliberately absent rather than present
 * and switched off: an earlier attempt moved several things per change, and it
 * was never clear which one was responsible for what was on screen.
 */
public class CrateArmRenderer {

    /**
     * Vanilla arm placement constants, from ItemInHandRenderer.renderPlayerArm.
     *
     * COPIES OF VANILLA, NOT DIALS. Adjusting the arms belongs in HOLD_* and
     * REACH_* below; these four describe where vanilla puts an empty hand, and
     * changing them moves BOTH arms off that reference at once. ARM_POS_X at 0
     * instead of 0.64 dragged each hand 0.64 toward the middle of the screen —
     * the main hand landed at x +0.095, all but dead centre.
     *
     * ARM_HEIGHT_SCALE multiplies equipProgress, which is 0 for a settled item
     * and rises to 1 only while one is being swapped — so getting it wrong is
     * invisible in every still frame and shows up solely as a broken dip on the
     * item switch.
     */
    private static final float ARM_POS_X = 1.0F;
    private static final float ARM_POS_Y = -0.6F;
    private static final float ARM_POS_Z = -0.72F;
    private static final float ARM_HEIGHT_SCALE = -0.6F;

    /** Model pixel — 16 to a block, the unit the rest of the crate is built in. */
    private static final float PIXEL = 1.0F / 16.0F;

    /**
     * Pose of the holding arm relative to vanilla's resting place: pixels for
     * the offsets, degrees for the turns.
     *
     * THE ONLY PLACE EITHER ARM IS POSITIONED. The free hand is this pose
     * reflected, plus its own reach — so changing anything here moves both arms
     * together and they stay symmetric by construction.
     */
    private static final float HOLD_X_PIXELS = 0.0F;
    private static final float HOLD_Y_PIXELS = 0.0F;
    private static final float HOLD_Z_PIXELS = 0.0F;
    private static final float HOLD_YAW_DEG = 15.0F;
    private static final float HOLD_PITCH_DEG = 15.0F;
    private static final float HOLD_ROLL_DEG = 0.0F;

    /**
     * Where a hand actually ends up in view space under vanilla's pose, for an
     * arm of sign +1; mirror X for the other side.
     *
     * Not the arm's root — the two are far apart, because renderPlayerArm
     * rotates the limb about pivots several blocks away. Obtained by running
     * that transform chain onto the hand end of the arm model:
     *
     *     root     x +-0.347   y -1.106   z -0.740
     *     shoulder x +-0.504   y -0.819   z -0.663
     *     hand     x +-0.735   y -0.479   z -1.138
     *
     * The model's arm side is the OPPOSITE sign to the chain's: the free hand is
     * the left arm, whose pivot is at +5, but it runs through the chain with
     * sign -1 (right_arm sits at -5 with its box at x -3..1, left_arm at +5 with
     * its box at -1..3). Mirroring the model point along with the chain put the
     * hand on the wrong side of its own pivot, and after the rotations that came
     * out as a phantom 0.43 of depth.
     */
    private static final float HAND_REST_X = 0.735F;
    private static final float HAND_REST_Y = -0.479F;
    private static final float HAND_REST_Z = -1.138F;

    /**
     * Share of the open spent reaching. Shared with the lid in LootCacheItem so
     * the lid begins turning exactly as the hand arrives.
     */
    private static final float REACH_PHASE = LootCacheItem.REACH_PHASE;

    /**
     * What the free hand does DIFFERENTLY from the mirrored holding hand, in
     * degrees. Its base pose is not set here — that comes from HOLD_*, mirrored.
     *
     * Only one arm is positioned. The other is its reflection, so the two cannot
     * drift apart the way two independent sets of numbers do, and there is half
     * as much to tune. These three are the free hand's own job on top of that:
     * it has to face the crate, which is not something a mirror can produce.
     */
    private static final float REACH_YAW_DEG = -30.0F;
    private static final float REACH_PITCH_DEG = 0.0F;
    private static final float REACH_ROLL_DEG = 0.0F;

    /**
     * Bone-space turn of the free arm at its shoulder, in degrees. This is the
     * kind of value Better Combat's animation JSONs hold.
     *
     * From pose_two_handed_sword at tick 10, converted from radians: its leftArm
     * — the free hand reaching to the hilt — carries yaw +0.9404 = +53.9 deg,
     * roll about zero, and its position is left at the bone's default (x 5, y 2,
     * z 0). Better Combat builds a two-handed grip almost purely out of shoulder
     * rotation and barely moves the arm at all, which is the opposite of what
     * this class was doing.
     *
     * Its pitch of -61.5 deg is deliberately NOT copied. That figure orients a
     * third-person arm hanging at the body; in first person vanilla's chain has
     * already pitched the limb forward, which is exactly why renderHand forces
     * xRot to 0. Taking the number as well would pitch it twice.
     */
    private static final float REACH_BONE_YAW_DEG = 53.9F;
    private static final float REACH_BONE_PITCH_DEG = 0.0F;
    private static final float REACH_BONE_ROLL_DEG = 0.0F;

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        // Only once per frame, and only for a crate.
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getItemStack().getItem() instanceof LootCacheItem)) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float equipProgress = event.getEquipProgress();

        HumanoidArm holding = player.getMainArm();
        HumanoidArm opening = holding.getOpposite();

        // The hand the crate rests in.
        poseStack.pushPose();
        renderArm(mc, player, poseStack, buffer, light, equipProgress, holding,
            HOLD_X_PIXELS * PIXEL, HOLD_Y_PIXELS * PIXEL, HOLD_Z_PIXELS * PIXEL,
            HOLD_YAW_DEG, HOLD_PITCH_DEG, HOLD_ROLL_DEG,
            0.0F, 0.0F, 0.0F);
        poseStack.popPose();

        // The free hand is drawn whenever the crate is held, not only while it is
        // being opened. At rest it is the exact mirror of the holding hand, and
        // that is the pose everything else is measured from — but the reach
        // starts the instant the button goes down, so with the hand appearing
        // only during the open there was no moment at which the resting pose
        // could be looked at.
        float progress = openProgress(player, event.getPartialTick());

        float holdingSign = holding == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float openingSign = opening == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        // Step 1: X only, linear, from rest to halfway between the two hands.
        // The hands sit at +-HAND_REST_X, so their midpoint is x = 0 exactly and
        // the travel is the whole of HAND_REST_X — written as a midpoint rather
        // than as that number so it stays correct if the pose ever changes.
        float handX = openingSign * HAND_REST_X;
        float midX = (holdingSign * HAND_REST_X + openingSign * HAND_REST_X) * 0.5F;

        float t = Mth.clamp(progress / REACH_PHASE, 0.0F, 1.0F);

        // The free hand's base pose is the holding hand's, reflected. Mirroring
        // across the screen's vertical plane negates the X offset and the turns
        // about Y and Z, and leaves the X turn and the Y and Z offsets as they
        // are. It has to be done explicitly: these are applied in view space,
        // so unlike vanilla's own chain they are not mirrored by the arm's sign.
        //
        // Its own reach is then added on top, so HOLD_* stays the one place the
        // pose of both arms is set.
        poseStack.pushPose();
        renderArm(mc, player, poseStack, buffer, light, equipProgress, opening,
            -HOLD_X_PIXELS * PIXEL + (midX - handX) * t,
            HOLD_Y_PIXELS * PIXEL,
            HOLD_Z_PIXELS * PIXEL,
            -HOLD_YAW_DEG + REACH_YAW_DEG,
            HOLD_PITCH_DEG + REACH_PITCH_DEG,
            -HOLD_ROLL_DEG + REACH_ROLL_DEG,
            REACH_BONE_YAW_DEG, REACH_BONE_PITCH_DEG, REACH_BONE_ROLL_DEG);
        poseStack.popPose();
    }

    /**
     * Vanilla's arm placement with swingProgress fixed at 0 — every swing term
     * in renderPlayerArm is a sin() of it, so they all drop out and what is left
     * is the resting arm. The offsets shift the arm's root through view space
     * before any rotation, so the whole limb moves without bending.
     */
    private static void renderArm(Minecraft mc, AbstractClientPlayer player, PoseStack poseStack,
                                  MultiBufferSource buffer, int light, float equipProgress,
                                  HumanoidArm arm, float ox, float oy, float oz,
                                  float yaw, float pitch, float roll,
                                  float boneYaw, float bonePitch, float boneRoll) {
        boolean right = arm != HumanoidArm.LEFT;
        float sign = right ? 1.0F : -1.0F;

        poseStack.translate(sign * ARM_POS_X + ox,
            ARM_POS_Y + equipProgress * ARM_HEIGHT_SCALE + oy,
            ARM_POS_Z + oz);

        // Global turn, in view space at the arm's root and ahead of vanilla's
        // own chain, so the limb swings as one piece.
        //
        // Order is yaw, pitch, roll. Rotations do not commute, so this is a
        // choice, not a formality: applied here each one turns the limb about a
        // VIEW axis, which means roll swings the whole arm across the screen
        // rather than twisting the wrist. Large values compound quickly.
        if (yaw != 0.0F) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        if (pitch != 0.0F) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        if (roll != 0.0F) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));

        poseStack.mulPose(Axis.YP.rotationDegrees(sign * 45.0F));

        RenderSystem.setShaderTexture(0, player.getSkinTextureLocation());

        poseStack.translate(sign * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(sign * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(sign * -135.0F));
        poseStack.translate(sign * 5.6F, 0.0F, 0.0F);

        // The arm bone is posed and drawn here rather than through
        // renderRightHand/renderLeftHand, because those force arm.xRot = 0 —
        // vanilla's chain supplies the orientation, so it wipes any pitch put
        // on the bone. Doing it directly is what makes bone-space posing, the
        // kind Better Combat's animation JSONs are written in, possible at all.
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        ModelPart armPart = right ? model.rightArm : model.leftArm;
        ModelPart sleevePart = right ? model.rightSleeve : model.leftSleeve;

        // Pitch replaces what vanilla zeroes; yaw and roll add to whatever
        // setupAnim left there, so all three at zero render exactly as before.
        armPart.xRot = bonePitch * Mth.DEG_TO_RAD;
        armPart.yRot += boneYaw * Mth.DEG_TO_RAD;
        armPart.zRot += boneRoll * Mth.DEG_TO_RAD;
        sleevePart.copyFrom(armPart);

        ResourceLocation skin = player.getSkinTextureLocation();
        armPart.render(poseStack, buffer.getBuffer(RenderType.entitySolid(skin)),
            light, OverlayTexture.NO_OVERLAY);
        sleevePart.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(skin)),
            light, OverlayTexture.NO_OVERLAY);
    }

    /**
     * How far along the opening is, from the same source the lid uses in
     * CrateItemRenderer, so arm and lid cannot drift apart.
     */
    private static float openProgress(LocalPlayer player, float partialTick) {
        if (!player.isUsingItem()) return 0.0F;
        if (!(player.getUseItem().getItem() instanceof LootCacheItem)) return 0.0F;

        float remaining = Math.max(0.0F, player.getUseItemRemainingTicks() - partialTick);
        return Mth.clamp(1.0F - remaining / LootCacheItem.OPEN_TICKS, 0.0F, 1.0F);
    }
}
