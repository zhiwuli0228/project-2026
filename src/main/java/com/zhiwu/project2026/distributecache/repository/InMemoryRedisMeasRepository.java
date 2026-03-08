package com.zhiwu.project2026.distributecache.repository;

import com.zhiwu.project2026.distributecache.model.MeasObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development placeholder.
 * Replace with Redis Cluster implementation.
 */
@Component
@ConditionalOnProperty(prefix = "distributecache.repo.redis", name = "type", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryRedisMeasRepository implements RedisMeasRepository {

    private final ConcurrentHashMap<Integer, MeasObject> objects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Integer>> taskOids = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> oidDns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Integer>> dnOids = new ConcurrentHashMap<>();

    @Override
    public Map<Integer, MeasObject> getObjectsByOids(Collection<Integer> oids) {
        Map<Integer, MeasObject> result = new HashMap<>();
        for (Integer oid : oids) {
            MeasObject value = objects.get(oid);
            if (value != null) {
                result.put(oid, value);
            }
        }
        return result;
    }

    @Override
    public List<Integer> getTaskOids(String taskKey, String moType) {
        return taskOids.get(taskKey(taskKey, moType));
    }

    @Override
    public Map<Integer, String> getDnByOids(Collection<Integer> oids) {
        Map<Integer, String> result = new HashMap<>();
        for (Integer oid : oids) {
            String dn = oidDns.get(oid);
            if (dn != null) {
                result.put(oid, dn);
            }
        }
        return result;
    }

    @Override
    public List<Integer> getDnOids(String dn) {
        return dnOids.get(dn);
    }

    @Override
    public void saveObjects(Map<Integer, MeasObject> objects, Duration ttl) {
        this.objects.putAll(objects);
    }

    @Override
    public void saveTaskOids(String taskKey, String moType, List<Integer> oids, Duration ttl) {
        taskOids.put(taskKey(taskKey, moType), safeCopy(oids));
    }

    @Override
    public void saveOidDn(Map<Integer, String> oidDnMap, Duration ttl) {
        oidDns.putAll(oidDnMap);
    }

    @Override
    public void saveDnOids(String dn, List<Integer> oids, Duration ttl) {
        dnOids.put(dn, safeCopy(oids));
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
