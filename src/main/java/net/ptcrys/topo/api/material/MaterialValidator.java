package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.api.registration.ExternalBlockTarget;
import net.ptcrys.topo.api.api.registration.ExternalFluidTarget;
import net.ptcrys.topo.api.api.registration.ExternalItemTarget;
import net.ptcrys.topo.api.material.data.MaterialDataRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialContentKey;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Freeze-time policy: data, form plans, output ids, external targets, and post-processors. */
final class MaterialValidator {

    private MaterialValidator() {}

    static void validate(List<Material> materials) {
        validateContentKeys(materials);
        validateExternalTargets(materials);
        for (Material material : materials) {
            validateMaterial(material);
        }
    }

    private static void validateContentKeys(List<Material> materials) {
        Map<MaterialContentKey, String> owners = new LinkedHashMap<>();
        for (Material material : materials) {
            for (MaterialForm form : material.forms()) {
                String owner = material.id() + " / " + form.id();
                for (MaterialContentKey key : material.strategy().plan(form).outputs()) {
                    String previous = owners.putIfAbsent(key, owner);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "material content " + key.type().id() + " " + key.id() + " is produced by both " + previous + " and " + owner);
                    }
                }
            }
        }
    }

    private static void validateExternalTargets(List<Material> materials) {
        Map<Identifier, String> itemTargets = new LinkedHashMap<>();
        Map<Identifier, String> blockTargets = new LinkedHashMap<>();
        Map<Identifier, String> fluidTargets = new LinkedHashMap<>();
        for (Material material : materials) {
            for (MaterialForm form : material.forms()) {
                String owner = material.id() + " / " + form.id();
                MaterialFormTargets targets = material.strategy().plan(form).targets();
                targets.item().ifPresent(target -> register(itemTargets, resolveItem(target), owner, "item"));
                targets.block().ifPresent(target -> {
                    register(blockTargets, resolveBlock(target), owner, "block");
                    register(itemTargets, resolveBlockItem(target), owner, "block item");
                });
                targets.fluid()
                        .ifPresent(target -> register(fluidTargets, resolveFluid(target), owner, "fluid"));
            }
        }
    }

    private static void register(Map<Identifier, String> targets, @Nullable Identifier id,
                                 String owner, String kind) {
        if (id == null) {
            return;
        }
        String previous = targets.putIfAbsent(id, owner);
        if (previous != null) {
            throw new IllegalStateException(
                    "external " + kind + " target " + id + " is declared by both " + previous + " and " + owner);
        }
    }

    private static @Nullable Identifier resolveItem(ExternalItemTarget target) {
        try {
            return target.id();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static @Nullable Identifier resolveBlock(ExternalBlockTarget target) {
        try {
            return target.id();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static @Nullable Identifier resolveBlockItem(ExternalBlockTarget target) {
        try {
            return target.itemId();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static @Nullable Identifier resolveFluid(ExternalFluidTarget target) {
        try {
            return target.id();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static void validateMaterial(Material material) {
        for (MaterialDataUse<?> use : material.dataUses()) {
            MaterialDataRegistry.validate(material, use);
        }
        for (MaterialForm form : material.forms()) {
            material.optionsFor(form).ifPresent(options -> {
                Predicate<MaterialDataType<?>> present = type -> material.hasData(type) || options.hasData(type);
                for (MaterialDataUse<?> use : options.dataUses()) {
                    MaterialDataRegistry.validate(material, use, present);
                }
            });
            material.strategy().plan(form).validate();
        }
        for (MaterialPostProcessor processor : material.postProcessors()) {
            processor.validate(material);
        }
    }
}
