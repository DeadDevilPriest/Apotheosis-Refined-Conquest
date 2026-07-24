package org.apotheosis_refined_conquest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.GemBonus;
import dev.shadowsoffire.placebo.util.StepFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;

import java.util.Map;
import java.util.Set;

/**
 * Grants extra Sophisticated Backpacks inventory slots, scaled to the backpack's
 * own tier.
 *
 *   bonus = round(tierBaseSlots * percent(rarity, level))
 *
 * so a leather backpack (27) and a netherite one (108) both gain a proportional
 * amount rather than the same flat block of slots.
 *
 * Deliberately carries NO Forge attribute: contributing a custom attribute made
 * Forge run its attribute-registry validation on world load, which crashed with
 * "Registry Object not present: forge:swim_speed". The value lives purely in the
 * affix, and BackpackCapacityBridge reads it straight off the item.
 *
 * Backpacks stay in the normal CHESTPLATE category (so they keep receiving all
 * regular armor affixes); canApplyTo additionally requires an actual backpack so
 * real chestplates never roll this.
 */
public class BackpackCapacityAffix extends Affix {

    public static final Codec<BackpackCapacityAffix> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            GemBonus.VALUES_CODEC.fieldOf("values").forGetter(a -> a.values),
            LootCategory.SET_CODEC.fieldOf("types").forGetter(a -> a.types))
        .apply(inst, BackpackCapacityAffix::new));

    protected final Map<LootRarity, StepFunction> values;
    protected final Set<LootCategory> types;

    public BackpackCapacityAffix(Map<LootRarity, StepFunction> values, Set<LootCategory> types) {
        super(AffixType.STAT);
        this.values = values;
        this.types = types;
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory category, LootRarity rarity) {
        return stack.getItem() instanceof BackpackItem
            && this.types.contains(category)
            && this.values.containsKey(rarity);
    }

    /** The percentage (0..1) this affix grants at the given rarity/level. */
    public float getPercent(LootRarity rarity, float level) {
        StepFunction fn = this.values.get(rarity);
        return fn == null ? 0.0F : fn.get(level);
    }

    /** Extra slots for a concrete backpack stack, rounded to a whole number. */
    public int getBonusSlots(ItemStack stack, LootRarity rarity, float level) {
        if (!(stack.getItem() instanceof BackpackItem backpack)) return 0;
        return Math.round(backpack.getNumberOfSlots() * getPercent(rarity, level));
    }

    @Override
    public MutableComponent getDescription(ItemStack stack, LootRarity rarity, float level) {
        return Component.translatable("affix.apotheosis_refined_conquest.backpack_capacity.desc",
                getBonusSlots(stack, rarity, level))
            .withStyle(ChatFormatting.BLUE);
    }

    @Override
    public Codec<? extends Affix> getCodec() {
        return CODEC;
    }
}
