package com.zhiwu.project2026.cachereload;

import com.zhiwu.project2026.cachereload.bo.Poller;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Extracted atomic-reference cache operations for deterministic tests.
 */
public final class PollerCacheAtomicOps {

    private PollerCacheAtomicOps() {
    }

    public static void legacyInitBucket(
            Map<String, AtomicReference<Set<Poller>>> cache, String type, Set<Poller> groupedPollers) {
        AtomicReference<Set<Poller>> ref = cache.get(type);
        if (ref == null) {
            // Legacy mistake: no initial Set value.
            ref = new AtomicReference<>();
        }
        ref.get().addAll(groupedPollers);
        cache.put(type, ref);
    }

    public static void fixedInitBucket(
            Map<String, AtomicReference<Set<Poller>>> cache, String type, Set<Poller> groupedPollers) {
        cache.put(type, new AtomicReference<>(new HashSet<>(groupedPollers)));
    }

    public static void legacySetPollerMissingPut(
            Map<String, AtomicReference<Set<Poller>>> cache, Poller poller) {
        AtomicReference<Set<Poller>> ref =
                cache.getOrDefault(poller.getType(), new AtomicReference<>(new HashSet<>()));
        while (true) {
            Set<Poller> oldSet = ref.get();
            Set<Poller> newSet = new HashSet<>(oldSet);
            newSet.add(poller);
            if (ref.compareAndSet(oldSet, newSet)) {
                return;
            }
        }
    }

    public static void fixedSetPoller(
            Map<String, AtomicReference<Set<Poller>>> cache, Poller poller) {
        AtomicReference<Set<Poller>> ref =
                cache.computeIfAbsent(poller.getType(), k -> new AtomicReference<>(new HashSet<>()));
        while (true) {
            Set<Poller> oldSet = ref.get();
            Set<Poller> newSet = new HashSet<>(oldSet);
            newSet.add(poller);
            if (ref.compareAndSet(oldSet, newSet)) {
                return;
            }
        }
    }
}
