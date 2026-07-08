package com.endlessepoch.core.nova.network.receiver;

import com.endlessepoch.core.api.energy.EnergyPacket;
import com.endlessepoch.core.api.energy.IOmegaEnergyStorage;
import com.endlessepoch.core.api.energy.OmegaValue;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.nova.network.transmitter.TransmitterEnergyBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Distributes energy from a receiver's buffer to nearby machines.
 * <p>
 * 将接收器缓冲区中的能量分发到附近的机器。
 */
public final class ReceiverDistributor {

    private ReceiverDistributor() {}

    /**
     * Push energy from the buffer to nearby machines implementing IOmegaEnergyStorage.
     * 将缓冲区中的能量推送到附近实现了 IOmegaEnergyStorage 的机器。
     */
    public static void distribute(Level level, BlockPos center, int range,
                                   VoltageTier tier, TransmitterEnergyBuffer buffer) {
        if (!buffer.hasEnergy()) return;

        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
        int range2 = range * range;

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx * dx + dy * dy + dz * dz > range2) continue;
                    if (!buffer.hasEnergy()) return;

                    scanPos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(scanPos)) continue;

                    var be = level.getBlockEntity(scanPos);
                    IOmegaEnergyStorage target = null;
                    if (be instanceof IOmegaEnergyStorage directStore) {
                        target = directStore;
                    }
                    if (target == null) continue;
                    if (target.getMaxInput().isZero()) continue;

                    EnergyPacket toSend = buffer.extract(tier, true);
                    if (toSend == null || toSend.isEmpty()) continue;

                    toSend = buffer.extract(tier, false);
                    if (toSend == null) continue;

                    EnergyPacket accepted = target.receivePacket(toSend, false);
                    if (accepted == null || accepted.isEmpty()) {
                        buffer.receive(toSend, false);
                    }
                }
            }
        }
    }
}
