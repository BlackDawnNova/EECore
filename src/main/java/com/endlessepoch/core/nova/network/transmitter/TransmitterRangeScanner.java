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
 */
public class TransmitterRangeScanner {

    /** Default alpha for inverse-square attenuation formula. */
    private static double attenuationAlpha = 0.0005;

    /**
     * Set the attenuation coefficient. Higher = faster drop-off with distance.
     */
    public static void setAttenuationAlpha(double alpha) {
        attenuationAlpha = Math.max(0, alpha);
    }

    public static double getAttenuationAlpha() {
        return attenuationAlpha;
    }

    /**
     * Calculate transmission efficiency for a given distance.
     * Formula: 1 / (1 + α × distance²)
     */
    public static double getEfficiency(double distance) {
        return 1.0 / (1.0 + attenuationAlpha * distance * distance);
    }

    /**
     * Scan adjacent blocks for machines implementing IOmegaEnergyStorage
     * and pull energy into the buffer.
     *
     * @param level    the world
     * @param center   center block position
     * @param range    scan radius in blocks
     * @param tier     transmitter voltage tier
     * @param buffer   buffer to fill
     * @param teamId   team filter (null = accept all)
     * @return total energy pulled this tick (Ω as BigInteger)
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
