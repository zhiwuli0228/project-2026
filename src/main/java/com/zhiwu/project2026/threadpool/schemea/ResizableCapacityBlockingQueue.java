package com.zhiwu.project2026.threadpool.schemea;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ResizableCapacityBlockingQueue<E> implements BlockingQueue<E> {

    private final Queue<E> queue = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private int capacity;

    public ResizableCapacityBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public int getCapacity() {
        lock.lock();
        try {
            return capacity;
        } finally {
            lock.unlock();
        }
    }

    public void setCapacity(int newCapacity) {
        lock.lock();
        try {
            if (newCapacity <= 0) {
                throw new IllegalArgumentException("newCapacity must be positive");
            }
            if (newCapacity < queue.size()) {
                throw new IllegalArgumentException("newCapacity cannot be smaller than current queue size");
            }
            this.capacity = newCapacity;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean add(E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("queue full");
    }

    @Override
    public boolean offer(E e) {
        Objects.requireNonNull(e, "element");
        lock.lock();
        try {
            if (queue.size() >= capacity) {
                return false;
            }
            queue.add(e);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(E e) throws InterruptedException {
        Objects.requireNonNull(e, "element");
        lock.lockInterruptibly();
        try {
            while (queue.size() >= capacity) {
                notFull.await();
            }
            queue.add(e);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(e, "element");
        Objects.requireNonNull(unit, "unit");
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (queue.size() >= capacity) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            queue.add(e);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }
            E element = queue.remove();
            notFull.signal();
            return element;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                if (nanos <= 0) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            E element = queue.remove();
            notFull.signal();
            return element;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int remainingCapacity() {
        lock.lock();
        try {
            return capacity - queue.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        lock.lock();
        try {
            boolean removed = queue.remove(o);
            if (removed) {
                notFull.signal();
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(Object o) {
        lock.lock();
        try {
            return queue.contains(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        Objects.requireNonNull(c, "collection");
        if (c == this) {
            throw new IllegalArgumentException("cannot drain to self");
        }
        lock.lock();
        try {
            int drained = 0;
            while (drained < maxElements && !queue.isEmpty()) {
                c.add(queue.remove());
                drained++;
            }
            if (drained > 0) {
                notFull.signalAll();
            }
            return drained;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E remove() {
        E e = poll();
        if (e == null) {
            throw new IllegalStateException("queue empty");
        }
        return e;
    }

    @Override
    public E poll() {
        lock.lock();
        try {
            E e = queue.poll();
            if (e != null) {
                notFull.signal();
            }
            return e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E element() {
        E e = peek();
        if (e == null) {
            throw new IllegalStateException("queue empty");
        }
        return e;
    }

    @Override
    public E peek() {
        lock.lock();
        try {
            return queue.peek();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Iterator<E> iterator() {
        lock.lock();
        try {
            return new ArrayList<>(queue).iterator();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object[] toArray() {
        lock.lock();
        try {
            return queue.toArray();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            return queue.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        lock.lock();
        try {
            return queue.containsAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        Objects.requireNonNull(c, "collection");
        lock.lock();
        try {
            if (c.size() > (capacity - queue.size())) {
                throw new IllegalStateException("not enough capacity");
            }
            boolean changed = false;
            for (E e : c) {
                Objects.requireNonNull(e, "element");
                changed |= queue.add(e);
            }
            if (changed) {
                notEmpty.signalAll();
            }
            return changed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c, "collection");
        lock.lock();
        try {
            boolean changed = queue.removeAll(c);
            if (changed) {
                notFull.signalAll();
            }
            return changed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c, "collection");
        lock.lock();
        try {
            boolean changed = queue.retainAll(c);
            if (changed) {
                notFull.signalAll();
            }
            return changed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            if (!queue.isEmpty()) {
                queue.clear();
                notFull.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }
}
