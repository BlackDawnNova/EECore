package com.endlessepoch.core.nova.network.laser;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side laser rendering configuration.
 * All fields are client-only — energy data transmission is unaffected.
 */
@OnlyIn(Dist.CLIENT)
public final class LaserRenderConfig {

    private static boolean enabled = true;
    private static int maxRenderDistance = 256;
    private static int particleDensity = 50; // 0-100%

    private LaserRenderConfig() {}

    /** Whether lasers are rendered at all. Disable on low-end PCs. */
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean enabled) { LaserRenderConfig.enabled = enabled; }

    /** Maximum distance (blocks) at which lasers are rendered. */
    public static int getMaxRenderDistance() { return maxRenderDistance; }
    public static void setMaxRenderDistance(int distance) {
        maxRenderDistance = Math.max(16, Math.min(512, distance));
    }

    /** Particle density 0-100%. 0 = no particles, 100 = full. */
    public static int getParticleDensity() { return particleDensity; }
    public static void setParticleDensity(int density) {
        particleDensity = Math.max(0, Math.min(100, density));
    }
}
