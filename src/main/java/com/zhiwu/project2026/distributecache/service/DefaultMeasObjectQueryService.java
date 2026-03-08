package com.zhiwu.project2026.distributecache.service;

import com.zhiwu.project2026.distributecache.cache.LocalMeasCache;
import com.zhiwu.project2026.distributecache.config.DegradeProperties;
import com.zhiwu.project2026.distributecache.model.MeasObject;
import com.zhiwu.project2026.distributecache.repository.MeasObjectDataRepository;
import com.zhiwu.project2026.distributecache.repository.RedisMeasRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class DefaultMeasObjectQueryService implements MeasObjectQueryService {

    private static final Duration OBJECT_TTL = Duration.ofMinutes(45);
    private static final Duration TASK_INDEX_TTL = Duration.ofMinutes(10);
    private static final Duration DN_INDEX_TTL = Duration.ofMinutes(15);

    private final LocalMeasCache localCache;
    private final RedisMeasRepository redisRepository;
    private final MeasObjectDataRepository dataRepository;
    private final DegradeProperties degradeProperties;

    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();
    private final Semaphore dbFallbackSemaphore;

    public DefaultMeasObjectQueryService(LocalMeasCache localCache,
                                         RedisMeasRepository redisRepository,
                                         MeasObjectDataRepository dataRepository,
                                         DegradeProperties degradeProperties) {
        this.localCache = localCache;
        this.redisRepository = redisRepository;
        this.dataRepository = dataRepository;
        this.degradeProperties = degradeProperties;
        this.dbFallbackSemaphore = new Semaphore(Math.max(1, degradeProperties.getDbFallbackMaxConcurrent()));
    }

    @Override
    public List<MeasObject> queryByTask(String taskKey, String moType) {
        List<Integer> oids = getTaskOids(taskKey, moType);
        if (oids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, MeasObject> objectMap = getObjectsByOids(oids);
        return orderByInput(oids, objectMap);
    }

    @Override
    public List<MeasObject> queryByDn(String dn) {
        if (dn == null || dn.isBlank()) {
            return Collections.emptyList();
        }
        List<Integer> oids = localCache.getDnOids(dn);
        if (oids == null || oids.isEmpty()) {
            oids = redisEnabled() ? redisRepository.getDnOids(dn) : Collections.emptyList();
            if (oids == null || oids.isEmpty()) {
                oids = loadListWithSingleFlight("dn#" + dn, () -> withDbFallbackPermit(() -> dataRepository.findDnOids(dn)));
                if (redisEnabled()) {
                    redisRepository.saveDnOids(dn, oids, DN_INDEX_TTL);
                }
            }
            localCache.putDnOids(dn, oids);
        }
        Map<Integer, MeasObject> objectMap = getObjectsByOids(oids);
        return orderByInput(oids, objectMap);
    }

    @Override
    public List<MeasObject> queryByOriginalValue(String originalValue, int pageNo, int pageSize) {
        if (originalValue == null || originalValue.isBlank() || pageNo < 1 || pageSize < 1) {
            return Collections.emptyList();
        }
        int cappedPageSize = Math.min(pageSize, 200);
        int offset = (pageNo - 1) * cappedPageSize;
        List<Integer> oids = withDbFallbackPermit(() -> dataRepository.findOidsByOriginalValue(originalValue, offset, cappedPageSize));
        if (oids == null) {
            oids = Collections.emptyList();
        }
        if (oids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, MeasObject> objectMap = getObjectsByOids(oids);
        return orderByInput(oids, objectMap);
    }

    @Override
    public void invalidateByOid(int oid) {
        localCache.evictOid(oid);
    }

    @Override
    public void invalidateByDn(String dn) {
        if (dn != null && !dn.isBlank()) {
            localCache.evictDn(dn);
        }
    }

    @Override
    public void invalidateTask(String taskKey, String moType) {
        localCache.evictTask(taskKey, moType);
    }

    private List<Integer> getTaskOids(String taskKey, String moType) {
        List<Integer> oids = localCache.getTaskOids(taskKey, moType);
        if (oids != null && !oids.isEmpty()) {
            return oids;
        }
        oids = redisEnabled() ? redisRepository.getTaskOids(taskKey, moType) : Collections.emptyList();
        if (oids == null || oids.isEmpty()) {
            String flightKey = "task#" + taskKey + "#" + moType;
            oids = loadListWithSingleFlight(flightKey, () -> withDbFallbackPermit(() -> dataRepository.findTaskOids(taskKey, moType)));
            if (redisEnabled()) {
                redisRepository.saveTaskOids(taskKey, moType, oids, TASK_INDEX_TTL);
            }
        }
        localCache.putTaskOids(taskKey, moType, oids);
        return oids == null ? Collections.emptyList() : oids;
    }

    private Map<Integer, MeasObject> getObjectsByOids(Collection<Integer> oids) {
        if (oids == null || oids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, MeasObject> result = new HashMap<>(localCache.getByOids(oids));
        Set<Integer> miss = subtract(oids, result.keySet());
        if (miss.isEmpty()) {
            return result;
        }

        Map<Integer, MeasObject> redisHit = redisEnabled() ? redisRepository.getObjectsByOids(miss) : Collections.emptyMap();
        if (!redisHit.isEmpty()) {
            localCache.putObjects(redisHit);
            result.putAll(redisHit);
        }

        Set<Integer> dbMiss = subtract(miss, redisHit.keySet());
        if (dbMiss.isEmpty()) {
            return result;
        }

        Map<Integer, MeasObject> dbHit = loadMapWithSingleFlight("obj#" + dbMiss.hashCode(),
            () -> withDbFallbackPermit(() -> dataRepository.findObjectsByOids(dbMiss)));
        if (!dbHit.isEmpty()) {
            localCache.putObjects(dbHit);
            if (redisEnabled()) {
                redisRepository.saveObjects(dbHit, OBJECT_TTL);
                redisRepository.saveOidDn(toOidDnMap(dbHit), OBJECT_TTL);
            }
            result.putAll(dbHit);
        }
        return result;
    }

    private boolean redisEnabled() {
        return degradeProperties.isRedisEnabled();
    }

    private Map<Integer, String> toOidDnMap(Map<Integer, MeasObject> objects) {
        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<Integer, MeasObject> entry : objects.entrySet()) {
            MeasObject value = entry.getValue();
            if (value != null && value.getDn() != null) {
                result.put(entry.getKey(), value.getDn());
            }
        }
        return result;
    }

    private <T> T withDbFallbackPermit(SupplierWithException<T> supplier) {
        boolean acquired = dbFallbackSemaphore.tryAcquire();
        if (!acquired) {
            return null;
        }
        try {
            return supplier.get();
        } catch (Exception ex) {
            return null;
        } finally {
            dbFallbackSemaphore.release();
        }
    }

    private List<Integer> loadListWithSingleFlight(String key, SupplierWithException<List<Integer>> loader) {
        Object lock = singleFlight.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                List<Integer> loaded = loader.get();
                return loaded == null ? Collections.emptyList() : loaded;
            } catch (Exception ex) {
                return Collections.emptyList();
            } finally {
                singleFlight.remove(key, lock);
            }
        }
    }

    private Map<Integer, MeasObject> loadMapWithSingleFlight(String key, SupplierWithException<Map<Integer, MeasObject>> loader) {
        Object lock = singleFlight.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                Map<Integer, MeasObject> loaded = loader.get();
                return loaded == null ? Collections.emptyMap() : loaded;
            } catch (Exception ex) {
                return Collections.emptyMap();
            } finally {
                singleFlight.remove(key, lock);
            }
        }
    }

    private Set<Integer> subtract(Collection<Integer> left, Collection<Integer> right) {
        Set<Integer> result = new LinkedHashSet<>(left);
        result.removeAll(new LinkedHashSet<>(right));
        return result;
    }

    private List<MeasObject> orderByInput(List<Integer> inputOrder, Map<Integer, MeasObject> objects) {
        List<MeasObject> result = new ArrayList<>(inputOrder.size());
        for (Integer oid : inputOrder) {
            MeasObject value = objects.get(oid);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
