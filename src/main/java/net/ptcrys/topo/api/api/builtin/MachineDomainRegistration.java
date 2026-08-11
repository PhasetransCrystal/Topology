package net.ptcrys.topo.api.api.builtin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.plugin.PluginIds;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.component.Attachment;
import net.ptcrys.topo.api.machine.component.AttachmentType;
import net.ptcrys.topo.api.machine.component.AttachmentTypes;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleRegistry;
import net.ptcrys.topo.api.machine.multiblock.ui.PropertyDisplay;
import net.ptcrys.topo.api.machine.multiblock.ui.PropertyDisplayRegistry;
import net.ptcrys.topo.api.machine.render.MachineBlockRenderStrategy;
import net.ptcrys.topo.api.machine.render.MachineBlockRenderType;
import net.ptcrys.topo.api.machine.render.MachineRenderRegistry;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.MachineResourceTypes;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.machine.resource.ResourceDataFieldFactory;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sole machine-domain registration entry for one {@link TopoPlugin}. Paths are bare; namespace and
 * {@link RegistryCore} are fixed by the plugin. One method per concern — no product-facing
 * overloads.
 */
public final class MachineDomainRegistration {

    private final TopoPlugin plugin;
    private final RegistryCore registry;

    private MachineDomainRegistration(TopoPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static MachineDomainRegistration of(TopoPlugin plugin) {
        return new MachineDomainRegistration(plugin, plugin.registry());
    }

    public TopoPlugin plugin() {
        return plugin;
    }

    public RegistryCore registry() {
        return registry;
    }

    public String modId() {
        return plugin.modId();
    }

    /** Stable id under this plugin namespace (machines, textures, attachment types, …). */
    public Identifier id(String path) {
        return PluginIds.id(plugin, path);
    }

    /** Begin declaring one machine. Completes with {@link Machines.Builder#build()}. */
    public Machines.Builder machine(String path) {
        return Machines.begin(id(path), registry);
    }

    /** Register a trait-mount metadata type (recipe logic, resource port, …). */
    public <M extends Attachment> AttachmentType<M> attachmentType(String path, Class<M> metadataClass) {
        return AttachmentTypes.begin(id(path), metadataClass);
    }

    /**
     * Register a machine resource family type with default ValueIO data-field factory.
     */
    public <R extends Resource> MachineResourceType<R> resourceType(
                                                                    String path,
                                                                    Class<R> resourceClass,
                                                                    @Nullable BlockCapability<ResourceHandler<R>, @Nullable Direction> blockCapability) {
        return MachineResourceTypes.begin(
                id(path), resourceClass, blockCapability, MachineResourceTypes.valueIoDataFieldFactory());
    }

    /**
     * Register a machine resource family type with a custom data-field factory (item/fluid slots).
     * Name lang must be bound on the same declaration site via {@link #bindResourceName} (or a
     * product factory that does both) — do not leave name/icon for a later phase.
     */
    public <R extends Resource> MachineResourceType<R> resourceTypeWithDataField(
                                                                                 String path,
                                                                                 Class<R> resourceClass,
                                                                                 @Nullable BlockCapability<ResourceHandler<R>, @Nullable Direction> blockCapability,
                                                                                 ResourceDataFieldFactory<R> dataFieldFactory) {
        return MachineResourceTypes.begin(id(path), resourceClass, blockCapability, dataFieldFactory);
    }

    /**
     * Bind display name for a resource type under this plugin and return the minted {@link LangKey}
     * (for shared handles such as scalar resources). Call in the same factory that created the type.
     */
    public LangKey bindResourceName(MachineResourceType<?> resourceType, String en, String cn) {
        Objects.requireNonNull(resourceType, "resource type");
        LangKey nameLang = plugin.lang().resource(resourceType.id(), "resource", en, cn);
        resourceType.bindNameLang(nameLang);
        return nameLang;
    }

    /** Register a machine block render type (shell, CTM hatch, …). */
    public <D, H extends MachineBlockRenderType<D>> H renderType(
                                                                 String path, H handle, MachineBlockRenderStrategy<D> strategy) {
        Identifier key = id(path);
        if (!key.equals(handle.id())) {
            throw new IllegalArgumentException(
                    "machine render type id mismatch: plugin path " + key + " != handle id " + handle.id());
        }
        return MachineRenderRegistry.beginBlock(key, handle, strategy);
    }

    /**
     * Register a multiblock part role. Display lang is owned by this declaration ({@code
     * part_capability.<modId>.&lt;path&gt;}); callers pass bare en/cn, not a pre-built Component.
     */
    public PartRole partRole(
                             String path,
                             String en,
                             String cn,
                             MachineResourceType<?> resourceType,
                             RecipeRole recipeIo) {
        Component displayName = plugin.lang().key("part_capability", path, en, cn).getComponent();
        return PartRoleRegistry.begin(id(path), displayName, resourceType, recipeIo);
    }

    /**
     * Register an enum block-state property display. Product passes catalog {@link LangKey}s (or
     * keys minted by {@link #lang()} elsewhere); domain converts to {@link Component} — product
     * must not call {@code getComponent()} at the registration site (code-style §3.12.3).
     */
    @SafeVarargs
    public final <T extends Enum<T> & StringRepresentable> PropertyDisplay propertyEnum(
                                                                                        String path,
                                                                                        LangKey label,
                                                                                        Map<T, LangKey> valueTexts,
                                                                                        EnumProperty<T>... properties) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(valueTexts, "value texts");
        Map<T, Component> components = new LinkedHashMap<>();
        for (Map.Entry<T, LangKey> entry : valueTexts.entrySet()) {
            components.put(
                    entry.getKey(),
                    Objects.requireNonNull(entry.getValue(), "value lang").getComponent());
        }
        return PropertyDisplayRegistry.beginEnum(id(path), label.getComponent(), components, properties);
    }

    /**
     * Register a boolean block-state property display. Same lang ownership rule as
     * {@link #propertyEnum}.
     */
    public PropertyDisplay propertyBoolean(
                                           String path,
                                           LangKey label,
                                           LangKey whenTrue,
                                           LangKey whenFalse,
                                           BooleanProperty... properties) {
        return PropertyDisplayRegistry.beginBoolean(
                id(path),
                Objects.requireNonNull(label, "label").getComponent(),
                Objects.requireNonNull(whenTrue, "whenTrue").getComponent(),
                Objects.requireNonNull(whenFalse, "whenFalse").getComponent(),
                properties);
    }
}
