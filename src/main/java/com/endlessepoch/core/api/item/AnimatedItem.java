package com.endlessepoch.core.api.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Item base class with animated tooltip support.
 * <p>
 * Other mods extend this and pass an {@link ItemTooltipAnimation} in the constructor.
 * The tooltip automatically renders with the configured animation effects.
 *
 * <pre>{@code
 * // In another mod:
 * public class MyCoolItem extends AnimatedItem {
 *     public MyCoolItem() {
 *         super(new Properties(), ItemTooltipAnimation.legendary("my_mod.my_item.desc"));
 *     }
 * }
 * }</pre>
 */
public abstract class AnimatedItem extends Item {

    protected final ItemTooltipAnimation animation;
    protected final boolean showAuthor;
    protected final String authorName;
    protected final String titleKey;

    public AnimatedItem(Properties properties, ItemTooltipAnimation animation,
                        boolean showAuthor, String authorName, String titleKey) {
        super(properties);
        this.animation = animation;
        this.showAuthor = showAuthor;
        this.authorName = authorName != null ? authorName : "EECore";
        this.titleKey = titleKey;
    }

    public AnimatedItem(Properties properties, ItemTooltipAnimation animation) {
        this(properties, animation, false, null, null);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        if (animation == null) return;

        // Animated title (use titleKey if set, otherwise item name)
        if (animation.titleRenderer() != null) {
            String key = titleKey != null ? titleKey : getDescriptionId();
            String title = Component.translatable(key).getString();
            tooltip.add(animation.titleRenderer().apply(title));
        }

        tooltip.add(Component.empty());

        // Extra description lines
        for (String key : animation.extraLines()) {
            if (key != null && !key.isEmpty()) {
                tooltip.add(Component.translatable(key));
            }
        }

        // Author line (last, with a blank line before it, animated)
        // Renderer receives the translation KEY and handles translate+animate internally
        if (showAuthor && animation.authorRenderer() != null) {
            tooltip.add(Component.empty());
            tooltip.add(animation.authorRenderer().apply("eecore.item.author"));
        }
    }
}
