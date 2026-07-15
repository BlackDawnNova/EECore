package com.endlessepoch.core.api.tier;

import com.endlessepoch.core.api.energy.OmegaValue;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Voltage tier definitions from ELV (steam) through QV (Planck).
 * 电压等级定义，从 ELV（蒸汽级）到 QV（普朗克级）。
 */
public enum VoltageTier {

    ELV(0, "超低压/蒸汽级"),
    LV(1, "低压"),
    MV(2, "中压"),
    HV(3, "高压"),
    EHV(4, "超高压"),
    UHV(5, "特高压"),
    PHV(6, "行星高压"),
    XHV(7, "极限高压"),
    PLV(8, "等离子约束级"),
    SV(9, "施温格级"),
    BV(10, "真空衰变级"),
    QV(11, "普朗克级");

    public static final BigInteger QV_MIN = BigInteger.TEN.pow(21);
    public static final BigInteger HARD_LIMIT = BigInteger.TEN.pow(1000);
    private static final int TOTAL_TIERS = 12;
    private static final int STEPS = TOTAL_TIERS - 1;
    public static final int[] COMMON_AMPERAGES = {1, 2, 4, 8, 16};

    private static BigInteger calculateMinVoltage(int n) {
        if (n == 0) return BigInteger.ONE;
        if (n == STEPS) return BigInteger.TEN.pow(21);
        BigDecimal rate = BigDecimal.valueOf(Math.pow(10, 21.0 / 11.0));
        BigDecimal result = BigDecimal.ONE;
        for (int i = 0; i < n; i++) {
            result = result.multiply(rate);
        }
        return result.toBigInteger();
    }

    private static BigInteger calculateMaxVoltage(int n) {
        if (n == STEPS) return HARD_LIMIT;
        return calculateMinVoltage(n + 1);
    }

    private final int index;
    private final String chineseName;
    private final BigInteger minVoltage;
    private final BigInteger maxVoltage;

    VoltageTier(int index, String chineseName) {
        this.index = index;
        this.chineseName = chineseName;
        this.minVoltage = calculateMinVoltage(index);
        this.maxVoltage = calculateMaxVoltage(index);
    }

    public int getIndex() { return index; }
    public String getShortName() { return name(); }
    public String getChineseName() { return chineseName; }
    public BigInteger getMinVoltage() { return minVoltage; }
    public BigInteger getMaxVoltage() { return maxVoltage; }
    public boolean isQV() { return this == QV; }
    public BigInteger getDisplayVoltage() { return minVoltage; }

    public int[] getCommonAmperages() { return COMMON_AMPERAGES; }

    public BigInteger getPowerPerTick(int amperage) {
        if (minVoltage == null) return BigInteger.ZERO;
        return minVoltage.multiply(BigInteger.valueOf(amperage));
    }

    public BigInteger getPowerPerSecond(int amperage) {
        return getPowerPerTick(amperage).multiply(BigInteger.valueOf(20));
    }

    public boolean canHandle(BigInteger otherVoltage) {
        if (otherVoltage == null) return false;
        if (minVoltage == null || maxVoltage == null) return false;
        if (isQV()) {
            return otherVoltage.compareTo(minVoltage) >= 0 &&
                    otherVoltage.compareTo(maxVoltage) <= 0;
        }
        return otherVoltage.compareTo(minVoltage) >= 0 &&
                otherVoltage.compareTo(maxVoltage) < 0;
    }

    public boolean canHandle(VoltageTier other) {
        if (other == null) return false;
        return this.index >= other.index;
    }

    public boolean isPowerInRange(OmegaValue power) {
        if (power == null) return false;
        BigInteger val = power.toBigInteger();
        if (val == null) return false;
        return canHandle(val);
    }

    public boolean isPowerInRange(BigInteger power) {
        return canHandle(power);
    }

    public VoltageTier next() {
        int idx = this.index + 1;
        return idx < values().length ? values()[idx] : this;
    }

    public VoltageTier prev() {
        int idx = this.index - 1;
        return idx >= 0 ? values()[idx] : this;
    }

    public static VoltageTier fromVoltage(OmegaValue power) {
        if (power == null) return ELV;
        if (power.isZero()) return ELV;
        BigInteger val = power.toBigInteger();
        if (val == null) return ELV;
        for (int i = values().length - 1; i >= 0; i--) {
            VoltageTier tier = values()[i];
            if (tier.canHandle(val)) {
                return tier;
            }
        }
        return ELV;
    }

    public static VoltageTier fromVoltage(BigInteger voltage) {
        if (voltage == null) return ELV;
        return fromVoltage(OmegaValue.of(voltage));
    }

    public static VoltageTier fromVoltage(long voltage) {
        return fromVoltage(OmegaValue.of(voltage));
    }

    public static VoltageTier fromOrdinal(int ordinal) {
        if (ordinal < 0) return ELV;
        if (ordinal >= values().length) return QV;
        return values()[ordinal];
    }

    public static VoltageTier fromShortName(String name) {
        if (name == null || name.isEmpty()) return ELV;
        for (VoltageTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return ELV;
    }

    public String getHexColor() {
        return switch (this) {
            case ELV -> "#404040";
            case LV  -> "#909090";
            case MV  -> "#6B8E9B";
            case HV  -> "#C0C0C0";
            case EHV -> "#E05090";
            case UHV -> "#5080D0";
            case PHV -> "#D8D8E0";
            case XHV -> "#F0C020";
            case PLV -> "#2E8B57";
            case SV  -> "#F0F0F0";
            case BV  -> "#1A1A3A";
            case QV  -> "#C02020";
        };
    }

    public String getRangeDisplay() {
        if (minVoltage == null || maxVoltage == null) return "初始化中";
        if (isQV()) {
            return minVoltage + " ~ " + maxVoltage;
        }
        return minVoltage + " ~ " + maxVoltage.subtract(BigInteger.ONE);
    }

    @Override
    public String toString() {
        return name() + " (" + chineseName + ") " + getRangeDisplay() + "Ω";
    }

    public String toShortString() {
        return name() + " (" + chineseName + ")";
    }

    public static void printAllTiers() {
        System.out.printf("%-8s %-14s %-35s %-20s %-20s%n",
                "等级", "中文", "电压范围（Ω）", "标识电压", "每tick功率(1A)");
        for (VoltageTier tier : values()) {
            String power = tier.getPowerPerTick(1).toString() + " Ω/t";
            System.out.printf("%-8s %-14s %-35s %-20s %-20s%n",
                    tier.name(),
                    tier.chineseName,
                    tier.getRangeDisplay(),
                    tier.minVoltage != null ? tier.minVoltage.toString() : "null",
                    power);
        }
        System.out.println("QV 级下限 = " + QV_MIN + " Ω（1 ZΩ），上限 = " + HARD_LIMIT + " Ω（硬上限）");
        System.out.println("每级倍率 = 10^(21/11) ≈ " + Math.pow(10, 21.0 / 11.0));
        System.out.println("常用电流档位: 1A, 2A, 4A, 8A, 16A");
        System.out.println("功率 = 电压 × 电流");
        System.out.println("FE 换算: 1Ω = 2FE");
        System.out.println("ELV = 蒸汽级，不发电；LV = 电力起点");
    }
}
