package org.apotheosis_refined_conquest;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: player clicked one of the armor stand panels to
 * insert/extract the carried item.
 */
public record FusionPanelClickPacket(int slotIndex) {

    public static void encode(FusionPanelClickPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slotIndex);
    }

    public static FusionPanelClickPacket decode(FriendlyByteBuf buf) {
        return new FusionPanelClickPacket(buf.readVarInt());
    }

    public static void handle(FusionPanelClickPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof FusionTableMenu menu) {
                menu.panelClicked(msg.slotIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
