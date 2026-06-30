package com.endlessepoch.core.api.security;

import java.util.UUID;

/**
 * Energy security provider — identity verification and rate limiting.
 * <p>
 * Phase 3 implementation. Default: permissive (allow all).
 * Other mods can implement to add authentication, DDOS protection, etc.
 * <p>
 * 能量安全提供器——身份验证和速率限制。
 * <p>
 * 第三阶段实现。默认：宽松（允许全部）。
 * 其他模组可以实现以添加认证、DDOS 防护等功能。
 */
public interface IEnergySecurityProvider {

    /** Check if a node is authorized to participate in the network. / 检查节点是否被授权加入网络。 */
    boolean isAuthorized(UUID nodeId);

    /** Check if a node exceeds rate limits for receive/extract operations. / 检查节点是否超过接收/提取操作的速率限制。 */
    boolean isRateLimited(UUID nodeId);

    /** Return the provider name for diagnostics. / 返回提供器名称，用于诊断。 */
    String getProviderName();
}
