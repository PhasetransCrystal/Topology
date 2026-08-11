package net.ptcrys.topo.api.async;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Central owner for server-side async executors used by Odyssey Industrial runtime systems. */
public final class OIAsyncExecutors {

    private static final Logger LOGGER = LoggerFactory.getLogger(OIAsyncExecutors.class);
    private static final int MAX_TICK_SCHEDULER_THREADS = 2;

    private static volatile ScheduledExecutorService tickScheduler;
    private static boolean registered;

    private OIAsyncExecutors() {}

    public static void register() {
        synchronized (OIAsyncExecutors.class) {
            if (registered) {
                return;
            }
            registered = true;
        }
        NeoForge.EVENT_BUS.addListener(OIAsyncExecutors::onServerStopped);
    }

    public static synchronized ScheduledExecutorService tickScheduler() {
        if (tickScheduler == null || tickScheduler.isShutdown() || tickScheduler.isTerminated()) {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                    tickSchedulerThreadCount(),
                    new NamedThreadFactory("oi-tick-async-", Thread.NORM_PRIORITY));
            executor.setRemoveOnCancelPolicy(true);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            tickScheduler = executor;
        }
        return tickScheduler;
    }

    public static int tickSchedulerThreadCount() {
        return tickSchedulerThreadCount(Runtime.getRuntime().availableProcessors());
    }

    static int tickSchedulerThreadCount(int availableProcessors) {
        int cpus = Math.max(1, availableProcessors);
        return cpus <= 3 ? 1 : MAX_TICK_SCHEDULER_THREADS;
    }

    public static synchronized void shutdownServerExecutors() {
        shutdownTickScheduler();
    }

    public static synchronized void shutdownTickScheduler() {
        ScheduledExecutorService current = tickScheduler;
        tickScheduler = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        shutdownServerExecutors();
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final String namePrefix;
        private final int priority;
        private final AtomicInteger next = new AtomicInteger();

        private NamedThreadFactory(String namePrefix, int priority) {
            this.namePrefix = namePrefix;
            this.priority = Math.clamp(priority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, namePrefix + next.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(priority);
            thread.setUncaughtExceptionHandler((failedThread, error) -> LOGGER.error(
                    "[OIAsyncExecutors] {} terminated with an uncaught exception",
                    failedThread.getName(),
                    error));
            return thread;
        }
    }
}
