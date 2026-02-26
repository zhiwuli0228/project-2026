package com.zhiwu.project2026.threadpool.integration;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;

import java.util.Objects;

public class ResizableQueueCapacityController implements QueueCapacityController {

    private final ResizableCapacityBlockingQueue<Runnable> queue;

    public ResizableQueueCapacityController(ResizableCapacityBlockingQueue<Runnable> queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    @Override
    public int currentSize() {
        return queue.size();
    }

    @Override
    public int currentCapacity() {
        return queue.getCapacity();
    }

    @Override
    public boolean supportsDynamicResize() {
        return true;
    }

    @Override
    public void resizeCapacity(int newCapacity) {
        queue.setCapacity(newCapacity);
    }
}
