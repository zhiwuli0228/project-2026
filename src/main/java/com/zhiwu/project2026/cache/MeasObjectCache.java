package com.zhiwu.project2026.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Kafka event buffer for local cache updates.
 *
 * <p>Design:
 * <p>1) handle() applies back-pressure: when queue is full it blocks on enqueue.
 * <p>2) worker drains multiple events and applies one merged batch under a single lock.
 * <p>3) lock acquisition count is reduced under burst traffic.
 */
public class MeasObjectCache implements AutoCloseable {

    private static final int DEFAULT_QUEUE_CAPACITY = 20_000;
    private static final int DEFAULT_BATCH_SIZE = 512;
    private static final long DEFAULT_POLL_MILLIS = 20L;

    private final Map<String, List<String>> localCache = new HashMap<>();
    private final ReentrantLock cacheLock = new ReentrantLock();
    private final BlockingQueue<CacheEvent> pendingEvents;
    private final Function<String, CacheEvent> eventDeserializer;
    private final int maxBatchSize;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong acceptedEvents = new AtomicLong();
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicLong appliedBatches = new AtomicLong();

    private final Thread workerThread;

    public MeasObjectCache() {
        this(DEFAULT_QUEUE_CAPACITY, DEFAULT_BATCH_SIZE, Duration.ofMillis(DEFAULT_POLL_MILLIS), MeasObjectCache::defaultDeserialize);
    }

    public MeasObjectCache(int queueCapacity,
                           int maxBatchSize,
                           Duration batchPollInterval,
                           Function<String, CacheEvent> eventDeserializer) {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("maxBatchSize must be positive");
        }
        Objects.requireNonNull(batchPollInterval, "batchPollInterval");
        if (batchPollInterval.isNegative() || batchPollInterval.isZero()) {
            throw new IllegalArgumentException("batchPollInterval must be positive");
        }
        this.pendingEvents = new LinkedBlockingQueue<>(queueCapacity);
        this.maxBatchSize = maxBatchSize;
        this.eventDeserializer = Objects.requireNonNull(eventDeserializer, "eventDeserializer");

        this.workerThread = new Thread(this::drainLoop, "meas-object-cache-worker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    /**
     * Supported default payload format:
     * <p>type1:key1,key2,key3 -> ADD
     * <p>type2:key1,key2,key3 -> REMOVE
     */
    public void handle(String rawEvent) {
        if (!running.get()) {
            throw new IllegalStateException("cache is closed");
        }
        CacheEvent event;
        try {
            event = eventDeserializer.apply(rawEvent);
        } catch (RuntimeException ex) {
            droppedEvents.incrementAndGet();
            return;
        }
        if (event == null || event.values().isEmpty()) {
            return;
        }
        try {
            pendingEvents.put(event);
            acceptedEvents.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for enqueue", e);
        }
    }

    public long getAcceptedEvents() {
        return acceptedEvents.get();
    }

    public long getDroppedEvents() {
        return droppedEvents.get();
    }

    public long getAppliedBatches() {
        return appliedBatches.get();
    }

    public Map<String, List<String>> snapshot() {
        cacheLock.lock();
        try {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            localCache.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            return copy;
        } finally {
            cacheLock.unlock();
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        workerThread.interrupt();
        try {
            workerThread.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void drainLoop() {
        List<CacheEvent> buffer = new ArrayList<>(maxBatchSize);
        while (running.get() || !pendingEvents.isEmpty()) {
            try {
                CacheEvent first;
                if (running.get()) {
                    // Fully block when idle to avoid periodic wake-up overhead.
                    first = pendingEvents.take();
                } else {
                    first = pendingEvents.poll();
                }
                if (first == null) {
                    continue;
                }
                buffer.clear();
                buffer.add(first);
                pendingEvents.drainTo(buffer, maxBatchSize - 1);
                applyBatch(buffer);
                appliedBatches.incrementAndGet();
            } catch (InterruptedException e) {
                if (!running.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void applyBatch(List<CacheEvent> batch) {
        cacheLock.lock();
        try {
            for (CacheEvent event : batch) {
                if (event.operation() == CacheOperation.ADD) {
                    addBatch(event.values());
                } else {
                    removeBatch(event.values());
                }
            }
        } finally {
            cacheLock.unlock();
        }
    }

    private void addBatch(List<String> values) {
        for (String value : values) {
            localCache.computeIfAbsent(value, k -> new ArrayList<>()).add(value);
        }
    }

    private void removeBatch(List<String> values) {
        for (String value : values) {
            localCache.remove(value);
        }
    }

    private static CacheEvent defaultDeserialize(String rawEvent) {
        if (rawEvent == null || rawEvent.isBlank()) {
            return null;
        }
        String[] split = rawEvent.split("[:|]", 2);
        String type = split[0].trim().toLowerCase();
        String payload = split.length > 1 ? split[1] : "";
        List<String> values = parseValues(payload);
        if ("type1".equals(type)) {
            return new CacheEvent(CacheOperation.ADD, values);
        }
        if ("type2".equals(type)) {
            return new CacheEvent(CacheOperation.REMOVE, values);
        }
        return null;
    }

    static CacheEvent defaultDeserializeForTest(String rawEvent) {
        return defaultDeserialize(rawEvent);
    }

    private static List<String> parseValues(String payload) {
        if (payload == null || payload.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = payload.split(",");
        List<String> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    public enum CacheOperation {
        ADD,
        REMOVE
    }

    public record CacheEvent(CacheOperation operation, List<String> values) {
        public CacheEvent {
            operation = Objects.requireNonNull(operation, "operation");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }
}
