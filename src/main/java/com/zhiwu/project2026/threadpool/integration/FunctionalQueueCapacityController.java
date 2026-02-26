package com.zhiwu.project2026.threadpool.integration;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class FunctionalQueueCapacityController implements QueueCapacityController {

    private final IntSupplier sizeSupplier;
    private final IntSupplier capacitySupplier;
    private final BooleanSupplier dynamicResizeSupported;
    private final IntConsumer resizeAction;

    public FunctionalQueueCapacityController(IntSupplier sizeSupplier,
                                             IntSupplier capacitySupplier,
                                             BooleanSupplier dynamicResizeSupported,
                                             IntConsumer resizeAction) {
        this.sizeSupplier = Objects.requireNonNull(sizeSupplier, "sizeSupplier");
        this.capacitySupplier = Objects.requireNonNull(capacitySupplier, "capacitySupplier");
        this.dynamicResizeSupported = Objects.requireNonNull(dynamicResizeSupported, "dynamicResizeSupported");
        this.resizeAction = Objects.requireNonNull(resizeAction, "resizeAction");
    }

    @Override
    public int currentSize() {
        return sizeSupplier.getAsInt();
    }

    @Override
    public int currentCapacity() {
        return capacitySupplier.getAsInt();
    }

    @Override
    public boolean supportsDynamicResize() {
        return dynamicResizeSupported.getAsBoolean();
    }

    @Override
    public void resizeCapacity(int newCapacity) {
        resizeAction.accept(newCapacity);
    }
}
