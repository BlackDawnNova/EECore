package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.api.multiblock.EECoreCodec;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.OpenMbVisPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** BlockItem carrying a machine ID, written to BE on placement. Model index allocated by MachineControllerBlock. / 携带机器ID的控制器物品，放置时写入BE。模型索引由 MachineControllerBlock 分配。 */
public class MachineControllerItem extends BlockItem {

    private final ResourceLocation machineId;
    private final String nameEn, nameZh;
    private final int modelIndex;

    public MachineControllerItem(Block block, Properties properties, ResourceLocation machineId, String nameEn, String nameZh, int modelIndex) {
        super(block, properties);
        this.machineId = machineId;
        this.nameEn = nameEn;
        this.nameZh = nameZh;
        this.modelIndex = modelIndex;
    }

    @Override
    public Component getName(ItemStack stack) {
        String lang = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        return Component.literal(lang != null && lang.toLowerCase().contains("zh") ? nameZh : nameEn);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && machineId != null) {
            var opt = MultiBlockRegistry.get(machineId);
            if (opt.isPresent()) sendPreview((ServerPlayer) player, opt.get());
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private void sendPreview(ServerPlayer player, com.endlessepoch.core.api.multiblock.MultiBlockPattern pat) {
        try {
            byte[] bytes = EECoreCodec.encode(pat);
            Map<String, List<String>> alts = new LinkedHashMap<>();
            for (var e : pat.getDefinitions().entrySet()) {
                var set = pat.getAlternatives(e.getKey());
                if (set.size() <= 1) continue;
                List<String> ids = new ArrayList<>();
                for (BlockState bs : set)
                    if (!bs.equals(e.getValue()))
                        ids.add(BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString());
                if (!ids.isEmpty()) alts.put(String.valueOf(e.getKey()), ids);
            }
            PacketDistributor.sendToPlayer(player, new OpenMbVisPacket(machineId, true, bytes, alts));
        } catch (Exception ignored) {}
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        BlockState state = getBlock().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(MachineControllerBlock.MODEL, modelIndex);

        level.setBlock(pos, state, 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineControllerBlockEntity mc) {
            mc.setMachineId(machineId);
            if (ctx.getPlayer() != null)
                mc.stampOwner(ctx.getPlayer().getUUID(), ctx.getPlayer().getName().getString());
        }

        if (ctx.getPlayer() != null && !ctx.getPlayer().isCreative())
            ctx.getItemInHand().shrink(1);

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
