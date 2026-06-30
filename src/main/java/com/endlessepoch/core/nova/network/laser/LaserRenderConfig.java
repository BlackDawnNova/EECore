package com.endlessepoch.core.nova.network.laser;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side laser rendering configuration.
 * All fields are client-only — energy data transmission is unaffected.
 * <p>
 * 客户端激光渲染配置。
 * 所有字段均为客户端专属 —— 不影响能量数据传输。
 */
@OnlyIn(Dist.CLIENT)
public final class LaserRenderConfig {

    private static boolean enabled = true;
    private static int maxRenderDistance = 256;
    private static int particleDensity = 50;

    private LaserRenderConfig() {}

    /** Whether lasers are rendered at all. Disable on low-end PCs. / 是否渲染激光。可在低配置电脑上禁用。 */
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean enabled) { LaserRenderConfig.enabled = enabled; }

    /** Maximum distance (blocks) at which lasers are rendered. / 激光渲染的最大距离（方块）。 */
    public static int getMaxRenderDistance() { return maxRenderDistance; }
    public static void setMaxRenderDistance(int distance) {
        maxRenderDistance = Math.max(16, Math.min(512, distance));
    }

    /** Particle density 0-100%. 0 = no particles, 100 = full. / 粒子密度 0-100%。0 = 无粒子，100 = 满密度。 */
    public static int getParticleDensity() { return particleDensity; }
    public static void setParticleDensity(int density) {
        particleDensity = Math.max(0, Math.min(100, density));
    }
}
