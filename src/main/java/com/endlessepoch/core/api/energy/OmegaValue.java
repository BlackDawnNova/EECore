package com.endlessepoch.core.api.energy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;

/**
 * Immutable wrapper for Ω energy values, backed by BigInteger.
 * <p>
 * Ω 能量值的不可变包装，内部使用 BigInteger。
 * <p>
 * Provides safe big-number arithmetic (automatically clamped to MAX_LIMIT = 10¹⁰⁰⁰),
 * human-readable display (auto k/M/G/T/P/E/Z/Y/R/Q suffixes or scientific notation),
 * and NBT serialization.
 * <p>
 * 提供安全的大数运算（自动 clamp 到 MAX_LIMIT = 10¹⁰⁰⁰）、
 * 友好显示（自动加 k/M/G/T/P/E/Z/Y/R/Q 后缀或科学记数法）、
 * NBT 序列化。
 *
 * <pre>{@code
 * OmegaValue v1 = OmegaValue.of(1000);          // 1.00 kΩ
 * OmegaValue v2 = OmegaValue.of("1000000");     // 1.00 MΩ
 * OmegaValue v3 = OmegaValue.of(new BigInteger("999999999999999999999"));
 * OmegaValue sum = v1.add(v2).subtract(v3);
 * String display = sum.toDisplayString();        // "999.00 GΩ"
 * }</pre>
 */
public class OmegaValue implements Comparable<OmegaValue> {
    private static final BigInteger BASE = BigInteger.valueOf(1000);
    private final BigInteger value;
    private static final BigInteger MAX_LIMIT = BigInteger.TEN.pow(1000);

    public static OmegaValue of(long value) {
        if (value < 0) return zero();
        return new OmegaValue(BigInteger.valueOf(value));
    }

    public static OmegaValue of(BigInteger value) {
        if (value == null || value.signum() < 0) return zero();
        if (value.compareTo(MAX_LIMIT) > 0) {
            return new OmegaValue(MAX_LIMIT);
        }
        return new OmegaValue(value);
    }

    public static OmegaValue of(String value) {
        if (value == null || value.isEmpty()) return zero();
        try {
            return of(new BigInteger(value));
        } catch (NumberFormatException e) {
            return zero();
        }
    }

    private OmegaValue(BigInteger value) {
        this.value = value;
    }

    public static OmegaValue zero() {
        return new OmegaValue(BigInteger.ZERO);
    }

    public static OmegaValue max() {
        return new OmegaValue(MAX_LIMIT);
    }

    public OmegaValue add(OmegaValue other) {
        if (other == null || other.isZero()) return this;
        if (this.isZero()) return other;
        BigInteger result = this.value.add(other.value);
        return new OmegaValue(result.compareTo(MAX_LIMIT) > 0 ? MAX_LIMIT : result);
    }

    public OmegaValue subtract(OmegaValue other) {
        if (other == null || other.isZero()) return this;
        if (this.isZero()) return zero();
        BigInteger result = this.value.subtract(other.value);
        if (result.signum() < 0) result = BigInteger.ZERO;
        return new OmegaValue(result);
    }

    public OmegaValue multiply(long factor) {
        if (factor == 0 || this.isZero()) return zero();
        BigInteger result = this.value.multiply(BigInteger.valueOf(factor));
        return new OmegaValue(result.compareTo(MAX_LIMIT) > 0 ? MAX_LIMIT : result);
    }

    public OmegaValue multiply(OmegaValue other) {
        if (other == null || other.isZero() || this.isZero()) return zero();
        BigInteger result = this.value.multiply(other.value);
        return new OmegaValue(result.compareTo(MAX_LIMIT) > 0 ? MAX_LIMIT : result);
    }

    public OmegaValue divide(long divisor) {
        if (divisor == 0 || this.isZero()) return zero();
        return new OmegaValue(this.value.divide(BigInteger.valueOf(divisor)));
    }

    public OmegaValue divide(OmegaValue other) {
        if (other == null || other.isZero() || this.isZero()) return zero();
        return new OmegaValue(this.value.divide(other.value));
    }

    public OmegaValue pow(int exponent) {
        if (exponent < 0) return zero();
        if (exponent == 0) return new OmegaValue(BigInteger.ONE);
        BigInteger result = this.value.pow(exponent);
        return new OmegaValue(result.compareTo(MAX_LIMIT) > 0 ? MAX_LIMIT : result);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isMax() {
        return this.value.compareTo(MAX_LIMIT) >= 0;
    }

    public boolean isGreaterThan(long other) {
        return this.value.compareTo(BigInteger.valueOf(other)) > 0;
    }

    @Override
    public int compareTo(OmegaValue o) {
        if (o == null) return 1;
        return this.value.compareTo(o.value);
    }

    public OmegaValue clampToMax() {
        if (this.value.compareTo(MAX_LIMIT) > 0) {
            return new OmegaValue(MAX_LIMIT);
        }
        return this;
    }

    public OmegaValue multiply(BigInteger factor) {
        if (factor == null || factor.signum() == 0 || this.isZero()) return zero();
        BigInteger result = this.value.multiply(factor);
        return new OmegaValue(result.compareTo(MAX_LIMIT) > 0 ? MAX_LIMIT : result);
    }

    public OmegaValue divide(BigInteger divisor) {
        if (divisor == null || divisor.signum() == 0 || this.isZero()) return zero();
        return new OmegaValue(this.value.divide(divisor));
    }

    /**
     * Converts to long. Silently returns Long.MAX_VALUE if the value exceeds Long.MAX_VALUE.
     * 转换为 long。如果值超过 Long.MAX_VALUE，静默返回 Long.MAX_VALUE。
     * @deprecated Use {@link #toBigInteger()} for values that may exceed Long.MAX_VALUE.
     * 对可能超过 Long.MAX_VALUE 的值，请使用 {@link #toBigInteger()}
     */
    @Deprecated
    public long toLong() {
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return Long.MAX_VALUE;
        }
        return value.longValue();
    }

    public double toDouble() {
        return value.doubleValue();
    }

    public BigInteger toBigInteger() {
        return value;
    }

    public void saveToNBT(CompoundTag tag, String key) {
        tag.putString(key, value.toString());
    }

    public static OmegaValue loadFromNBT(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return OmegaValue.zero();
        }
        String str = tag.getString(key);
        return OmegaValue.of(str);
    }

    public String toDisplayString() {
        if (value.signum() == 0) return "0.00 Ω";

        String str = value.toString();
        int digits = str.length();

        if (digits > 30) {
            return toScientificNotation();
        }

        BigInteger v = value;
        int exp = 0;
        while (v.compareTo(BigInteger.valueOf(1000)) >= 0 && exp < 30) {
            v = v.divide(BigInteger.valueOf(1000));
            exp += 3;
        }

        BigInteger unit = BigInteger.valueOf(1000).pow(exp / 3);

        long mantissa = v.longValue();

        BigInteger remainder = value.mod(unit);
        long decimal = remainder.multiply(BigInteger.valueOf(1000))
                .divide(unit)
                .longValue();
        decimal = (decimal + 5) / 10;
        if (decimal >= 100) {
            mantissa += 1;
            decimal = 0;
        }

        if (exp == 0) {
            return mantissa + "." + String.format("%02d", decimal) + " Ω";
        } else {
            return mantissa + "." + String.format("%02d", decimal) + " " + getPrefix(exp) + "Ω";
        }
    }

    private String toScientificNotation() {
        String str = value.toString();
        int digits = str.length();
        int exponent = digits - 1;

        int mantissaDigits = Math.min(5, digits);
        String mantissaStr = str.substring(0, mantissaDigits);

        if (mantissaDigits == 1) {
            return mantissaStr + "×10^" + exponent + " Ω";
        }

        String formatted = mantissaStr.charAt(0) + "." + mantissaStr.substring(1);

        while (formatted.endsWith("0") && formatted.length() > 3) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }

        return formatted + "×10^" + exponent + " Ω";
    }

    private String getPrefix(int exp) {
        return switch (exp) {
            case 3 -> "k";
            case 6 -> "M";
            case 9 -> "G";
            case 12 -> "T";
            case 15 -> "P";
            case 18 -> "E";
            case 21 -> "Z";
            case 24 -> "Y";
            case 27 -> "R";
            case 30 -> "Q";
            default -> "";
        };
    }

    public static OmegaValue min(OmegaValue a, OmegaValue b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) < 0 ? a : b;
    }

    public static OmegaValue max(OmegaValue a, OmegaValue b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) > 0 ? a : b;
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        OmegaValue that = (OmegaValue) obj;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
