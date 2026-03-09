package com.zhiwu.project2026.cachereload.impl;

import com.zhiwu.project2026.cachereload.LocalCache;
import com.zhiwu.project2026.cachereload.bo.Poller;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 鍔熻兘锛?
 *
 * @author zhiwu
 * @Data 2026/3/9 21:08
 */
public class PollerCache extends LocalCache<String, Poller> {

    private volatile Map<String, Set<Poller>> cache = new ConcurrentHashMap<>();

    public PollerCache(Consumer consumer) {
        super(consumer);
    }

    public void init(java.util.List<Poller> pollers) {
        Map<String, Set<Poller>> newCache = new ConcurrentHashMap<>();
        pollers.stream().collect(Collectors.groupingBy(Poller::getType)).forEach((type, groupedPollers) -> {
            newCache.put(type, Collections.unmodifiableSet(new HashSet<>(groupedPollers)));
        });
        cache = newCache;
    }

    public Set<Poller> get(String type, String key) {
        return cache.get(type);
    }

    public void setPoller(Poller poller) {
        cache.compute(poller.getType(), (type, oldSet) -> {
            Set<Poller> next = oldSet == null ? new HashSet<>() : new HashSet<>(oldSet);
            next.add(poller);
            return Collections.unmodifiableSet(next);
        });
    }
}
