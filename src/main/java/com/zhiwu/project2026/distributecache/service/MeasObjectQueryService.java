package com.zhiwu.project2026.distributecache.service;

import com.zhiwu.project2026.distributecache.model.MeasObject;

import java.util.List;

public interface MeasObjectQueryService {

    List<MeasObject> queryByTask(String taskKey, String moType);

    List<MeasObject> queryByDn(String dn);

    List<MeasObject> queryByOriginalValue(String originalValue, int pageNo, int pageSize);

    void invalidateByOid(int oid);

    void invalidateByDn(String dn);

    void invalidateTask(String taskKey, String moType);
}

