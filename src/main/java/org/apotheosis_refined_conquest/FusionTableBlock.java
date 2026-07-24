package org.apotheosis_refined_conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public class FusionTableBlock extends Block {

    private static final Component TITLE = Component.translatable("container.apotheosis_refined_conquest.fusion_table");

    public FusionTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        NetworkHooks.openScreen((ServerPlayer) player,
            new SimpleMenuProvider((id, inv, p) -> new FusionTableMenu(id, inv, ContainerLevelAccess.create(level, pos)), TITLE));
        return InteractionResult.CONSUME;
    }
}
