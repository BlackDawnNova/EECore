package com.endlessepoch.core.api;

import com.endlessepoch.core.api.energy.IOmegaEnergyStorage;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * EECore capability definitions for external mod integration.
 * <p>
 * Other mods query Ω energy storage through this capability instead of hard-dependency on EECore classes.
 * <p>
 * EECore 能力定义，用于外部模组集成。
 * <p>
 * 其他模组通过此能力查询 Ω 能量存储，而无需硬依赖 EECore 类。
 *
 * <pre>{@code
 * // Get a neighbor's Ω energy storage
 * var cap = level.getCapability(EECoreCapabilities.OMEGA_ENERGY, pos, side);
 * if (cap != null) cap.receivePacket(packet, false);
 * }</pre>
 */
public class EECoreCapabilities {

    /**
     * Ω energy storage capability key.
     * Any block entity implementing {@link IOmegaEnergyStorage} and registering this capability
     * becomes accessible to other mods without compile-time dependency.
     * <p>
     * Ω 能量存储能力键。
     * 任何实现了 {@link IOmegaEnergyStorage} 并注册了此能力的方块实体，
     * 无需编译时依赖即可被其他模组访问。
     */
    public static final BlockCapability<IOmegaEnergyStorage, Direction> OMEGA_ENERGY =
            BlockCapability.create(
                    ResourceLocation.fromNamespaceAndPath("eecore", "omega_energy"),
                    IOmegaEnergyStorage.class,
                    Direction.class
            );

    private EECoreCapabilities() {}
}
