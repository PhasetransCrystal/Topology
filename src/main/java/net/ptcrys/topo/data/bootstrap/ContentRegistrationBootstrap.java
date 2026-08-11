package net.ptcrys.topo.data.bootstrap;

/**
 * Residual content phase between equipment and machine: force-load hull/part item tables so
 * RegistryLib queues their entries. Machine workbench / process recipes run later in
 * {@code OfficialOIPlugin#registerMachineFollowUps} after machine catalogs exist (code-style §3.11).
 *
 * <p>
 * Only orchestrates activation; does not own freezable product tables.
 */
public final class ContentRegistrationBootstrap {

    private static boolean bootstrapped;

    private ContentRegistrationBootstrap() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }

        ContentDataRegistrationBootstrap.bootstrap();

        bootstrapped = true;
    }
}
