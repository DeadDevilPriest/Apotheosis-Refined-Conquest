package org.apotheosis_refined_conquest;

import com.mojang.logging.LogUtils;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TrifectaAffixes.MODID)
public class TrifectaAffixes {

    public static final String MODID = "apotheosis_refined_conquest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final boolean BACKPACKS_LOADED = ModList.get() != null
        && ModList.get().isLoaded("sophisticatedbackpacks");

    public TrifectaAffixes() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TrifectaConfig.SPEC);

        ModRegistry.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(new CraftRollHandler());

        // Backpack capacity integration is optional — only if SB is present.
        // It carries no custom Forge attribute (that crashed world load) and only
        // resizes EMPTY backpacks (resizing populated ones duplicated items).
        if (BACKPACKS_LOADED) {
            // No event handlers needed: BackpackWrapperMixin makes SB itself
            // include the capacity bonus wherever it resolves the slot count.
            LOGGER.info("Sophisticated Backpacks detected — capacity affix enabled");
        }

        LOGGER.info("Trifecta Affixes loaded");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TrifectaNetwork.init();

            // Required before triggerAnim can reach a crate: GeckoLib needs to know
            // which animatables may receive server-side animation triggers.
            int synced = 0;
            for (CacheCategory category : CacheCategory.values()) {
                for (RarityTier tier : RarityTier.values()) {
                    var entry = ModRegistry.CACHES.get(category).get(tier);
                    if (entry != null && entry.isPresent()) {
                        software.bernie.geckolib.animatable.GeoItem
                            .registerSyncedAnimatable((LootCacheItem) entry.get());
                        synced++;
                    }
                }
            }
            LOGGER.info("[Crates] {} caches registered for animation sync", synced);

            if (BACKPACKS_LOADED) {
                AffixRegistry.INSTANCE.registerCodec(
                    new ResourceLocation(MODID, "backpack_capacity"), BackpackCapacityAffix.CODEC);
                LOGGER.info("[Backpack] registered affix codec {}:backpack_capacity", MODID);
            }
        });
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModRegistry.FUSION_MENU.get(), org.apotheosis_refined_conquest.client.FusionTableScreen::new);

            // Holds the arm still while a crate is being opened.
            MinecraftForge.EVENT_BUS.register(new org.apotheosis_refined_conquest.client.CrateSwingSuppressor());

            // Vanilla draws no arm for a held item, so the crate's arms come from us.
            MinecraftForge.EVENT_BUS.register(new org.apotheosis_refined_conquest.client.CrateArmRenderer());
        });
    }
}
