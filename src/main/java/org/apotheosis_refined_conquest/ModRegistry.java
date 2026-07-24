package org.apotheosis_refined_conquest;

import net.minecraft.core.registries.Registries;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TrifectaAffixes.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TrifectaAffixes.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, TrifectaAffixes.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TrifectaAffixes.MODID);
    // NOTE: the backpack_capacity attribute is intentionally NOT registered right
    // now. Contributing a custom attribute makes Forge run its attribute-registry
    // validation during world load, which crashed with
    // "Registry Object not present: forge:swim_speed". Re-introduce only after
    // that interaction is understood and verified.

    public static final RegistryObject<Block> FUSION_TABLE = BLOCKS.register("fusion_table",
        () -> new FusionTableBlock(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.WOOD)));

    public static final RegistryObject<Item> FUSION_TABLE_ITEM = ITEMS.register("fusion_table",
        () -> new BlockItem(FUSION_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<MenuType<FusionTableMenu>> FUSION_MENU = MENUS.register("fusion_table",
        () -> IForgeMenuType.create((windowId, inv, data) -> new FusionTableMenu(windowId, inv)));

    /** Crafting material for the eight tiers this mod introduces. */
    public static final Map<RarityTier, RegistryObject<Item>> MATERIALS = new EnumMap<>(RarityTier.class);

    /** Right-click caches: one per category per rarity tier. */
    public static final Map<CacheCategory, Map<RarityTier, RegistryObject<Item>>> CACHES =
        new EnumMap<>(CacheCategory.class);

    static {
        for (RarityTier tier : RarityTier.values()) {
            if (tier.isCustom()) {
                MATERIALS.put(tier, ITEMS.register(tier.getId() + "_material",
                    () -> new RarityMaterialItem(new Item.Properties(), tier)));
            }
        }
        for (CacheCategory category : CacheCategory.values()) {
            Map<RarityTier, RegistryObject<Item>> byTier = new EnumMap<>(RarityTier.class);
            for (RarityTier tier : RarityTier.values()) {
                byTier.put(tier, ITEMS.register(category.getId() + "_cache_" + tier.getId(),
                    () -> new LootCacheItem(new Item.Properties().stacksTo(16), category, tier)));
            }
            CACHES.put(category, byTier);
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.apotheosis_refined_conquest"))
            .icon(() -> FUSION_TABLE_ITEM.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(FUSION_TABLE_ITEM.get());
                for (RarityTier tier : RarityTier.values()) {
                    RegistryObject<Item> material = MATERIALS.get(tier);
                    if (material != null) output.accept(material.get());
                }
                for (CacheCategory category : CacheCategory.values()) {
                    for (RarityTier tier : RarityTier.values()) {
                        output.accept(CACHES.get(category).get(tier).get());
                    }
                }
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }

    private ModRegistry() {}
}
