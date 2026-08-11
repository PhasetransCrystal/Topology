package net.ptcrys.topo.api.pipe.network;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * Fast transaction opener for the pipe engine hot path.
 *
 * <p>
 * {@link Transaction#openRoot()} performs a {@code StackWalker.getCallerClass()} on every call
 * — a native stack walk costing ~20µs that the JIT cannot remove (it only feeds debug messages in
 * {@code TransactionManager}). The pipe engine opens at most one transaction per extraction port
 * per tick, so that walk dominates a steady-state network's entire budget. This utility binds
 * {@code TransactionManager.getManagerForThread().open(null, callerClass)} through method handles
 * with a constant caller class instead; if the private API ever changes or reflection is denied,
 * it falls back to the public {@link Transaction#openRoot()} transparently.
 */
final class PipeTransactions {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipeTransactions.class);
    private static final @Nullable MethodHandle GET_MANAGER;
    private static final @Nullable MethodHandle OPEN;

    static {
        MethodHandle getManager = null;
        MethodHandle open = null;
        try {
            Class<?> managerClass = Class.forName("net.neoforged.neoforge.transfer.transaction.TransactionManager");
            Method getManagerMethod = managerClass.getDeclaredMethod("getManagerForThread");
            getManagerMethod.setAccessible(true);
            Method openMethod = managerClass.getDeclaredMethod("open", TransactionContext.class, Class.class);
            openMethod.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            // Pre-adapted to generic call-site types so the hot path can use invokeExact.
            getManager = lookup.unreflect(getManagerMethod)
                    .asType(MethodType.methodType(Object.class));
            open = lookup.unreflect(openMethod)
                    .asType(MethodType.methodType(
                            Transaction.class, Object.class, TransactionContext.class, Class.class));
        } catch (Throwable error) {
            LOGGER.warn("Pipe engine falls back to Transaction.openRoot (per-open stack walk): {}",
                    error.toString());
            getManager = null;
            open = null;
        }
        GET_MANAGER = getManager;
        OPEN = open;
    }

    private PipeTransactions() {}

    static Transaction openRoot() {
        MethodHandle getManager = GET_MANAGER;
        MethodHandle open = OPEN;
        if (getManager != null && open != null) {
            try {
                Object manager = (Object) getManager.invokeExact();
                return (Transaction) open.invokeExact(manager, (TransactionContext) null,
                        (Class) PipeTransactions.class);
            } catch (Throwable error) {
                throw new IllegalStateException("Pipe transaction fast-open failed", error);
            }
        }
        return Transaction.openRoot();
    }
}
