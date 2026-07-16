package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Per-machine heat state with per-profile slots and lazy cooling.
 * Each machine profile (furnace, blast_furnace, etc.) has an independent heat slot.
 * Heat is gained per recipe completion ({@link Config#heatUpRate}) and decays
 * during idle ticks ({@link Config#coolDownRate}). Switching profiles retains
 * only a fraction of the heat ({@link Config#heatSwitchDecay}).
 * <p>
 * 单台机器热量状态，每个 profile 独立热量槽。惰性冷却——访问时计算衰减。
 */
public class HeatComponent {

    /** Heat per profile. Key = profile ResourceLocation, Value = current heat. / 每个 profile 的热量 */
    private final Map<ResourceLocation, Double> heatSlots = new ConcurrentHashMap<>();

    /** Last tick the machine was actively processing (any profile). / 机器最后活跃的 tick */
    private long lastActiveTick;

    /** Currently active profile, or null if idle. For NBT only, not used in logic. / 当前活跃配方 */
    private ResourceLocation activeProfile;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // ── Read ──

    /**
     * Get current heat for a profile, applying lazy cooling.
     * Does NOT mutate the heat slot — pure read.
     * <p>
     * 获取指定 profile 的当前热量（含惰性冷却衰减），纯读取不修改。
     */
    public double getHeat(ResourceLocation profileId, long currentTick) {
        lock.readLock().lock();
        try {
            Double stored = heatSlots.get(profileId);
            if (stored == null || stored <= 0) return 0.0;
            long elapsed = currentTick - lastActiveTick;
            if (elapsed <= 0 || Config.coolDownRate <= 0) return stored;
            return Math.max(0.0, stored - elapsed * Config.coolDownRate);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Get stored heat without applying cooling. For GUI display. / 不带冷却的原始热量（GUI显示用） */
    public double getHeatRaw(ResourceLocation profileId) {
        lock.readLock().lock();
        try { return heatSlots.getOrDefault(profileId, 0.0); }
        finally { lock.readLock().unlock(); }
    }

    /** Returns the active profile (for NBT). / 当前活跃配方 ID */
    public ResourceLocation getActiveProfile() {
        lock.readLock().lock();
        try { return activeProfile; }
        finally { lock.readLock().unlock(); }
    }

    // ── Write ──

    /**
     * Apply heat gain for one recipe completion, then cap at maxHeat.
     * Cools only for idle time BEFORE recipe start, not during processing.
     * <p>
     * 完成一次配方后的热量结算：仅冷却配方启动前的闲置时间，加工期间不算冷却。
     */
    public void bulkHeat(ResourceLocation profileId, double maxHeat, long recipeStartTick, long completionTick) {
        lock.writeLock().lock();
        try {
            // Lazy cooling: only for idle time BEFORE recipe started / 只冷却配方启动前的闲置
            double current = heatSlots.getOrDefault(profileId, 0.0);
            long idleElapsed = recipeStartTick - lastActiveTick;
            if (idleElapsed > 0 && Config.coolDownRate > 0 && current > 0) {
                current = Math.max(0.0, current - idleElapsed * Config.coolDownRate);
            }

            // Switch decay if profile changed / 切换配方衰减
            if (activeProfile != null && !activeProfile.equals(profileId) && current > 0) {
                current *= Config.heatSwitchDecay;
            }

            // Gain heat for this recipe completion / 完成一次配方涨一次热量
            current += Config.heatUpRate;
            current = Math.min(maxHeat, current);

            heatSlots.put(profileId, current);
            activeProfile = profileId;
            lastActiveTick = completionTick;

            // Clean up tiny values / 清理极低值
            if (current < 0.001) heatSlots.remove(profileId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Reset all heat slots. Called when multiblock breaks. / 破坏时清零所有热量。 */
    public void reset() {
        lock.writeLock().lock();
        try {
            heatSlots.clear();
            lastActiveTick = 0;
            activeProfile = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── NBT ──

    public void saveToNBT(CompoundTag tag) {
        lock.readLock().lock();
        try {
            tag.putLong("heatLastTick", lastActiveTick);
            if (activeProfile != null) tag.putString("heatProfile", activeProfile.toString());
            ListTag list = new ListTag();
            for (var e : heatSlots.entrySet()) {
                CompoundTag slot = new CompoundTag();
                slot.putString("id", e.getKey().toString());
                slot.putDouble("val", e.getValue());
                list.add(slot);
            }
            tag.put("heatSlots", list);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void loadFromNBT(CompoundTag tag) {
        lock.writeLock().lock();
        try {
            heatSlots.clear();
            lastActiveTick = tag.getLong("heatLastTick");
            if (tag.contains("heatProfile"))
                activeProfile = ResourceLocation.tryParse(tag.getString("heatProfile"));
            if (tag.contains("heatSlots")) {
                ListTag list = tag.getList("heatSlots", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag slot = list.getCompound(i);
                    var id = ResourceLocation.tryParse(slot.getString("id"));
                    if (id != null) heatSlots.put(id, slot.getDouble("val"));
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
