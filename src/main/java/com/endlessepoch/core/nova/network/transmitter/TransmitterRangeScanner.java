package com.endlessepoch.core.nova.network.transmitter;

import com.endlessepoch.core.api.energy.EnergyPacket;
import com.endlessepoch.core.api.energy.IOmegaEnergyStorage;
import com.endlessepoch.core.api.energy.OmegaValue;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.math.BigInteger;

/**
 * Scans for generators in range and pulls energy into the transmitter's buffer.
 * <p>
 * 在范围内扫描发电机并将能量拉入发射器的缓冲区。
 */
public class TransmitterRangeScanner {

    /** Default alpha for inverse-square attenuation formula. / 逆平方衰减公式的默认 alpha 值。 */
    private static double attenuationAlpha = 0.0005;

    /**
     * Set the attenuation coefficient. Higher = faster drop-off with distance.
     * 设置衰减系数。值越大，距离衰减越快。
     */
    public static void setAttenuationAlpha(double alpha) {
        attenuationAlpha = Math.max(0, alpha);
    }

    public static double getAttenuationAlpha() {
        return attenuationAlpha;
    }

    /**
     * Calculate transmission efficiency for a given distance.
     * <p>
     * 计算给定距离的传输效率。
     * Formula: 1 / (1 + alpha x distance^2)
     * 公式：1 / (1 + alpha x distance^2)
     */
    public static double getEfficiency(double distance) {
        return 1.0 / (1.0 + attenuationAlpha * distance * distance);
    }

    /**
     * Scan adjacent blocks for machines implementing IOmegaEnergyStorage
     * and pull energy into the buffer.
     * <p>
     * 扫描相邻方块中实现了 IOmegaEnergyStorage 的机器，并将能量拉入缓冲区。
     *
     * @param level    the world / 世界
     * @param center   center block position / 中心方块位置
     * @param range    scan radius in blocks / 扫描半径（方块）
     * @param tier     transmitter voltage tier / 发射器电压等级
     * @param buffer   buffer to fill / 需要填充的缓冲区
     * @param teamId   team filter (null = accept all) / 队伍过滤器（null = 接受所有）
     * @return total energy pulled this tick (Omega as BigInteger) / 此 tick 拉取的总能量（BigInteger 表示的 Omega）
     */
    public static BigInteger scanAndPull(Level level, BlockPos center, int range,
                                          VoltageTier tier, TransmitterEnergyBuffer buffer,
                                          java.util.UUID teamId) {
        BigInteger totalPulled = BigInteger.ZERO;
        if (buffer.isFull()) return totalPulled;

        int range2 = range * range;
        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx * dx + dy * dy + dz * dz > range2) continue;

                    scanPos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);

                    if (!level.isLoaded(scanPos)) continue;

                    var be = level.getBlockEntity(scanPos);

                    IOmegaEnergyStorage gen = null;
                    if (be instanceof IOmegaEnergyStorage directGen) {
                        gen = directGen;
                    }

                    if (gen == null || !gen.getEnergyStored().isZero()) {
                        if (gen != null && !gen.getEnergyStored().isZero()) {
                            EnergyPacket extracted = gen.extractPacket(gen.getTier(), true);
                            if (extracted != null && !extracted.isEmpty()) {
                                extracted = gen.extractPacket(gen.getTier(), false);
                                if (extracted != null) {
                                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                    double efficiency = getEfficiency(distance);
                                    BigInteger attenuated = extracted.getEnergy().toBigInteger()
                                            .multiply(BigInteger.valueOf((long) (efficiency * 100)))
                                            .divide(BigInteger.valueOf(100));
                                    OmegaValue toStore = OmegaValue.of(attenuated);
                                    if (!toStore.isZero()) {
                                        OmegaValue accepted = buffer.receive(
                                                new EnergyPacket(extracted.getTier(),
                                                        extracted.getAmperage(), toStore),
                                                false);
                                        totalPulled = totalPulled.add(accepted.toBigInteger());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return totalPulled;
    }
}
