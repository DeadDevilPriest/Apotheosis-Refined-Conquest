package org.apotheosis_refined_conquest.mixin;

import org.apotheosis_refined_conquest.TrifectaAffixes;
import org.apotheosis_refined_conquest.TrifectaConfig;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps very large backpacks from growing a GUI taller than the screen.
 *
 * Sophisticated Core sizes the storage grid as
 *   visibleRows = min((availableHeight - 114) / 18, totalRows)
 *   imageHeight = 114 + visibleRows * 18
 * with availableHeight being the FULL gui-scaled screen height. A backpack with
 * a large capacity affix therefore stretches the window edge to edge — at 2560x1440
 * with GUI scale 2 that is 708 of 720 available units, leaving the player inventory
 * squeezed against the bottom edge.
 *
 * Clamping the height that goes IN is enough: imageHeight, getNumberOfVisibleRows()
 * and the scroll panel are all derived from it, so they stay consistent by
 * construction. Rows beyond the cap remain reachable through the scroll panel that
 * Sophisticated Core already uses — no slot is hidden or lost, the window just
 * stops growing and scrolls sooner.
 */
@Mixin(value = StorageScreenBase.class, remap = false)
public class StorageScreenBaseMixin {

    @ModifyVariable(
        method = "updateDimensionsAndSlotPositions",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 1)
    private int trifecta$capStorageHeight(int availableHeight) {
        try {
            int maxRows = TrifectaConfig.BACKPACK_MAX_VISIBLE_ROWS.get();
            int cap = StorageScreenBase.HEIGHT_WITHOUT_STORAGE_SLOTS + maxRows * 18;
            return Math.min(availableHeight, cap);
        } catch (Throwable t) {
            // Config not loaded yet — leave the vanilla layout untouched.
            TrifectaAffixes.LOGGER.warn("[StorageScreenMixin] height cap unavailable", t);
            return availableHeight;
        }
    }
}
