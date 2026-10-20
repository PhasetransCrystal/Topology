package net.ptcrys.topodemo.api.equipment.common;

import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.topo.api.equipment.Equipment;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.equipment.EquipmentStrategy;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.api.lang.TemplateDisplayName;
import net.ptcrys.topo.api.machine.MachineItemBehavior;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.common.Tags;

import java.util.Objects;
import java.util.function.Supplier;

/** One configured equipment family produces one tool for each eligible material. */
public final class RegulatorEquipment implements EquipmentStrategy {

    private final String registryPath;
    private final TemplateDisplayName displayName;
    private final TextureRef texture;
    private final Supplier<ResourceKey<CreativeModeTab>> creativeTab;
    private final MachineItemBehavior behavior;

    private RegulatorEquipment(Builder builder) {
        if (builder.registryPath == null || !builder.registryPath.matches("[a-z0-9_/]*%s[a-z0-9_/]*")) {
            throw new IllegalArgumentException("registryPath must contain one %s and valid path characters");
        }
        registryPath = builder.registryPath;
        displayName = Objects.requireNonNull(builder.displayName, "displayName(en, cn) required");
        texture = Objects.requireNonNull(builder.texture, "texture required");
        creativeTab = Objects.requireNonNull(builder.creativeTab, "creativeTab required");
        behavior = Objects.requireNonNull(builder.behavior, "machineBehavior required");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean appliesTo(Material material) {
        return material.strategy().data(BuiltinMaterialDataTypes.MASS).isPresent() && material.strategy().forms().contains(BuiltinMaterialForms.INGOT);
    }

    @Override
    public void register(MaterialContentContext context, Equipment self) {
        String path = registryPath.formatted(context.material().id().getPath());
        var builder = context.core().<Item>item(path, RegulatorItem::new)
                .properties(properties -> properties.stacksTo(1))
                .modelTexture(texture)
                .addTag(Tags.Items.TOOLS_WRENCH)
                .addTab(creativeTab.get());
        RegistryDisplayLang.applyItem(builder, context.core(), path, displayName.resolve(context.material()));
        EquipmentRegistry.recordItem(self, context.material(), builder.register());
    }

    @Override
    public MachineItemBehavior machineBehavior() {
        return behavior;
    }

    public static final class Builder {

        private String registryPath;
        private TemplateDisplayName displayName;
        private TextureRef texture;
        private Supplier<ResourceKey<CreativeModeTab>> creativeTab;
        private MachineItemBehavior behavior;

        private Builder() {}

        public Builder registryPath(String pattern) {
            registryPath = pattern;
            return this;
        }

        public Builder displayName(String en, String cn) {
            displayName = DisplayNames.template(en, cn);
            return this;
        }

        public Builder texture(TextureRef texture) {
            this.texture = texture;
            return this;
        }

        public Builder creativeTab(Supplier<ResourceKey<CreativeModeTab>> tab) {
            creativeTab = tab;
            return this;
        }

        public Builder machineBehavior(MachineItemBehavior behavior) {
            this.behavior = behavior;
            return this;
        }

        public RegulatorEquipment build() {
            return new RegulatorEquipment(this);
        }
    }

    private static final class RegulatorItem extends Item {

        private RegulatorItem(Properties properties) {
            super(properties);
        }

        @Override
        public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
            return true;
        }
    }
}
