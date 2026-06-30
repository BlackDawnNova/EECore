package com.endlessepoch.core.api.energy;

import java.math.BigInteger;

/**
 * Energy units - only Ω and FE are kept.
 * 能量单位 - 只保留 Ω 和 FE
 * 1Ω = 2FE
 * <p>
 * BigInteger-based methods are lossless for large numbers; long overloads are deprecated.
 * BigInteger 版本的方法对大数无损，long 版本已废弃。
 */
public enum EnergyUnit {
    OMEGA("Ω", 1.0),
    FE("FE", 0.5);  // 1 FE = 0.5 Ω → 1 Ω = 2 FE

    private final String symbol;
    private final double toOmega;

    EnergyUnit(String symbol, double toOmega) {
        this.symbol = symbol;
        this.toOmega = toOmega;
    }

    public String getSymbol() { return symbol; }

    /**
     * Converts the given amount to Ω (BigInteger, lossless).
     * 将指定数量转换为 Ω（BigInteger，无损）。
     * For FE: amount / 2
     * 对于 FE 单位：amount / 2
     */
    public BigInteger convertToOmega(BigInteger amount) {
        if (amount == null || amount.signum() <= 0) return BigInteger.ZERO;
        if (this == FE) return amount.divide(BigInteger.TWO);
        return amount;
    }

    /**
     * Converts Ω to this unit (BigInteger, lossless).
     * 将 Ω 转换为指定单位（BigInteger，无损）。
     * For FE: omega * 2
     * 对于 FE 单位：omega * 2
     */
    public BigInteger convertFromOmega(BigInteger omega) {
        if (omega == null || omega.signum() <= 0) return BigInteger.ZERO;
        if (this == FE) return omega.multiply(BigInteger.TWO);
        return omega;
    }

    public double convertToOmega(double amount) {
        return amount * toOmega;
    }

    public double convertFromOmega(double omega) {
        return omega / toOmega;
    }

    /**
     * @deprecated Overflows for values exceeding Long.MAX_VALUE. Use {@link #convertToOmega(BigInteger)}.
     * 对超过 Long.MAX_VALUE 的值会溢出。请使用 {@link #convertToOmega(BigInteger)}.
     */
    @Deprecated
    public long convertToOmega(long amount) {
        return (long)(amount * toOmega);
    }

    /**
     * @deprecated Overflows for values exceeding Long.MAX_VALUE/2. Use {@link #convertFromOmega(BigInteger)}.
     * 对超过 Long.MAX_VALUE/2 的值会溢出。请使用 {@link #convertFromOmega(BigInteger)}.
     */
    @Deprecated
    public long convertFromOmega(long omega) {
        return (long)(omega / toOmega);
    }
}
