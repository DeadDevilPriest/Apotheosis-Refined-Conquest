package org.apotheosis_refined_conquest;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Client -> Server: the player's affix selection in the fusion table GUI.
 */
public record FusionSelectionPacket(Set<ResourceLocation> transfers, Set<ResourceLocation> removals, boolean upgrade) {

    public static void encode(FusionSelectionPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.transfers, FriendlyByteBuf::writeResourceLocation);
        buf.writeCollection(msg.removals, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(msg.upgrade);
    }

    public static FusionSelectionPacket decode(FriendlyByteBuf buf) {
        Set<ResourceLocation> transfers = buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation);
        Set<ResourceLocation> removals = buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation);
        boolean upgrade = buf.readBoolean();
        return new FusionSelectionPacket(transfers, removals, upgrade);
    }

    public static void handle(FusionSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof FusionTableMenu menu) {
                menu.updateSelection(msg.transfers, msg.removals, msg.upgrade);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
