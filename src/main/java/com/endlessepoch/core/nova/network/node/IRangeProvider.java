package com.endlessepoch.core.nova.network.node;

import com.endlessepoch.core.api.tier.VoltageTier;

/**
 * Maps voltage tier to coverage range.
 * <p>
 * 将电压等级映射到覆盖范围。
 * <p>
 * Default: ELV=4, LV=8, MV=16, HV=32, EHV=64, UHV=128
 * 默认：ELV=4, LV=8, MV=16, HV=32, EHV=64, UHV=128
 * Other mods can replace with their own scaling.
 * 其他模组可替换为自定义缩放。
 */
@FunctionalInterface
public interface IRangeProvider {

    /** Get the range (block radius) for a given voltage tier. / 获取指定电压等级的范围（方块半径）。 */
    int getRange(VoltageTier tier);

    /** Default range scaling: 4 * 2^(tier.ordinal). / 默认范围缩放：4 * 2^(tier.ordinal)。 */
    IRangeProvider DEFAULT = tier -> 4 << Math.min(tier.ordinal(), 10);
}
