package com.zhiwu.project2026.cachereload;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalCacheReloadSemanticsTest {

    @Test
    void sameCacheCanReenterReloadAfterPreviousCallReturns() {
        AtomicInteger invokeCount = new AtomicInteger(0);
        LocalCache<String, String> cache = new LocalCache<>(k -> invokeCount.incrementAndGet());

        cache.reload();
        cache.reload();

        assertEquals(2, invokeCount.get(),
                "Write lock serializes one call, but does not prevent future scheduled calls");
    }

    @Test
    void blockedConsumerShouldHoldWriteLockAndBlockAnotherReloadCall() throws Exception {
        CountDownLatch firstEnter = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean shouldBlock = new AtomicBoolean(true);
        AtomicInteger entered = new AtomicInteger(0);

        LocalCache<String, String> cache = new LocalCache<>(k -> {
            entered.incrementAndGet();
            firstEnter.countDown();
            if (shouldBlock.get()) {
                try {
                    releaseFirst.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        CompletableFuture<Void> first = CompletableFuture.runAsync(cache::reload);
        assertTrue(firstEnter.await(300, TimeUnit.MILLISECONDS), "First reload should enter consumer");

        CompletableFuture<Void> second = CompletableFuture.runAsync(cache::reload);
        TimeUnit.MILLISECONDS.sleep(200);

        assertFalse(second.isDone(), "Second reload should block on write lock while first consumer is stuck");
        assertEquals(1, entered.get(), "Only first reload should have entered consumer at this moment");

        shouldBlock.set(false);
        releaseFirst.countDown();

        first.get(2, TimeUnit.SECONDS);
        second.get(2, TimeUnit.SECONDS);
        assertEquals(2, entered.get(), "Second reload enters only after the lock is released");
    }
}
