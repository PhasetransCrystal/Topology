package net.ptcrys.topo.integration.jei.ae2;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;

import java.util.List;

/**
 * Production binding to AE2's {@link EncodingHelper#encodeProcessingRecipe(PatternEncodingTermMenu,
 * java.util.List, java.util.List)}. Stateless singleton.
 */
public final class DefaultAe2PatternEncoder implements Ae2PatternEncoder {

    public static final Ae2PatternEncoder INSTANCE = new DefaultAe2PatternEncoder();

    private DefaultAe2PatternEncoder() {}

    @Override
    public void encode(
                       PatternEncodingTermMenu menu,
                       List<List<GenericStack>> inputs,
                       List<GenericStack> outputs) {
        EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
    }
}
