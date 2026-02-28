package com.zhiwu.project2026.aioobe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/2/27 22:33
 */
public class AtomicReferenceSetTest {
    @Test
    void atomicReferenceVersionIsSafe() throws InterruptedException {

        AtomicReference<Set<Integer>> ref =
                new AtomicReference<>(new HashSet<>());

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(1);

        int writeCount = 10000;

        // 写线程
        pool.submit(() -> {
            try {
                latch.await();
                for (int i = 0; i < writeCount; i++) {
                    int value = i;
                    while (true) {
                        Set<Integer> oldSet = ref.get();
                        Set<Integer> newSet = new HashSet<>(oldSet);
                        newSet.add(value);

                        if (ref.compareAndSet(oldSet, newSet)) {
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });

        // 读线程
        pool.submit(() -> {
            try {
                latch.await();
                for (int i = 0; i < writeCount; i++) {
                    Set<Integer> snapshot = ref.get();
                    List<Integer> list = new ArrayList<>(snapshot);
                    // 不应抛异常
                }
            } catch (Exception e) {
                fail("Should not throw exception: " + e.getMessage());
            }
        });

        latch.countDown();

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // 验证数据完整性
        Set<Integer> finalSet = ref.get();
        assertEquals(writeCount, finalSet.size(),
                "Data loss detected in CAS implementation");
    }
}
