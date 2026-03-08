package com.zhiwu.project2026.distributecache.repository;

import com.zhiwu.project2026.distributecache.model.MeasObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development placeholder.
 * Replace with DB implementation.
 */
@Component
@ConditionalOnProperty(prefix = "distributecache.repo.db", name = "type", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryMeasObjectDataRepository implements MeasObjectDataRepository {

    private final ConcurrentHashMap<Integer, MeasObject> objects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Integer>> taskOids = new ConcurrentHashMap<>();

    @Override
    public Map<Integer, MeasObject> findObjectsByOids(Collection<Integer> oids) {
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
    public List<Integer> findTaskOids(String taskKey, String moType) {
        List<Integer> oids = taskOids.get(key(taskKey, moType));
        return oids == null ? Collections.emptyList() : oids;
    }

    @Override
    public Map<Integer, String> findDnByOids(Collection<Integer> oids) {
        Map<Integer, String> result = new HashMap<>();
        for (Integer oid : oids) {
            MeasObject value = objects.get(oid);
            if (value != null && value.getDn() != null) {
                result.put(oid, value.getDn());
            }
        }
        return result;
    }

    @Override
    public List<Integer> findDnOids(String dn) {
        if (dn == null) {
            return Collections.emptyList();
        }
        Set<Integer> result = new LinkedHashSet<>();
        for (Map.Entry<Integer, MeasObject> entry : objects.entrySet()) {
            MeasObject value = entry.getValue();
            if (dn.equals(value.getDn())) {
                result.add(entry.getKey());
            }
        }
        return new ArrayList<>(result);
    }

    @Override
    public List<Integer> findOidsByOriginalValue(String originalValue, int offset, int limit) {
        if (originalValue == null || limit <= 0 || offset < 0) {
            return Collections.emptyList();
        }
        List<Integer> all = new ArrayList<>();
        for (Map.Entry<Integer, MeasObject> entry : objects.entrySet()) {
            if (originalValue.equals(entry.getValue().getOriginalValue())) {
                all.add(entry.getKey());
            }
        }
        all.sort(Comparator.naturalOrder());
        if (offset >= all.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(offset + limit, all.size());
        return new ArrayList<>(all.subList(offset, end));
    }

    private String key(String taskKey, String moType) {
        return (taskKey == null ? "" : taskKey) + "#" + (moType == null ? "" : moType);
    }
}
