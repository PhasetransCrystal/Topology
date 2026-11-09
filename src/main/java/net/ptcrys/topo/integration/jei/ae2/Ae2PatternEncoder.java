package net.ptcrys.topo.integration.jei.ae2;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;

import java.util.List;

/**
 * Seam onto AE2's pattern-encoding side. Production binds {@link DefaultAe2PatternEncoder} which
 * calls {@code EncodingHelper.encodeProcessingRecipe}; tests inject a spy that records the call
 * arguments without constructing a real {@link PatternEncodingTermMenu}.
 *
 * <p>
 * AE2 is a required dependency ({@code neoforge.mods.toml}), so this interface may reference
 * AE2 types directly — there is no optional-mod guard anywhere on this path.
 */
@FunctionalInterface
public interface Ae2PatternEncoder {

    void encode(
                PatternEncodingTermMenu menu,
                List<List<GenericStack>> inputs,
                List<GenericStack> outputs);
}
