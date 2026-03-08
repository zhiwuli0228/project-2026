package com.zhiwu.project2026.distributecache.repository;

import com.zhiwu.project2026.distributecache.model.MeasObject;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RedisMeasRepository {

    Map<Integer, MeasObject> getObjectsByOids(Collection<Integer> oids);

    List<Integer> getTaskOids(String taskKey, String moType);

    Map<Integer, String> getDnByOids(Collection<Integer> oids);

    List<Integer> getDnOids(String dn);

    void saveObjects(Map<Integer, MeasObject> objects, Duration ttl);

    void saveTaskOids(String taskKey, String moType, List<Integer> oids, Duration ttl);

    void saveOidDn(Map<Integer, String> oidDnMap, Duration ttl);

    void saveDnOids(String dn, List<Integer> oids, Duration ttl);
}

