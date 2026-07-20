package com.endlessepoch.core.api.energy.eb.batch;

import java.util.List;

/**
 * Versioned segment result — produced by background ForkJoin, validated by main thread.
 * If the version mismatches the current plan version, the entire segment is discarded
 * (inputs are only consumed during write-back, so this is lossless).
 * <p>
 * 带版本的分段结果——后台 ForkJoin 产出，主线程校验。版本不匹配则全段丢弃（物品仅写回时消耗，无损）。
 */
public record SpecResult(long version, long posHash, List<ShardResultUnit> results) {}
