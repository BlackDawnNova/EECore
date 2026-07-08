package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Interface for any block that can be a multiblock structural part.
 * Addon mods implement this on their BlockEntity to integrate with EECore multiblocks.
 * 多方块部件接口，附属 mod 在 BE 上实现以接入 EECore 多方块系统。
 */
public interface IPart {

    /**
     * What this part can do. / 此部件的能力。
     */
    Set<PartAbility> getAbilities();

    /**
     * Called when the multiblock forms. / 多方块成形时调用。
     */
    void onFormed(ResourceLocation machineId, BlockPos controllerPos);

    /**
     * Called when the multiblock breaks. / 多方块破碎时调用。
     */
    void onBroken();

    /**
     * Get the controller position, or null if not formed. / 控制器位置，未成形时返回 null。
     */
    BlockPos getControllerPos();

    /**
     * Get the machine ID, or null if not formed. / 机器 ID，未成形时返回 null。
     */
    ResourceLocation getMachineId();

    /**
     * Whether this part is currently part of a formed multiblock. / 是否已成形。
     */
    boolean isFormed();
}
