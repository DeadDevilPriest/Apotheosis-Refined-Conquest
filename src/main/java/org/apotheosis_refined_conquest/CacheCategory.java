package org.apotheosis_refined_conquest;

import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;

import java.util.function.Predicate;

/**
 * The kinds of cache this mod offers. Each one draws from Apotheosis' own
 * affix loot entries, filtered to the matching LootCategory, so the pool always
 * reflects whatever the pack has configured rather than a hard-coded item list.
 *
 * BACKPACK is special: backpacks are not part of the affix loot pool, so that
 * cache builds a backpack directly (see LootCacheItem).
 */
public enum CacheCategory {

    WEAPON("weapon", cat -> cat.isWeapon() || cat.isRanged()),
    ARMOR("armor", LootCategory::isArmor),
    TOOL("tool", LootCategory::isBreaker),
    BACKPACK("backpack", cat -> false),
    GEM("gem", cat -> false);

    private final String id;
    private final Predicate<LootCategory> filter;

    CacheCategory(String id, Predicate<LootCategory> filter) {
        this.id = id;
        this.filter = filter;
    }

    public String getId() {
        return this.id;
    }

    /** Whether an affix loot entry of this LootCategory belongs in this cache. */
    public boolean matches(LootCategory category) {
        return category != null && !category.isNone() && this.filter.test(category);
    }

    /** These do not draw from the affix loot pool. */
    public boolean isSpecial() {
        return this == BACKPACK || this == GEM;
    }
}
