package com.zhiwu.project2026.threadpool.integration;

public interface QueueCapacityController {

    int currentSize();

    int currentCapacity();

    boolean supportsDynamicResize();

    void resizeCapacity(int newCapacity);
}
