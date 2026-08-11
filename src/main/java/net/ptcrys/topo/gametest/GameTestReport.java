package net.ptcrys.topo.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

final class GameTestReport {

    private static final Logger LOGGER = LoggerFactory.getLogger("topo.gametest");

    private GameTestReport() {}

    static Consumer<GameTestHelper> wrap(
                                         String suite,
                                         int index,
                                         int total,
                                         String name,
                                         String description,
                                         Consumer<GameTestHelper> test) {
        Objects.requireNonNull(test, "test");
        return helper -> {
            long startNanos = System.nanoTime();
            LOGGER.info("\n{}\n", block(suite, index, total, name, "START", description, 0L, null));
            try {
                test.accept(helper);
                LOGGER.info("\n{}\n", block(
                        suite,
                        index,
                        total,
                        name,
                        "SUBMITTED",
                        description,
                        System.nanoTime() - startNanos,
                        null));
            } catch (RuntimeException | Error failure) {
                LOGGER.error("\n{}\n", block(
                        suite,
                        index,
                        total,
                        name,
                        "FAIL",
                        description,
                        System.nanoTime() - startNanos,
                        failure));
                throw failure;
            }
        };
    }

    private static String block(
                                String suite,
                                int index,
                                int total,
                                String name,
                                String status,
                                String description,
                                long elapsedNanos,
                                Throwable failure) {
        StringBuilder builder = new StringBuilder(384);
        builder.append("gameTest:\n");
        builder.append("  suite=").append(suite)
                .append(" index=").append(index).append('/').append(total)
                .append(" name=").append(name).append('\n');
        builder.append("  status=").append(status)
                .append(" description=").append(description).append('\n');
        if (elapsedNanos > 0L) {
            builder.append("  elapsedNanos=").append(elapsedNanos).append('\n');
        }
        if (failure != null) {
            builder.append("  failure=").append(failure.getClass().getName())
                    .append(": ").append(failure.getMessage()).append('\n');
        }
        return builder.toString();
    }
}
