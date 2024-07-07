package com.poldroc.async.timer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 替代 System.currentTimeMillis(),用于解决高并发下System.currentTimeMillis卡顿
 * <p>
 * 避免在多线程环境下多次系统调用带来的性能开销
 */
public class SystemClock {
    private final int period;

    private final AtomicLong now;

    private static class InstanceHolder {
        private static final SystemClock INSTANCE = new SystemClock(1);
    }

    private SystemClock(int period) {
        this.period = period;
        this.now = new AtomicLong(System.currentTimeMillis());
        scheduleClockUpdating();
    }

    private void scheduleClockUpdating() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread systemClock = new Thread(runnable, "System Clock");
                    systemClock.setDaemon(true);
                    return systemClock;
                }
        );
        scheduler.scheduleAtFixedRate(() -> now.set(System.currentTimeMillis()), period, period, TimeUnit.MILLISECONDS);
    }

    private static SystemClock instance() {
        return InstanceHolder.INSTANCE;
    }

    private long currentTimeMillis() {
        return now.get();
    }

    /**
     * 用来替换原来的System.currentTimeMillis()
     */
    public static long now() {
        return instance().currentTimeMillis();
    }
}
