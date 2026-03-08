package com.zhiwu.project2026.distributecache.repository;

import com.zhiwu.project2026.distributecache.model.MeasObject;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MeasObjectDataRepository {

    Map<Integer, MeasObject> findObjectsByOids(Collection<Integer> oids);

    List<Integer> findTaskOids(String taskKey, String moType);

    Map<Integer, String> findDnByOids(Collection<Integer> oids);

    List<Integer> findDnOids(String dn);

    List<Integer> findOidsByOriginalValue(String originalValue, int offset, int limit);
}

