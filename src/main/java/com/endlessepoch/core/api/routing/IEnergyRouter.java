package com.endlessepoch.core.api.routing;

import java.util.*;

/**
 * Energy router — path discovery and load balancing for NovaNet.
 * <p>
 * Phase 3 full implementation. Default: direct connection only.
 * <p>
 * 能量路由器——NovaNet 的路径发现和负载均衡。
 * <p>
 * 第三阶段完整实现。默认：仅直连。
 */
public interface IEnergyRouter {

    /** Find all paths from one node to another. / 查找从一个节点到另一个节点的所有路径。 */
    List<UUID> findPaths(UUID fromId, UUID toId);

    /** Find the optimal (lowest loss / highest bandwidth) path. / 查找最优（最低损耗 / 最高带宽）路径。 */
    Optional<UUID> findOptimalPath(UUID fromId, UUID toId);

    /** Update load on a given path. / 更新指定路径上的负载。 */
    void updatePathLoad(UUID pathId, double load);

    /** Get the provider name for diagnostics. / 获取提供器名称，用于诊断。 */
    String getProviderName();
}
