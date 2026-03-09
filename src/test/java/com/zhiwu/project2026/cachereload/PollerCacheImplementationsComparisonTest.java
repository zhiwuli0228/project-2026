package com.zhiwu.project2026.cachereload;

import com.zhiwu.project2026.cachereload.bo.Poller;
import com.zhiwu.project2026.cachereload.impl.PollerCache;
import com.zhiwu.project2026.cachereload.impl.PollerCacheAtomicOps1;
import com.zhiwu.project2026.cachereload.impl.PollerCacheHashSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollerCacheImplementationsComparisonTest {

    @Test
    void hashSetInitShouldThrowNpeWhenInputIsNotEmpty() {
        PollerCacheHashSet cache = new PollerCacheHashSet(k -> {
        });

        assertThrows(NullPointerException.class, () -> cache.init(List.of(buildPoller("T1", "K1"))));
    }

    @Test
    void hashSetLiveViewCanThrowConcurrentModificationException() {
        PollerCacheHashSet cache = new PollerCacheHashSet(k -> {
        });
        String type = "T_HASH";
        cache.setPoller(buildPoller(type, "K1"));
        cache.setPoller(buildPoller(type, "K2"));

        Set<Poller> liveSet = cache.get(type, "K1");
        Iterator<Poller> iterator = liveSet.iterator();
        assertTrue(iterator.hasNext());

        cache.setPoller(buildPoller(type, "K3"));

        assertThrows(ConcurrentModificationException.class, iterator::next);
    }

    @Test
    void atomicOps1SetCreatesSnapshotSoOldIteratorStaysValid() {
        PollerCacheAtomicOps1 cache = new PollerCacheAtomicOps1(k -> {
        });
        String type = "T_ATOMIC";
        cache.setPoller(buildPoller(type, "K1"));
        cache.setPoller(buildPoller(type, "K2"));

        Set<Poller> snapshot = cache.get(type, "K1");
        Iterator<Poller> iterator = snapshot.iterator();
        assertTrue(iterator.hasNext());

        cache.setPoller(buildPoller(type, "K3"));

        assertDoesNotThrow(iterator::next);
    }

    @Test
    void atomicOps1InitCanExposeHalfInitializedEmptyWindow() throws Exception {
        PollerCacheAtomicOps1 cache = new PollerCacheAtomicOps1(k -> {
        });
        String targetType = "TARGET";
        cache.init(buildPollers(200, targetType));
        assertNotNull(cache.get(targetType, "K_INIT"));

        AtomicBoolean sawNullDuringReload = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        Thread reloader = new Thread(() -> {
            started.countDown();
            for (int i = 0; i < 40; i++) {
                cache.init(buildPollers(20_000, targetType));
            }
        });
        reloader.start();
        assertTrue(started.await(300, TimeUnit.MILLISECONDS));

        while (reloader.isAlive()) {
            if (cache.get(targetType, "K_ANY") == null) {
                sawNullDuringReload.set(true);
                break;
            }
        }
        reloader.join();

        assertTrue(sawNullDuringReload.get(),
                "Assigning cache to empty map first can expose transient null reads");
    }

    @Test
    void pollerCacheInitBuildsNewMapThenSwapSoNoHalfInitializedWindow() throws Exception {
        PollerCache cache = new PollerCache(k -> {
        });
        String targetType = "TARGET";
        cache.init(buildPollers(200, targetType));
        assertNotNull(cache.get(targetType, "K_INIT"));

        AtomicBoolean sawNullDuringReload = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        Thread reloader = new Thread(() -> {
            started.countDown();
            for (int i = 0; i < 40; i++) {
                cache.init(buildPollers(20_000, targetType));
            }
        });
        reloader.start();
        assertTrue(started.await(300, TimeUnit.MILLISECONDS));

        while (reloader.isAlive()) {
            if (cache.get(targetType, "K_ANY") == null) {
                sawNullDuringReload.set(true);
                break;
            }
        }
        reloader.join();

        assertFalse(sawNullDuringReload.get(),
                "Building new map first avoids exposing half-initialized state");
    }

    @Test
    void atomicOps1CanLookStuckForReaderWhenInitIsSlowBecauseCacheIsClearedFirst() throws Exception {
        PollerCacheAtomicOps1 cache = new PollerCacheAtomicOps1(k -> {
        });
        String targetType = "TARGET";
        cache.init(buildPollers(20, targetType));
        assertNotNull(cache.get(targetType, "K_INIT"));

        Thread reloader = new Thread(() -> cache.init(buildSlowPollers(250, targetType, 2)));
        reloader.start();

        boolean observedNull = waitUntil(() -> cache.get(targetType, "K_ANY") == null, 1_000);
        assertTrue(observedNull, "AtomicOps1 should expose null window after clearing cache");

        long startMs = System.currentTimeMillis();
        waitUntil(() -> cache.get(targetType, "K_ANY") != null, 5_000);
        long blockedMs = System.currentTimeMillis() - startMs;

        reloader.join();
        assertTrue(blockedMs >= 250,
                "Reader can stay unavailable for long time, which looks like a hang");
    }

    @Test
    void pollerCacheNewMapSwapShouldNotLookStuckForReaderEvenWhenInitIsSlow() throws Exception {
        PollerCache cache = new PollerCache(k -> {
        });
        String targetType = "TARGET";
        cache.init(buildPollers(20, targetType));
        assertNotNull(cache.get(targetType, "K_INIT"));

        Thread reloader = new Thread(() -> cache.init(buildSlowPollers(250, targetType, 2)));
        reloader.start();

        boolean observedNull = waitUntil(() -> cache.get(targetType, "K_ANY") == null, 1_000);
        reloader.join();
        assertFalse(observedNull,
                "New-map-then-swap keeps old snapshot visible, so readers do not observe null window");
    }

    private List<Poller> buildPollers(int typeCount, String targetType) {
        List<Poller> result = new ArrayList<>(typeCount + 1);
        for (int i = 0; i < typeCount; i++) {
            result.add(buildPoller("TYPE_" + i, "K_" + i));
        }
        result.add(buildPoller(targetType, "K_INIT"));
        return result;
    }

    private List<Poller> buildSlowPollers(int typeCount, String targetType, long delayMs) {
        List<Poller> result = new ArrayList<>(typeCount + 1);
        for (int i = 0; i < typeCount; i++) {
            result.add(buildSlowPoller("TYPE_" + i, "K_" + i, delayMs));
        }
        result.add(buildSlowPoller(targetType, "K_INIT", delayMs));
        return result;
    }

    private Poller buildPoller(String type, String key) {
        Poller poller = new Poller();
        poller.setType(type);
        poller.setKey(key);
        return poller;
    }

    private Poller buildSlowPoller(String type, String key, long delayMs) {
        SlowPoller poller = new SlowPoller(delayMs);
        poller.setType(type);
        poller.setKey(key);
        return poller;
    }

    private boolean waitUntil(Check check, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (check.matches()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(5);
        }
        return check.matches();
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }

    private static class SlowPoller extends Poller {
        private final long delayMs;

        private SlowPoller(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public String getType() {
            ThreadUtils.sleep(delayMs);
            return super.getType();
        }
    }
}
