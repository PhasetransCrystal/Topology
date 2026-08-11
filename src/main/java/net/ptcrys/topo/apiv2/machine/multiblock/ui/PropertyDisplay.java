package net.ptcrys.topo.apiv2.machine.multiblock.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;

/**
 * Display strategy for one recognizable blueprint block-state aspect: turns the raw property values
 * a cell pins (stair facing, top/bottom half, corner shape, log axis, &hellip;) into player-readable
 * text for the structure UI. Sealed on purpose: the only ways to obtain (and register) an instance
 * are {@link PropertyDisplayRegistry#registerEnum} and {@link PropertyDisplayRegistry#registerBoolean}
 * — other property kinds are not displayable. The builtin registrations live in data
 * ({@code BuiltinOIPropertyDisplays}).
 */
public sealed interface PropertyDisplay permits PropertyDisplays.EnumDisplay, PropertyDisplays.BooleanDisplay {

    /** The block-state properties this display can describe (e.g. both vanilla facing properties). */
    List<Property<?>> properties();

    /** The aspect's label, e.g. "Facing". */
    Component label();

    /** The player-readable form of {@code value} of {@code property}, e.g. "West". */
    Component value(Property<?> property, Comparable<?> value);
}
