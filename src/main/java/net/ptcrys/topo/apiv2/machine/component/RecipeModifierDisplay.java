package net.ptcrys.topo.apiv2.machine.component;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Declaration-time title + description for a mount that provides {@link RecipeModifier}. Used by
 * machine item tooltips without instantiating a block entity; runtime {@link RecipeModifier#title()}
 * / {@link RecipeModifier#description()} must match these values.
 */
public record RecipeModifierDisplay(Component title, Component description) implements Attachment {

    public static final AttachmentType<RecipeModifierDisplay> TYPE = net.ptcrys.topo.apiv2.OfficialOIAPIPlugin.INSTANCE
            .machine()
            .attachmentType("recipe_modifier_display", RecipeModifierDisplay.class);

    public RecipeModifierDisplay {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }

    @Override
    public AttachmentType<RecipeModifierDisplay> type() {
        return TYPE;
    }

    @Override
    public void validateHost(ComponentMount<?> host) {
        for (ComponentKey.ServiceMetadata<?, ?> service : host.key().services()) {
            if (RecipeModifier.KEY.equals(service.service())) {
                return;
            }
        }
        throw new IllegalStateException(
                "RecipeModifierDisplay must be attached to a mount that provides RecipeModifier");
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}
}
