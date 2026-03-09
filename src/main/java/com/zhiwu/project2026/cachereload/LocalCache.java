
package com.zhiwu.project2026.cachereload;

import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 20:52
 */
@Log4j2
public class LocalCache<K, V> {

    private volatile boolean initFlag = false;

    private volatile boolean reloadFlag = false;

    private final Consumer consumer;

    private final Lock lock = new ReentrantLock();

    private final Map<K, V> cache = new ConcurrentHashMap<>();


    public void reload() {
        reloadFlag = true;
        try {
            log.info("start reload");
            LockUtils.doWithWriteLock(() -> {
                consumer.accept(null);
                initFlag = true;
            }, lock);
            log.info("end reload");
        } catch (Throwable e) {
            log.error("reload cache error", e);
        } finally {
            reloadFlag = false;
        }
    }

    public LocalCache(Consumer consumer) {
        this.consumer = consumer;
        CacheMgr.addLocalCache(this);
    }

    public V get(K key) {
        while (!initFlag) {
            ThreadUtils.sleep(100);
        }
        while (reloadFlag) {
            ThreadUtils.sleep(100);
        }
        return cache.get(key);
    }
}
