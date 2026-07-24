package org.apotheosis_refined_conquest;

import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.placebo.util.StepFunction;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

/**
 * A per-rarity value map that never has a "hole".
 *
 * Every affix declares its values keyed by rarity name, so a rarity added by
 * this mod (Improved, Heroic, Legendary, …) would find no entry and contribute
 * nothing — the item would show a colour and a name but carry no stats.
 *
 * This wrapper resolves a missing rarity to the closest DEFINED one, preferring
 * the highest rarity at or below the requested ordinal (falling back upward only
 * if the request sits below everything defined). Affix power therefore inherits
 * from the neighbouring tier instead of vanishing.
 *
 * Note that the added tiers still feel stronger despite sharing values: a
 * rarity's own "rules" decide how MANY affixes and sockets an item rolls, and
 * those are defined per tier.
 *
 * Only reads are intercepted; iteration and serialisation pass straight through
 * to the delegate, so encoding an affix writes back exactly what it declared.
 */
public class RarityFallbackMap<T> extends AbstractMap<LootRarity, T> {

    private final Map<LootRarity, T> delegate;

    private RarityFallbackMap(Map<LootRarity, T> delegate) {
        this.delegate = delegate;
    }

    public static <T> Map<LootRarity, T> wrap(Map<LootRarity, T> delegate) {
        if (delegate instanceof RarityFallbackMap) return delegate;
        return new RarityFallbackMap<>(delegate);
    }

    @Override
    public Set<Entry<LootRarity, T>> entrySet() {
        // Pass through: serialisation and iteration see only real entries.
        return this.delegate.entrySet();
    }

    @Override
    public T get(Object key) {
        T direct = this.delegate.get(key);
        if (direct != null) return direct;
        if (!(key instanceof LootRarity rarity)) return null;
        return nearest(rarity);
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    /**
     * Highest defined rarity at or below the request (otherwise the lowest
     * defined), with its value scaled to the requested tier.
     */
    @Nullable
    private T nearest(LootRarity requested) {
        Entry<LootRarity, T> best = null;
        Entry<LootRarity, T> lowest = null;

        for (Entry<LootRarity, T> entry : this.delegate.entrySet()) {
            LootRarity candidate = entry.getKey();
            if (candidate == null) continue;
            int ordinal = candidate.ordinal();

            if (ordinal <= requested.ordinal()
                && (best == null || ordinal > best.getKey().ordinal())) {
                best = entry;
            }
            if (lowest == null || ordinal < lowest.getKey().ordinal()) {
                lowest = entry;
            }
        }

        Entry<LootRarity, T> source = best != null ? best : lowest;
        if (source == null) return null;
        return scale(source.getValue(), source.getKey(), requested);
    }

    /**
     * Grows an inherited value to the requested tier.
     *
     * Without this, a tier that declares no value of its own would simply reuse
     * its neighbour's numbers — a Cosmic item would hit exactly as hard as an
     * Epic one. The multipliers come from Apotheosis' own gem scaling, so an
     * inherited bonus follows the same curve the game already uses.
     *
     * Only StepFunction values can be scaled meaningfully; anything else is
     * inherited unchanged.
     */
    @SuppressWarnings("unchecked")
    private T scale(T value, LootRarity from, LootRarity to) {
        if (!(value instanceof StepFunction fn)) return value;

        RarityTier fromTier = RarityTier.byRarity(from);
        RarityTier toTier = RarityTier.byRarity(to);
        if (fromTier == null || toTier == null) return value;

        float factor = toTier.getBonusMultiplier() / fromTier.getBonusMultiplier();
        if (Math.abs(factor - 1.0F) < 0.001F) return value;

        return (T) new StepFunction(fn.min() * factor, fn.steps(), fn.step() * factor);
    }
}
