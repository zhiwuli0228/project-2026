package com.zhiwu.project2026.cachereload;

import com.zhiwu.project2026.cachereload.bo.Poller;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollerCacheAtomicOpsTest {

    @Test
    void legacyInitBucketShouldThrowNpeWhenReferenceValueIsNull() {
        Map<String, AtomicReference<Set<Poller>>> cache = new HashMap<>();
        Set<Poller> grouped = new HashSet<>();
        grouped.add(buildPoller("T1", "K1"));

        assertThrows(NullPointerException.class,
                () -> PollerCacheAtomicOps.legacyInitBucket(cache, "T1", grouped));
    }

    @Test
    void fixedInitBucketShouldCreateReferenceWithNonNullSet() {
        Map<String, AtomicReference<Set<Poller>>> cache = new HashMap<>();
        Set<Poller> grouped = new HashSet<>();
        Poller poller = buildPoller("T1", "K1");
        grouped.add(poller);

        assertDoesNotThrow(() -> PollerCacheAtomicOps.fixedInitBucket(cache, "T1", grouped));
        AtomicReference<Set<Poller>> ref = cache.get("T1");
        assertNotNull(ref);
        assertNotNull(ref.get());
        assertTrue(ref.get().contains(poller));
    }

    @Test
    void legacySetPollerMissingPutShouldLoseDataForNewType() {
        Map<String, AtomicReference<Set<Poller>>> cache = new HashMap<>();
        Poller poller = buildPoller("NEW_TYPE", "K1");

        PollerCacheAtomicOps.legacySetPollerMissingPut(cache, poller);

        assertFalse(cache.containsKey("NEW_TYPE"),
                "Legacy getOrDefault path mutates temporary reference and loses the new type");
    }

    @Test
    void fixedSetPollerShouldPersistDataForNewType() {
        Map<String, AtomicReference<Set<Poller>>> cache = new HashMap<>();
        Poller poller = buildPoller("NEW_TYPE", "K1");

        PollerCacheAtomicOps.fixedSetPoller(cache, poller);

        assertTrue(cache.containsKey("NEW_TYPE"));
        assertEquals(1, cache.get("NEW_TYPE").get().size());
        assertTrue(cache.get("NEW_TYPE").get().contains(poller));
    }

    private Poller buildPoller(String type, String key) {
        Poller poller = new Poller();
        poller.setType(type);
        poller.setKey(key);
        return poller;
    }
}
