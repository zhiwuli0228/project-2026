package com.zhiwu.project2026.cachereload;

import lombok.extern.log4j.Log4j2;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 20:50
 */
@Log4j2
public class CacheMgr {

    private static final Set<LocalCache> LOCAL_CACHES = new CopyOnWriteArraySet<>();

    public static void addLocalCache(LocalCache localCache) {
        LOCAL_CACHES.add(localCache);
    }

    public static void reload() {
        try {
            for (LocalCache localCache : LOCAL_CACHES) {
                localCache.reload();
            }
        } catch (Exception e) {
            log.error("reload cache error", e);
        }
    }
}
