package com.zhiwu.project2026.cachereload;

import com.zhiwu.project2026.cachereload.bo.Poller;
import com.zhiwu.project2026.cachereload.impl.PollerCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheReloadRegressionTest {

    @Test
    void oldAtomicReferenceInitLogicShouldThrowNpe() {
        Map<String, AtomicReference<Set<Poller>>> oldCache = new HashMap<>();
        String type = "T1";
        Set<Poller> grouped = new HashSet<>();
        grouped.add(new Poller());

        assertThrows(NullPointerException.class, () -> {
            AtomicReference<Set<Poller>> setAtomicReference = oldCache.get(type);
            if (setAtomicReference == null) {
                // Old code initialized AtomicReference without initial Set value.
                setAtomicReference = new AtomicReference<>();
            }
            setAtomicReference.get().addAll(grouped);
            oldCache.put(type, setAtomicReference);
        });
    }

    @Test
    void oldReloadLoopWouldBlockFollowingCachesDuringRetryWindow() throws Exception {
        AtomicInteger remainingFailures = new AtomicInteger(4);
        LegacyLoopReloadCache broken = new LegacyLoopReloadCache(() -> {
            if (remainingFailures.getAndDecrement() > 0) {
                throw new RuntimeException("fail before eventually succeeding");
            }
        });
        AtomicBoolean nextCacheExecuted = new AtomicBoolean(false);
        LegacyLoopReloadCache next = new LegacyLoopReloadCache(() -> nextCacheExecuted.set(true));

        CompletableFuture<Void> chain = CompletableFuture.runAsync(() -> {
            broken.reload();
            next.reload();
        });

        assertFalse(waitUntil(nextCacheExecuted, 120),
                "Old while(reloadFlag) retry loop should block subsequent cache reload");
        chain.get(2, TimeUnit.SECONDS);
        assertTrue(nextCacheExecuted.get());
    }

    @Test
    void currentReloadWillStillBlockFollowingCachesDuringRetryWindow() throws Exception {
        AtomicInteger remainingFailures = new AtomicInteger(4);
        LocalCache<String, String> broken = new LocalCache<>(k -> {
            if (remainingFailures.getAndDecrement() > 0) {
                throw new RuntimeException("fail before eventually succeeding");
            }
        });
        AtomicBoolean nextCacheExecuted = new AtomicBoolean(false);
        LocalCache<String, String> next = new LocalCache<>(k -> nextCacheExecuted.set(true));

        CompletableFuture<Void> chain = CompletableFuture.runAsync(() -> {
            broken.reload();
            next.reload();
        });

        assertFalse(waitUntil(nextCacheExecuted, 120),
                "Current while(reloadFlag) policy will keep retrying broken cache and block next cache");
        chain.get(2, TimeUnit.SECONDS);
        assertTrue(nextCacheExecuted.get());
    }

    @Test
    void setPollerShouldPersistNewTypeIntoCacheMap() {
        PollerCache cache = new PollerCache(k -> {
        });
        Poller poller = new Poller();
        poller.setType("NEW_TYPE");
        poller.setKey("K1");

        cache.setPoller(poller);

        Set<Poller> result = cache.get("NEW_TYPE", "K1");
        assertNotNull(result);
        assertTrue(result.contains(poller));
    }

    private boolean waitUntil(AtomicBoolean value, long timeoutMs) {
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMs).toNanos();
        while (System.nanoTime() < deadline) {
            if (value.get()) {
                return true;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return value.get();
    }

    private static class LegacyLoopReloadCache {
        private final Runnable reloader;
        private volatile boolean reloadFlag;

        LegacyLoopReloadCache(Runnable reloader) {
            this.reloader = reloader;
        }

        void reload() {
            reloadFlag = true;
            while (reloadFlag) {
                try {
                    reloader.run();
                    reloadFlag = false;
                } catch (Throwable ignored) {
                    ThreadUtils.sleep(50);
                }
            }
        }
    }
}
