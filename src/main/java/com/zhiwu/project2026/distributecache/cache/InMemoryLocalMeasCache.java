package com.zhiwu.project2026.distributecache.cache;

import com.zhiwu.project2026.distributecache.model.MeasObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default local cache implementation.
 * Replace with Caffeine in production.
 */
@Component
public class InMemoryLocalMeasCache implements LocalMeasCache {

    private final ConcurrentHashMap<Integer, MeasObject> oidCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Integer>> taskOidIndexCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> oidDnCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Integer>> dnOidIndexCache = new ConcurrentHashMap<>();

    @Override
    public Map<Integer, MeasObject> getByOids(Collection<Integer> oids) {
        Map<Integer, MeasObject> result = new HashMap<>();
        for (Integer oid : oids) {
            MeasObject value = oidCache.get(oid);
            if (value != null) {
                result.put(oid, value);
            }
        }
        return result;
    }

    @Override
    public List<Integer> getTaskOids(String taskKey, String moType) {
        return taskOidIndexCache.get(taskKey(taskKey, moType));
    }

    @Override
    public Map<Integer, String> getDnByOids(Collection<Integer> oids) {
        Map<Integer, String> result = new HashMap<>();
        for (Integer oid : oids) {
            String dn = oidDnCache.get(oid);
            if (dn != null) {
                result.put(oid, dn);
            }
        }
        return result;
    }

    @Override
    public List<Integer> getDnOids(String dn) {
        return dnOidIndexCache.get(dn);
    }

    @Override
    public void putObjects(Map<Integer, MeasObject> objects) {
        oidCache.putAll(objects);
    }

    @Override
    public void putTaskOids(String taskKey, String moType, List<Integer> oids) {
        taskOidIndexCache.put(taskKey(taskKey, moType), safeCopy(oids));
    }

    @Override
    public void putOidDn(Map<Integer, String> oidDnMap) {
        oidDnCache.putAll(oidDnMap);
    }

    @Override
    public void putDnOids(String dn, List<Integer> oids) {
        dnOidIndexCache.put(dn, safeCopy(oids));
    }

    @Override
    public void evictOid(int oid) {
        oidCache.remove(oid);
        oidDnCache.remove(oid);
    }

    @Override
    public void evictDn(String dn) {
        dnOidIndexCache.remove(dn);
    }

    @Override
    public void evictTask(String taskKey, String moType) {
        taskOidIndexCache.remove(taskKey(taskKey, moType));
    }

    private String taskKey(String taskKey, String moType) {
        return Objects.requireNonNullElse(taskKey, "") + "#" + Objects.requireNonNullElse(moType, "");
    }

    private List<Integer> safeCopy(List<Integer> oids) {
        if (oids == null || oids.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(oids));
    }
}

