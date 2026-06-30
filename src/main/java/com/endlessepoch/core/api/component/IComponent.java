package com.endlessepoch.core.api.component;

/**
 * Pluggable component for NovaNet nodes.
 * <p>
 * Other mods implement this to add amplifiers, filters, meters, or any custom module.
 * Registered via {@code INovaNetRegistry.registerComponent()}.
 * <p>
 * NovaNet 节点的可插拔组件。
 * <p>
 * 其他模组实现此接口以添加放大器、过滤器、计量器或任何自定义模块。
 * 通过 {@code INovaNetRegistry.registerComponent()} 注册。
 */
public interface IComponent {

    /** Unique component ID (e.g. "eecore:amplifier"). / 唯一的组件 ID（如 "eecore:amplifier"）。 */
    String getComponentId();

    /** Human-readable display name. / 人类可读的显示名称。 */
    String getDisplayName();

    /** Whether this component can be installed on a given node type. / 此组件是否可以安装在指定节点类型上。 */
    boolean isCompatibleWith(String nodeType);
}
