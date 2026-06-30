package com.endlessepoch.core.api.credit;

import java.util.UUID;

/**
 * Energy credit system — fair distribution, anti-vampire, overdraft limits.
 * <p>
 * Phase 3 full implementation. Default: permissive (unlimited).
 * Each team earns credit by producing energy, spends by consuming.
 * <p>
 * 能量信用系统——公平分配、反吸血、透支限制。
 * <p>
 * 第三阶段完整实现。默认：宽松（无限制）。
 * 每个队伍通过生产能量赚取信用，通过消耗能量花费信用。
 */
public interface IEnergyCreditSystem {

    /** Get the credit balance for a node. / 获取节点的信用余额。 */
    long getBalance(UUID nodeId);

    /** Add credit (earned through energy production). / 添加信用（通过能量生产获得）。 */
    void addCredit(UUID nodeId, long amount);

    /** Subtract credit (spent through energy consumption). / 扣除信用（通过能量消耗花费）。 */
    boolean subtractCredit(UUID nodeId, long amount);

    /** Check if a node has enough credit to withdraw a given amount. / 检查节点是否有足够信用提取指定数量。 */
    boolean hasSufficientCredit(UUID nodeId, long amount);

    /** Get the provider name for diagnostics. / 获取提供器名称，用于诊断。 */
    String getProviderName();
}
