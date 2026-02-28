package com.zhiwu.project2026.aioobe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/2/27 22:32
 */
public class ConcurrentSetCopyTest {
    @Test
    void reproduceArrayIndexOutOfBounds() throws InterruptedException {
        Set<Integer> set = new HashSet<>();

        // 预填充
        for (int i = 0; i < 128; i++) {
            set.add(i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        // 写线程：不停修改
        pool.submit(() -> {
            try {
                latch.await();
                while (!errorOccurred.get()) {
                    set.add(ThreadLocalRandom.current().nextInt());
                }
            } catch (Exception ignored) {
            }
        });

        // 读线程：不停拷贝
        pool.submit(() -> {
            try {
                latch.await();
                while (!errorOccurred.get()) {
                    try {
                        new ArrayList<>(set);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        errorOccurred.set(true);
                        System.out.println("Reproduced AIOOBE!");
                        e.printStackTrace();
                    }
                }
            } catch (Exception ignored) {
            }
        });

        latch.countDown();

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(errorOccurred.get(),
                "Expected ArrayIndexOutOfBoundsException but did not reproduce");
    }
}
