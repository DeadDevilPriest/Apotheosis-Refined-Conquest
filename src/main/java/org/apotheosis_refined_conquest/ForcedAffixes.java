package org.apotheosis_refined_conquest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Marks affixes that were deliberately moved onto an item whose LootCategory the
 * affix does not officially accept.
 *
 * Background: Apotheosis re-validates EVERY affix against the item's own category
 * each time it reads them back from NBT (AffixHelper.getAffixesImpl). Writing an
 * axe-only affix such as Shredding or Executing onto a sword therefore succeeds
 * silently but the affix is dropped on the next read — it exists in NBT yet is
 * invisible and inert.
 *
 * This marker records the exception so {@code AffixHelperMixin} can restore those
 * affixes after Apotheosis has filtered them out. Only affixes the fusion table
 * explicitly moved are marked; nothing else changes.
 *
 * The list lives INSIDE the {@code affix_data} compound on purpose: Apotheosis
 * caches parsed affixes keyed by that compound's hash, so editing the marker
 * invalidates the cache automatically. {@code AffixHelper.setAffixes} replaces
 * only the nested "affixes" tag, so a sibling key survives it.
 */
public final class ForcedAffixes {

    private static final String AFFIX_DATA = "affix_data";
    private static final String FORCED = "trifecta_forced";

    private ForcedAffixes() {}

    /** Affix ids that were force-applied to this stack (never null). */
    public static Set<ResourceLocation> get(ItemStack stack) {
        Set<ResourceLocation> out = new HashSet<>();
        if (stack.isEmpty()) return out;

        CompoundTag data = stack.getTagElement(AFFIX_DATA);
        if (data == null || !data.contains(FORCED, Tag.TAG_LIST)) return out;

        ListTag list = data.getList(FORCED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) out.add(id);
        }
        return out;
    }

    /**
     * Adds {@code ids} to the stack's forced set.
     * Call AFTER {@link dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper#setAffixes}
     * so the affix values are already in place when the marker lands.
     */
    public static void add(ItemStack stack, Set<ResourceLocation> ids) {
        if (stack.isEmpty() || ids.isEmpty()) return;

        Set<ResourceLocation> merged = get(stack);
        merged.addAll(ids);
        write(stack, merged);
    }

    /** Drops {@code ids} from the forced set — used when an affix is removed again. */
    public static void remove(ItemStack stack, Set<ResourceLocation> ids) {
        if (stack.isEmpty() || ids.isEmpty()) return;

        Set<ResourceLocation> merged = get(stack);
        if (!merged.removeAll(ids)) return;
        write(stack, merged);
    }

    private static void write(ItemStack stack, Set<ResourceLocation> ids) {
        CompoundTag data = stack.getOrCreateTagElement(AFFIX_DATA);
        if (ids.isEmpty()) {
            data.remove(FORCED);
            return;
        }
        ListTag list = new ListTag();
        for (ResourceLocation id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        data.put(FORCED, list);
    }
}
