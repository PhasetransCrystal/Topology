package net.ptcrys.topo.api.material.process;

import net.ptcrys.topo.api.material.Material;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** Runs activated post-processors after all material content exists. Validation is freeze-time. */
public final class MaterialPostProcessorRunner {

    private MaterialPostProcessorRunner() {}

    @ApiStatus.Internal
    public static void processAll(List<Material> materials) {
        for (Material material : materials) {
            for (MaterialPostProcessor processor : material.postProcessors()) {
                processor.process(material);
            }
        }
    }
}
