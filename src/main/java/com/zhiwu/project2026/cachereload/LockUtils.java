package com.zhiwu.project2026.cachereload;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 20:58
 */
public class LockUtils {
    public static void doWithWriteLock(Runnable runnable, Lock lock) {
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }
}
