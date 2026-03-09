package com.zhiwu.project2026.cachereload.impl;

import com.zhiwu.project2026.cachereload.LocalCache;
import com.zhiwu.project2026.cachereload.bo.Poller;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 鍔熻兘锛?
 *
 * @author zhiwu
 * @Data 2026/3/9 21:08
 */
public class PollerCacheHashSet extends LocalCache<String, Poller> {

    private Map<String, Set<Poller>> cache = new ConcurrentHashMap<>();

    public PollerCacheHashSet(Consumer consumer) {
        super(consumer);
    }

    public void init(List<Poller>  pollers) {
        cache = new ConcurrentHashMap<>();
        pollers.stream().collect(Collectors.groupingBy(Poller::getType)).forEach((type, groupedPollers) -> {
            Set<Poller> pollers1 = cache.get(type);
            pollers1.addAll(groupedPollers);

        });

    }

    public Set<Poller> get(String type, String key) {
        return cache.get(type);

    }

    public void setPoller(Poller poller) {
        Set<Poller> pollers = cache.computeIfAbsent(poller.getType(), n -> new HashSet<>());
        pollers.add(poller);
    }
}
