package com.endlessepoch.core.nova.network.laser;

import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.nova.network.transmitter.TransmitterRangeScanner;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Data record for a laser link between a transmitter and a receiver.
 */
public final class LaserConnection {

    private final UUID id;
    private final UUID transmitterId;
    private final UUID receiverId;
    private final BlockPos transmitterPos;
    private final BlockPos receiverPos;
    private final VoltageTier tier;
    private final double distance;
    private final double efficiency;
    private volatile double currentPower; // Ω/t flowing currently

    public LaserConnection(UUID transmitterId, UUID receiverId,
                           BlockPos transmitterPos, BlockPos receiverPos,
                           VoltageTier tier) {
        this.id = UUID.randomUUID();
        this.transmitterId = transmitterId;
        this.receiverId = receiverId;
        this.transmitterPos = transmitterPos;
        this.receiverPos = receiverPos;
        this.tier = tier;
        this.distance = Math.sqrt(transmitterPos.distSqr(receiverPos));
        this.efficiency = TransmitterRangeScanner.getEfficiency(this.distance);
    }

    // ===== Getters =====
    public UUID getId() { return id; }
    public UUID getTransmitterId() { return transmitterId; }
    public UUID getReceiverId() { return receiverId; }
    public BlockPos getTransmitterPos() { return transmitterPos; }
    public BlockPos getReceiverPos() { return receiverPos; }
    public VoltageTier getTier() { return tier; }
    public double getDistance() { return distance; }

    /** Transmission efficiency (0.0 ~ 1.0). */
    public double getEfficiency() { return efficiency; }

    /** Current power flowing (Ω/t). */
    public double getCurrentPower() { return currentPower; }
    public void setCurrentPower(double power) { this.currentPower = power; }

    /** Whether energy is actively flowing. */
    public boolean isActive() { return currentPower > 0.01; }
}
