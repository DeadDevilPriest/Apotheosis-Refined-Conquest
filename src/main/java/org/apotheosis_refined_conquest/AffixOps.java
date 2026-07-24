package org.apotheosis_refined_conquest;

import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.AttributeAffix;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import dev.shadowsoffire.placebo.util.StepFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Core affix manipulation built on Apotheosis' AffixHelper.
 *
 * Central invariant: NO operation may ever lower an item's rarity
 * (the downgrade guard lives in {@link #isUpgradeAllowed}).
 */
public final class AffixOps {

    private AffixOps() {}

    /** Access to AttributeAffix's per-rarity value functions for exact stat stacking. */
    @Nullable
    private static final Field ATTRIBUTE_VALUES_FIELD;

    static {
        Field f = null;
        try {
            f = AttributeAffix.class.getDeclaredField("values");
            f.setAccessible(true);
        } catch (Exception e) {
            TrifectaAffixes.LOGGER.warn("AttributeAffix.values not accessible — stat stacking falls back to level addition", e);
        }
        ATTRIBUTE_VALUES_FIELD = f;
    }

    /** Resolved rarity of a stack, or null when the item carries no affix data. */
    @Nullable
    public static LootRarity getRarity(ItemStack stack) {
        if (stack.isEmpty()) return null;
        DynamicHolder<LootRarity> holder = AffixHelper.getRarity(stack);
        return holder != null && holder.isBound() ? holder.get() : null;
    }

    /** Downgrade guard: target rarity must be at least the current one. */
    public static boolean isUpgradeAllowed(@Nullable LootRarity current, LootRarity target) {
        return current == null || target.isAtLeast(current);
    }

    /**
     * Same equipment type — the requirement for raising rarity.
     *
     * Compares the LootCategory rather than the item, so an iron and a diamond
     * sword still count as the same type while a sword and an axe do not.
     *
     * Not used for the rarity upgrade itself — that requires the identical item
     * via ItemStack.isSameItem — but kept for type-level checks.
     */
    public static boolean sameType(ItemStack a, ItemStack b) {
        LootCategory ca = LootCategory.forItem(a);
        LootCategory cb = LootCategory.forItem(b);
        return !ca.isNone() && ca == cb;
    }

    /**
     * Whether affixes may be moved from one item to the other.
     *
     * Looser than {@link #sameType}: any two melee weapons can exchange bonuses
     * (sword to axe and back), while ranged weapons stay separate. Armour and
     * tools still require an exact type match.
     *
     * Individual affixes are still vetted by Apotheosis' own canApplyTo during
     * the transfer, so a sword-only affix will not land on an axe.
     */
    public static boolean canTransfer(ItemStack a, ItemStack b) {
        LootCategory ca = LootCategory.forItem(a);
        LootCategory cb = LootCategory.forItem(b);
        if (ca.isNone() || cb.isNone()) return false;
        if (ca == cb) return true;
        return isMelee(ca) && isMelee(cb);
    }

    private static boolean isMelee(LootCategory category) {
        return category.isWeapon() && !category.isRanged();
    }

    /**
     * Transfers the selected affixes from {@code sacrifice} onto {@code target}.
     * Selected ids replace an existing affix of the same id, otherwise they are added.
     * Invalid affixes (wrong category / rarity) are skipped silently.
     *
     * @return number of affixes actually applied
     */
    public static int transferAffixes(ItemStack target, ItemStack sacrifice, Set<ResourceLocation> selected) {
        LootRarity targetRarity = getRarity(target);
        if (targetRarity == null) return 0;
        DynamicHolder<LootRarity> targetRarityHolder = RarityRegistry.INSTANCE.holder(targetRarity);
        LootCategory targetCategory = LootCategory.forItem(target);
        if (targetCategory.isNone()) return 0;

        Map<DynamicHolder<? extends Affix>, AffixInstance> targetAffixes = new HashMap<>(AffixHelper.getAffixes(target));
        Map<DynamicHolder<? extends Affix>, AffixInstance> sourceAffixes = AffixHelper.getAffixes(sacrifice);

        // Many affixes are locked to one weapon class (Shredding and Executing accept
        // only HEAVY_WEAPON), so an axe bonus would be rejected on a sword. Between
        // melee weapons we accept an affix that ANY melee class would take and record
        // the exception — Apotheosis re-checks the category on every read, so without
        // the marker the affix would silently vanish again (see ForcedAffixes).
        LootCategory sourceCategory = LootCategory.forItem(sacrifice);
        boolean meleePair = isMelee(targetCategory) && isMelee(sourceCategory);

        Set<ResourceLocation> forced = new HashSet<>();
        int applied = 0;
        for (Map.Entry<DynamicHolder<? extends Affix>, AffixInstance> e : sourceAffixes.entrySet()) {
            DynamicHolder<? extends Affix> holder = e.getKey();
            if (!selected.contains(holder.getId())) continue;
            if (!holder.isBound()) continue;

            Affix affix = holder.get();
            boolean isForced = false;
            if (!affix.canApplyTo(target, targetCategory, targetRarity)) {
                if (!meleePair || !acceptedByAnyMelee(affix, target, targetRarity)) continue;
                isForced = true;
            }

            float level = e.getValue().level();

            // Same stat already on the target: STACK the values (+30% + +20% = +50%).
            AffixInstance existing = targetAffixes.get(holder);
            if (existing != null) {
                level = stackLevels(affix, targetRarity, existing.level(), level);
            }

            AffixInstance rebound = new AffixInstance(holder, target, targetRarityHolder, level);
            targetAffixes.put(holder, rebound);
            if (isForced) forced.add(holder.getId());
            applied++;
        }

        if (applied > 0) {
            AffixHelper.setAffixes(target, targetAffixes);   // replaces the "affixes" tag
            ForcedAffixes.add(target, forced);               // marker must survive it
        }
        return applied;
    }

    /**
     * Whether any melee class would accept this affix at that rarity.
     *
     * Checking both melee categories rather than just the sacrifice's covers the
     * case of re-transferring an already-forced affix (axe -> sword -> sword) and
     * still enforces that the affix actually has a value at the target rarity.
     */
    private static boolean acceptedByAnyMelee(Affix affix, ItemStack target, LootRarity rarity) {
        return affix.canApplyTo(target, LootCategory.SWORD, rarity)
            || affix.canApplyTo(target, LootCategory.HEAVY_WEAPON, rarity);
    }

    /**
     * Removes the selected affixes from {@code target}.
     *
     * @return number of affixes removed
     */
    public static int removeAffixes(ItemStack target, Set<ResourceLocation> selected) {
        Map<DynamicHolder<? extends Affix>, AffixInstance> affixes = new HashMap<>(AffixHelper.getAffixes(target));
        int before = affixes.size();
        affixes.keySet().removeIf(holder -> selected.contains(holder.getId()));
        int removed = before - affixes.size();
        if (removed > 0) {
            AffixHelper.setAffixes(target, affixes);
            ForcedAffixes.remove(target, selected);   // drop stale markers
        }
        return removed;
    }

    /**
     * Combined level so that value(combined) == value(a) + value(b).
     *
     * For attribute affixes the per-rarity StepFunction is inverted numerically
     * (binary search over the monotonic function, allowing levels beyond 1.0 so
     * stacked stats can exceed a single roll's maximum). Anything else falls
     * back to capped level addition.
     */
    private static float stackLevels(Affix affix, LootRarity rarity, float a, float b) {
        if (affix instanceof AttributeAffix attrAffix && ATTRIBUTE_VALUES_FIELD != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<LootRarity, StepFunction> values = (Map<LootRarity, StepFunction>) ATTRIBUTE_VALUES_FIELD.get(attrAffix);
                StepFunction f = values.get(rarity);
                if (f != null) {
                    float sum = f.get(a) + f.get(b);
                    boolean increasing = f.get(4.0F) >= f.get(0.0F);

                    float lo = 0.0F;
                    float hi = 4.0F;
                    if (increasing ? f.get(hi) < sum : f.get(hi) > sum) return hi;
                    for (int i = 0; i < 40; i++) {
                        float mid = (lo + hi) / 2.0F;
                        boolean reached = increasing ? f.get(mid) >= sum : f.get(mid) <= sum;
                        if (reached) hi = mid;
                        else lo = mid;
                    }
                    return hi;
                }
            } catch (Throwable t) {
                TrifectaAffixes.LOGGER.warn("stat stacking inversion failed for {}", affix, t);
            }
        }
        return Math.min(1.0F, a + b);
    }

    /**
     * Raises the item's rarity while KEEPING its affixes. Because affix values
     * scale with rarity, existing bonuses grow automatically
     * (e.g. common "+4 Max Health" becomes epic "+12 Max Health").
     *
     * @return true when the rarity was raised
     */
    public static boolean upgradeRarity(ItemStack target, LootRarity newRarity) {
        LootRarity current = getRarity(target);
        if (current == null || current == newRarity) return false;
        if (!isUpgradeAllowed(current, newRarity)) return false;   // downgrade guard

        DynamicHolder<LootRarity> newHolder = RarityRegistry.INSTANCE.holder(newRarity);

        // Re-bind every affix instance to the new rarity, keeping levels.
        Map<DynamicHolder<? extends Affix>, AffixInstance> affixes = new HashMap<>(AffixHelper.getAffixes(target));
        Map<DynamicHolder<? extends Affix>, AffixInstance> rebound = new HashMap<>();
        for (Map.Entry<DynamicHolder<? extends Affix>, AffixInstance> e : affixes.entrySet()) {
            rebound.put(e.getKey(), new AffixInstance(e.getKey(), target, newHolder, e.getValue().level()));
        }

        AffixHelper.setRarity(target, newRarity);
        AffixHelper.setAffixes(target, rebound);
        return true;
    }
}
