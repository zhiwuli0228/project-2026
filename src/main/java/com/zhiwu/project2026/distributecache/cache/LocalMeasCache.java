package com.zhiwu.project2026.distributecache.cache;

import com.zhiwu.project2026.distributecache.model.MeasObject;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface LocalMeasCache {

    Map<Integer, MeasObject> getByOids(Collection<Integer> oids);

    List<Integer> getTaskOids(String taskKey, String moType);

    Map<Integer, String> getDnByOids(Collection<Integer> oids);

    List<Integer> getDnOids(String dn);

    void putObjects(Map<Integer, MeasObject> objects);

    void putTaskOids(String taskKey, String moType, List<Integer> oids);

    void putOidDn(Map<Integer, String> oidDnMap);

    void putDnOids(String dn, List<Integer> oids);

    void evictOid(int oid);

    void evictDn(String dn);

    void evictTask(String taskKey, String moType);
}

