package com.zhiwu.project2026.cachesub;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 同一个 pollerId 在本机最多只保留一份待处理任务，重复事件在入队前直接过滤。
 */
@Component
public class Consumer {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final long LOCK_TTL_MILLIS = 3_000L;
    private static final long RETRY_DELAY_MILLIS = 20L;
    private static final int WORKER_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

    private final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final Set<Integer> pendingPollerIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> dirtyPollerIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> retryAtByPollerId = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(WORKER_COUNT);
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();
    private final RedisDistributedLock redisDistributedLock;

    public Consumer(RedisDistributedLock redisDistributedLock) {
        this.redisDistributedLock = redisDistributedLock;
    }

    @PostConstruct
    void init() {
        for (int i = 0; i < WORKER_COUNT; i++) {
            workers.submit(this::runWorker);
        }
        retryScheduler.scheduleWithFixedDelay(
                this::drainRetryQueue,
                RETRY_DELAY_MILLIS,
                RETRY_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    void destroy() {
        workers.shutdownNow();
        retryScheduler.shutdownNow();
    }

    public void add(KafkaCacheModel kafkaCacheModel) {
        if (kafkaCacheModel == null) {
            return;
        }
        add(kafkaCacheModel.getPollerId());
    }

    public void add(int pollerId) {
        if (!pendingPollerIds.add(pollerId)) {
            dirtyPollerIds.add(pollerId);
            return;
        }
        if (!queue.offer(pollerId)) {
            pendingPollerIds.remove(pollerId);
        }
    }

    private void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Integer pollerId = queue.take();
                handleEvent(pollerId);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleEvent(int pollerId) {
        String lockKey = buildLockKey(pollerId);
        String lockValue = redisDistributedLock.tryLock(lockKey, LOCK_TTL_MILLIS);
        if (lockValue == null) {
            scheduleRetry(pollerId);
            return;
        }

        try {
            handleMergedEvent(pollerId);
        } finally {
            redisDistributedLock.unlock(lockKey, lockValue);
            finishOrReschedule(pollerId);
        }
    }

    private void scheduleRetry(int pollerId) {
        long nextRetryAt = System.currentTimeMillis() + RETRY_DELAY_MILLIS;
        retryAtByPollerId.merge(pollerId, nextRetryAt, Math::min);
    }

    private void handleMergedEvent(int pollerId) {
        // do sth
    }

    private String buildLockKey(int pollerId) {
        return "lock:" + pollerId;
    }

    private void finishOrReschedule(int pollerId) {
        retryAtByPollerId.remove(pollerId);
        if (dirtyPollerIds.remove(pollerId)) {
            if (!queue.offer(pollerId)) {
                scheduleRetry(pollerId);
            }
            return;
        }
        pendingPollerIds.remove(pollerId);
    }

    private void drainRetryQueue() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Long> entry : retryAtByPollerId.entrySet()) {
            Integer pollerId = entry.getKey();
            Long retryAt = entry.getValue();
            if (retryAt == null || retryAt > now) {
                continue;
            }
            if (!retryAtByPollerId.remove(pollerId, retryAt)) {
                continue;
            }
            if (!pendingPollerIds.contains(pollerId)) {
                continue;
            }
            if (!queue.offer(pollerId)) {
                scheduleRetry(pollerId);
            }
        }
    }
}
