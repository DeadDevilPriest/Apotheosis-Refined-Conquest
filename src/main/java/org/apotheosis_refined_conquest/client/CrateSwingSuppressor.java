package org.apotheosis_refined_conquest.client;

import org.apotheosis_refined_conquest.LootCacheItem;
import org.apotheosis_refined_conquest.TrifectaAffixes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Keeps the arm still while a crate is being opened.
 *
 * Earlier attempts all tried to stop the swing from being DRAWN — cancelling
 * RenderHandEvent, forcing the swingProgress argument to 0, taking over the pose
 * via applyForgeHandTransform. None of them worked, and the reason is that this
 * pack has several mods that draw the hand (Better Combat, Not Enough
 * Animations, player-animation-lib, Oculus). Each one is a separate draw path,
 * so suppressing any single path leaves the others untouched.
 *
 * What every one of those paths DOES share is where the swing comes from:
 * LivingEntity.swinging / swingTime / attackAnim. Clearing those clears the
 * swing everywhere at once, no matter who ends up rendering. Attacking that
 * state instead of the rendering is the only fix that does not have to know
 * which mod is drawing.
 *
 * Scope is deliberately narrow: only the local player, only while actually
 * using a crate. A normal left-click swing is untouched.
 */
public class CrateSwingSuppressor {

    /** Throttle for the diagnostic line; this runs every tick. */
    private static long lastLog = 0L;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!player.isUsingItem()) return;
        if (!(player.getUseItem().getItem() instanceof LootCacheItem)) return;

        // Nothing to do unless something actually started a swing.
        if (!player.swinging && player.attackAnim == 0.0F && player.oAttackAnim == 0.0F) return;

        long now = System.currentTimeMillis();
        if (now - lastLog > 500) {
            lastLog = now;
            // Tells us whether the motion really is the vanilla swing state.
            // If this never appears while the crate still moves, the animation
            // comes from a mod's own system rather than from attackAnim.
            TrifectaAffixes.LOGGER.info(
                "[CrateSwing] clearing swing state: swinging={} swingTime={} attackAnim={}",
                player.swinging, player.swingTime, player.attackAnim);
        }

        // updateSwingTime recomputes attackAnim from swingTime each tick, so
        // clearing swinging keeps it at zero from here on; the two anim fields
        // are zeroed as well so the current frame is already still.
        player.swinging = false;
        player.swingTime = 0;
        player.attackAnim = 0.0F;
        player.oAttackAnim = 0.0F;
    }
}
