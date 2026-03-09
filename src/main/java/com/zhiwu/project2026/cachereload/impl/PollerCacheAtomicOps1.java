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
public class PollerCacheAtomicOps1 extends LocalCache<String, Poller> {

    private Map<String, AtomicReference<Set<Poller>>> cache = new ConcurrentHashMap<>();

    public PollerCacheAtomicOps1(Consumer consumer) {
        super(consumer);
    }

    public void init(List<Poller>  pollers) {
        cache = new ConcurrentHashMap<>();
        pollers.stream().collect(Collectors.groupingBy(Poller::getType)).forEach((type, groupedPollers) -> {
            cache.put(type, new AtomicReference<>(new HashSet<>(groupedPollers)));
        });
    }

    public Set<Poller> get(String type, String key) {
        AtomicReference<Set<Poller>> setAtomicReference = cache.get(type);
        if (setAtomicReference == null) {
            return null;
        }
        return setAtomicReference.get();
    }

    public void setPoller(Poller poller) {
        AtomicReference<Set<Poller>> ref =
                cache.computeIfAbsent(poller.getType(), k -> new AtomicReference<>(new HashSet<>()));
        while (true) {
            Set<Poller> oldSet = ref.get();
            Set<Poller> newSet = new HashSet<>(oldSet);
            newSet.add(poller);

            if (ref.compareAndSet(oldSet, newSet)) {
                break;
            }
        }
    }
}
