package com.endlessepoch.core.api.multiblock.loader;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.EECoreCodec;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/**
 * Builder API for registering multiblock machines from .ecs structure files.
 * Loads .ecs from classpath or config dir, applies .where() tag bindings,
 * registers the pattern and binds it to the controller block.
 * <p>
 * 从 .ecs 结构文件注册多方块机器的构建器 API。
 * 从 classpath 或 config 目录加载 .ecs，应用 .where() 标记绑定，
 * 注册模式并绑定到对应的控制器方块。
 */
public final class MultiblockLoader {
    private final ResourceLocation ecsFile;
    private final Map<String, List<Block>> tagDefinitions = new LinkedHashMap<>();
    private String lastTag;
    private MultiblockLoader(ResourceLocation ecsFile) { this.ecsFile = ecsFile; }
    public static MultiblockLoader load(ResourceLocation ecsFile) { return new MultiblockLoader(ecsFile); }
    public MultiblockLoader where(String tag, Block... blocks) { lastTag = tag; tagDefinitions.computeIfAbsent(tag,k->new ArrayList<>()).addAll(Arrays.asList(blocks)); return this; }
    public MultiblockLoader or(Block... blocks) { if(lastTag!=null)tagDefinitions.get(lastTag).addAll(Arrays.asList(blocks)); return this; }
    
    public void register(ResourceLocation id) {
        MultiBlockPattern pattern = null;
        String cp = "/data/"+ecsFile.getNamespace()+"/structures/"+ecsFile.getPath()+".ecs";
        try(InputStream is = getClass().getResourceAsStream(cp)) { if(is!=null)pattern=EECoreCodec.decode(is.readAllBytes()); } catch(Exception ignored){}
        if(pattern==null) { try { Path p=Path.of("config","eecore","structures",ecsFile.getNamespace(),ecsFile.getPath()+".ecs"); if(Files.exists(p))pattern=EECoreCodec.read(p); } catch(Exception ignored){} }
        if(pattern==null) { EECore.LOGGER.error(".ecs not found: {}",ecsFile); return; }
        for(var e:tagDefinitions.entrySet())for(Block b:e.getValue())for(char c:pattern.getDefinitions().keySet())if(pattern.getTags(c).contains(e.getKey()))pattern.addAlternatives(c,b.defaultBlockState());
        MultiBlockRegistry.registerMod(id,pattern);
        var ctrl=pattern.getDefinitions().get('K'); if(ctrl!=null)MultiBlockRegistry.bindControllerToPattern(ctrl.getBlock(),id);
        EECore.LOGGER.info("Registered multiblock: {}",id);
    }
}
