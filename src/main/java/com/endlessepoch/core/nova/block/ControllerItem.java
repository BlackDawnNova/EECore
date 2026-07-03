package com.endlessepoch.core.nova.block;
/** Sends structure preview to client when right-clicking air. / 右键空气发送结构预览到客户端。 */

import com.endlessepoch.core.api.multiblock.EECoreCodec;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.OpenMbVisPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public class ControllerItem extends BlockItem {
    public ControllerItem(ScannerControllerBlock b, Properties p) { super(b,p); }
    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        if(!l.isClientSide()) {
            var id=MultiBlockRegistry.getPatternForController(this.getBlock());
            if(id.isPresent()) sendPattern((ServerPlayer)p,id.get());
        }
        return InteractionResultHolder.success(p.getItemInHand(h));
    }
    private static void sendPattern(ServerPlayer p, ResourceLocation id) {
        var opt=MultiBlockRegistry.get(id); if(opt.isEmpty())return;
        var pat=opt.get();
        try {
            byte[] bytes=EECoreCodec.encode(pat);
            Map<String,List<String>> alts=new LinkedHashMap<>();
            for(var e:pat.getDefinitions().entrySet()) {
                char c=e.getKey(); var set=pat.getAlternatives(c);
                if(set.size()<=1)continue;
                List<String> ids=new ArrayList<>();
                for(BlockState bs:set)if(!bs.equals(e.getValue()))ids.add(BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString());
                if(!ids.isEmpty())alts.put(String.valueOf(c),ids);
            }
            PacketDistributor.sendToPlayer(p,new OpenMbVisPacket(id,true,bytes,alts));
        } catch(Exception ignored){}
    }
}
