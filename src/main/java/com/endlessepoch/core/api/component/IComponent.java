package com.endlessepoch.core.api.component;

/**
 * Pluggable component for NovaNet nodes.
 * <p>
 * Other mods implement this to add amplifiers, filters, meters, or any custom module.
 * Registered via {@code INovaNetRegistry.registerComponent()}.
 */
public interface IComponent {

    /** Unique component ID (e.g. "eecore:amplifier"). */
    String getComponentId();

    /** Human-readable display name. */
    String getDisplayName();

    /** Whether this component can be installed on a given node type. */
    boolean isCompatibleWith(String nodeType);
}
