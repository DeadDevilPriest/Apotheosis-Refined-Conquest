package org.apotheosis_refined_conquest;

import dev.shadowsoffire.apotheosis.adventure.loot.AffixLootEntry;
import dev.shadowsoffire.apotheosis.adventure.loot.AffixLootRegistry;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Right-click to receive one random item of this cache's category, rolled at
 * exactly the cache's rarity.
 *
 * Weapon/armor/tool caches draw their base item from Apotheosis' affix loot
 * entries filtered by LootCategory, so they automatically respect whatever the
 * pack has configured — including items added by other mods. Gem and backpack
 * caches are built directly because neither is part of that pool.
 */
public class LootCacheItem extends Item implements GeoItem {

    /** Controller that the right-click trigger targets. */
    public static final String OPEN_CONTROLLER = "open";

    /**
     * How long the crate is held before it yields its contents.
     * Must stay in step with OPEN_LENGTH in the animation generator (1.5s = 30
     * ticks): the lid finishes its travel exactly as the reward is handed over.
     */
    public static final int OPEN_TICKS = 30;

    /**
     * Share of the open spent reaching before the lid starts to move.
     *
     * Lives here because two renderers have to agree on it: CrateArmRenderer
     * ends the free hand's travel at this point, and CrateItemRenderer only
     * starts turning the lid from here on. Splitting it into two constants
     * would let the hand and the lid drift apart.
     */
    public static final float REACH_PHASE = 0.4F;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.crate.idle");
    private static final RawAnimation IDLE_HOT = RawAnimation.begin().thenLoop("animation.crate.idle_hot");

    private static final RawAnimation OPEN = RawAnimation.begin().thenPlay("animation.crate.open");
    private static final RawAnimation OPEN_WORN = RawAnimation.begin().thenPlay("animation.crate.open_worn");
    private static final RawAnimation OPEN_RICH = RawAnimation.begin().thenPlay("animation.crate.open_rich");

    private final RarityTier tier;
    private final CacheCategory category;
    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    public LootCacheItem(Properties properties, CacheCategory category, RarityTier tier) {
        super(properties);
        this.category = category;
        this.tier = tier;
    }

    public RarityTier getTier() {
        return this.tier;
    }

    public CacheCategory getCategory() {
        return this.category;
    }

    // === GeckoLib ============================================================

    /**
     * Two controllers on purpose: the idle loop must keep running undisturbed
     * while the one-shot opening animation plays over it.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0,
            state -> state.setAndContinue(idleAnimation())));

        controllers.add(new AnimationController<>(this, OPEN_CONTROLLER, 0,
            state -> PlayState.STOP).triggerableAnim("open", openAnimation()));
    }

    private RawAnimation idleAnimation() {
        // Mythic runs hot: faster pulse, spikes and horns shivering.
        return this.tier.getOrdinal() == 9 ? IDLE_HOT : IDLE;
    }

    private RawAnimation openAnimation() {
        int stage = this.tier.getOrdinal();
        if (stage == 0) return OPEN_WORN;      // groans, overshoots, settles back
        if (stage >= 3) return OPEN_RICH;      // straps, ribs, cross and skull react
        return OPEN;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animCache;
    }

    /**
     * Swaps in the GeckoLib renderer and the crate's own hand pose. Only ever
     * called on the client, so neither class is loaded on a dedicated server.
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new org.apotheosis_refined_conquest.client.CrateClientExtensions());
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(style -> style.withColor(this.tier.getColor()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.apotheosis_refined_conquest.cache.tooltip")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Gives the stack its GeckoLib id long before it is ever used.
     *
     * Assigning it at use time raced the animation trigger: until the tag exists
     * {@link GeoItem#getId} reports Long.MAX_VALUE, so the client was still
     * rendering that placeholder instance when the trigger arrived for the real
     * id — and the lid never moved. Writing the tag here means the client has
     * long since caught up by the time anyone right-clicks.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server) {
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(GeoItem.ID_NBT_KEY, Tag.TAG_ANY_NUMERIC)) {
                GeoItem.getOrAssignId(stack, server);
            }
        }
    }

    /**
     * An NBT-only change must not make the hand dip and re-raise. The GeckoLib id
     * is written into the stack, and vanilla would otherwise read that as a
     * different item and play the equip animation over ours.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    /**
     * Right-click STARTS opening the crate; the contents arrive in
     * {@link #finishUsingItem}. Consuming the item immediately would destroy it
     * in the same tick the opening animation begins, so nothing would ever be
     * seen of it.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            // Fires the ornament clip (latch, straps, cross flash, …). The lid
            // itself is driven separately by CrateItemRenderer from hold progress.
            long id = GeoItem.getOrAssignId(held, server);
            triggerAnim(player, id, OPEN_CONTROLLER, "open");
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(held);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return OPEN_TICKS;
    }

    /** NONE keeps vanilla's hand motion out of the way of the crate's own animation. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof Player player)) return stack;
        openCrate(level, player, stack);
        return stack;
    }

    private void openCrate(Level level, Player player, ItemStack held) {
        LootRarity rarity = this.tier.resolve();
        if (rarity == null) {
            player.displayClientMessage(
                Component.translatable("message.apotheosis_refined_conquest.rarity_unavailable", this.tier.getId()), true);
            return;
        }

        ItemStack reward = switch (this.category) {
            case GEM -> rollGem(rarity, player);
            case BACKPACK -> rollBackpack(rarity, player);
            default -> rollLoot(rarity, player);
        };

        if (reward.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.apotheosis_refined_conquest.cache_empty",
                    this.category.getId(), this.tier.getId()), true);
            return;
        }

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /**
     * Weapon / armor / tool: pull a base item from Apotheosis' loot pool.
     *
     * Entries carry their own rarity clamp (typically common..mythic), so the
     * tiers outside that span — Worn below it, Ancient and up above it — match
     * nothing and the cache would come back empty. Those tiers therefore fall
     * back to the full category pool: the cache was crafted for a specific
     * rarity, so it must deliver one.
     */
    private ItemStack rollLoot(LootRarity rarity, Player player) {
        List<AffixLootEntry> inClamp = new ArrayList<>();
        List<AffixLootEntry> anyOfCategory = new ArrayList<>();
        for (AffixLootEntry entry : AffixLootRegistry.INSTANCE.getValues()) {
            try {
                if (!this.category.matches(entry.getType())) continue;
                anyOfCategory.add(entry);
                if (!rarity.isAtLeast(entry.getMinRarity())) continue;
                if (!entry.getMaxRarity().isAtLeast(rarity)) continue;
                inClamp.add(entry);
            } catch (Throwable ignored) {
                // A malformed entry must not break the cache.
            }
        }
        List<AffixLootEntry> candidates = inClamp.isEmpty() ? anyOfCategory : inClamp;
        if (candidates.isEmpty()) return ItemStack.EMPTY;

        AffixLootEntry chosen = candidates.get(player.getRandom().nextInt(candidates.size()));
        ItemStack base = chosen.getStack().copy();
        return LootController.createLootItem(base, rarity, player.getRandom());
    }

    /** Backpacks are not in the loot pool — affix a plain backpack directly. */
    private ItemStack rollBackpack(LootRarity rarity, Player player) {
        Item backpack = ForgeRegistries.ITEMS.getValue(
            new net.minecraft.resources.ResourceLocation("sophisticatedbackpacks", "backpack"));
        if (backpack == null) return ItemStack.EMPTY;
        return LootController.createLootItem(new ItemStack(backpack), rarity, player.getRandom());
    }

    /** A random gem that legitimately supports this rarity, built at that rarity. */
    private ItemStack rollGem(LootRarity rarity, Player player) {
        List<Gem> inClamp = new ArrayList<>();
        List<Gem> all = new ArrayList<>();
        for (Gem gem : GemRegistry.INSTANCE.getValues()) {
            try {
                all.add(gem);
                if (rarity.isAtLeast(gem.getMinRarity()) && gem.getMaxRarity().isAtLeast(rarity)) {
                    inClamp.add(gem);
                }
            } catch (Throwable ignored) {
                // A malformed gem definition must not break the cache.
            }
        }
        // Same reasoning as rollLoot: outside the declared span, use the full pool.
        List<Gem> candidates = inClamp.isEmpty() ? all : inClamp;
        if (candidates.isEmpty()) return ItemStack.EMPTY;

        Gem chosen = candidates.get(player.getRandom().nextInt(candidates.size()));
        return GemRegistry.createGemStack(chosen, rarity);
    }
}
