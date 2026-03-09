package com.zhiwu.project2026.cachereload;

import com.zhiwu.project2026.cachereload.bo.Poller;
import com.zhiwu.project2026.cachereload.impl.PollerCache;
import com.zhiwu.project2026.cachereload.impl.PollerCacheAtomicOps1;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheReloadChainIntegrationTest {

    @Test
    void chainWithAtomicOps1CanExposeNullReadWindowDuringMiddleReload() throws Exception {
        PollerCacheAtomicOps1 middle = new PollerCacheAtomicOps1(k -> {
        });
        middle.init(buildPollers(50, "TARGET", 0));
        assertNotNull(middle.get("TARGET", "K_INIT"));

        AtomicBoolean cache3Executed = new AtomicBoolean(false);
        AtomicBoolean sawNullDuringMiddleReload = new AtomicBoolean(false);

        Thread scheduler = new Thread(() -> {
            // cache2 reload
            sleepMs(10);
            // middle reload (slow)
            middle.init(buildPollers(200, "TARGET", 2));
            // cache3 reload
            cache3Executed.set(true);
        });
        scheduler.start();

        while (scheduler.isAlive()) {
            if (middle.get("TARGET", "K_ANY") == null) {
                sawNullDuringMiddleReload.set(true);
                break;
            }
            sleepMs(2);
        }
        scheduler.join();

        assertTrue(cache3Executed.get(), "cache3 should execute after middle cache reload returns");
        assertTrue(sawNullDuringMiddleReload.get(),
                "AtomicOps1 clears map first, so readers can observe null during middle reload");
    }

    @Test
    void chainWithPollerCacheSwapKeepsReadsAvailableDuringMiddleReload() throws Exception {
        PollerCache middle = new PollerCache(k -> {
        });
        middle.init(buildPollers(50, "TARGET", 0));
        assertNotNull(middle.get("TARGET", "K_INIT"));

        AtomicBoolean cache3Executed = new AtomicBoolean(false);
        AtomicBoolean sawNullDuringMiddleReload = new AtomicBoolean(false);

        Thread scheduler = new Thread(() -> {
            // cache2 reload
            sleepMs(10);
            // middle reload (slow)
            middle.init(buildPollers(200, "TARGET", 2));
            // cache3 reload
            cache3Executed.set(true);
        });
        scheduler.start();

        while (scheduler.isAlive()) {
            if (middle.get("TARGET", "K_ANY") == null) {
                sawNullDuringMiddleReload.set(true);
                break;
            }
            sleepMs(2);
        }
        scheduler.join();

        assertTrue(cache3Executed.get(), "cache3 should execute after middle cache reload returns");
        assertFalse(sawNullDuringMiddleReload.get(),
                "New-map-then-swap should keep old snapshot visible during middle reload");
    }

    private List<Poller> buildPollers(int typeCount, String targetType, long delayMs) {
        List<Poller> result = new ArrayList<>(typeCount + 1);
        for (int i = 0; i < typeCount; i++) {
            result.add(buildPoller("TYPE_" + i, "K_" + i, delayMs));
        }
        result.add(buildPoller(targetType, "K_INIT", delayMs));
        return result;
    }

    private Poller buildPoller(String type, String key, long delayMs) {
        SlowPoller poller = new SlowPoller(delayMs);
        poller.setType(type);
        poller.setKey(key);
        return poller;
    }

    private void sleepMs(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class SlowPoller extends Poller {
        private final long delayMs;

        private SlowPoller(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public String getType() {
            if (delayMs > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.getType();
        }
    }
}
