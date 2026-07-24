package org.apotheosis_refined_conquest;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TrifectaNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(TrifectaAffixes.MODID, "main"),
        () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void init() {
        CHANNEL.registerMessage(0, FusionSelectionPacket.class,
            FusionSelectionPacket::encode, FusionSelectionPacket::decode, FusionSelectionPacket::handle);
        CHANNEL.registerMessage(1, FusionPanelClickPacket.class,
            FusionPanelClickPacket::encode, FusionPanelClickPacket::decode, FusionPanelClickPacket::handle);
    }

    private TrifectaNetwork() {}
}
