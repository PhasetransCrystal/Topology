package net.ptcrys.topo.api.api.infrastructure;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/**
 * Zero-cost hook so debug probes (owned by the content mod) can instrument UI trees built by API
 * runtime code. API code calls {@link #instrument}; a probe installs itself once via
 * {@link #install}. Normal play with no probe installed is a no-op.
 */
public final class ExternalUiInstrumentation {

    /** Receives live instrument requests for UI trees the API builds outside a probe's own GUI. */
    public interface Instrumenter {

        void instrument(String tag, UIElement root);
    }

    private static volatile Instrumenter active;

    private ExternalUiInstrumentation() {}

    public static void install(Instrumenter instrumenter) {
        active = instrumenter;
    }

    public static void instrument(String tag, UIElement root) {
        Instrumenter instrumenter = active;
        if (instrumenter != null) {
            instrumenter.instrument(tag, root);
        }
    }
}
