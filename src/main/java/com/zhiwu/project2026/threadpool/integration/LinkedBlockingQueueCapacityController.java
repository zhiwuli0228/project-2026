package com.zhiwu.project2026.threadpool.integration;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

public class LinkedBlockingQueueCapacityController implements QueueCapacityController {

    private final LinkedBlockingQueue<Runnable> queue;

    public LinkedBlockingQueueCapacityController(LinkedBlockingQueue<Runnable> queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    @Override
    public int currentSize() {
        return queue.size();
    }

    @Override
    public int currentCapacity() {
        return queue.size() + queue.remainingCapacity();
    }

    @Override
    public boolean supportsDynamicResize() {
        return false;
    }

    @Override
    public void resizeCapacity(int newCapacity) {
        throw new UnsupportedOperationException("LinkedBlockingQueue does not support dynamic resize");
    }
}
