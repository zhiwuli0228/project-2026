package com.zhiwu.project2026.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasObjectCacheTest {

    @Test
    void shouldHandleAddAndRemoveEvents() {
        try (MeasObjectCache cache = new MeasObjectCache()) {
            cache.handle("type1:a,b,a");
            cache.handle("type2:b");

            awaitUntil(Duration.ofSeconds(2), () -> {
                Map<String, java.util.List<String>> snapshot = cache.snapshot();
                return snapshot.containsKey("a") && snapshot.get("a").size() == 2 && !snapshot.containsKey("b");
            });

            Map<String, java.util.List<String>> snapshot = cache.snapshot();
            assertEquals(2, snapshot.get("a").size());
            assertFalse(snapshot.containsKey("b"));
        }
    }

    @Test
    void shouldMergeEventsIntoBatchesToReduceLockCycles() {
        try (MeasObjectCache cache = new MeasObjectCache(
                10_000,
                512,
                Duration.ofMillis(50),
                raw -> new MeasObjectCache.CacheEvent(MeasObjectCache.CacheOperation.ADD, java.util.List.of("x"))
        )) {
            int totalEvents = 1_000;
            for (int i = 0; i < totalEvents; i++) {
                cache.handle("type1:x");
            }

            awaitUntil(Duration.ofSeconds(5), () -> cache.snapshot().getOrDefault("x", java.util.List.of()).size() == totalEvents);

            assertTrue(cache.getAppliedBatches() < totalEvents);
            assertEquals(totalEvents, cache.getAcceptedEvents());
            assertEquals(0, cache.getDroppedEvents());
        }
    }

    @Test
    void shouldRejectHandleAfterClose() {
        MeasObjectCache cache = new MeasObjectCache();
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.handle("type1:a"));
    }

    @Test
    void shouldApplyBackPressureInsteadOfDroppingWhenQueueIsFull() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (MeasObjectCache cache = new MeasObjectCache(1, 32, Duration.ofMillis(20), MeasObjectCache::defaultDeserializeForTest)) {
                int totalEvents = 200;
                for (int i = 0; i < totalEvents; i++) {
                    cache.handle("type1:x");
                }

                awaitUntil(Duration.ofSeconds(3), () -> {
                    int count = cache.snapshot().getOrDefault("x", java.util.List.of()).size();
                    return count == totalEvents;
                });

                assertEquals(totalEvents, cache.getAcceptedEvents());
                assertEquals(0, cache.getDroppedEvents());
            }
        });
    }

    private void awaitUntil(Duration timeout, Condition condition) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.ok()) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting");
            }
        }
        throw new AssertionError("condition not reached within " + timeout);
    }

    @FunctionalInterface
    private interface Condition {
        boolean ok();
    }
}
