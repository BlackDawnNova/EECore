package com.endlessepoch.core.nova.network.node;

import com.endlessepoch.core.api.tier.VoltageTier;

/**
 * Maps voltage tier to coverage range.
 * <p>
 * Default: ELV=4, LV=8, MV=16, HV=32, EHV=64, UHV=128
 * Other mods can replace with their own scaling.
 */
@FunctionalInterface
public interface IRangeProvider {

    /** Get the range (block radius) for a given voltage tier. */
    int getRange(VoltageTier tier);

    /** Default range scaling: 4 * 2^(tier.ordinal). */
    IRangeProvider DEFAULT = tier -> 4 << Math.min(tier.ordinal(), 10);
}
