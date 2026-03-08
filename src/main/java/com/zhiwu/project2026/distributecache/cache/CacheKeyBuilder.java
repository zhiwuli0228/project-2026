package com.zhiwu.project2026.distributecache.cache;

import org.springframework.stereotype.Component;

/**
 * Centralized key builder to avoid key drift in business code.
 */
@Component
public class CacheKeyBuilder {

    public String measObjByOid(int oid) {
        return "meas:{" + oid + "}:obj";
    }

    public String oidDn(int oid) {
        return "meas:{" + oid + "}:dn";
    }

    public String taskOids(String taskKey, String moType) {
        return "idx:task-oids:" + safe(taskKey) + ":" + safe(moType);
    }

    public String dnOids(String dn) {
        return "idx:dn-oids:" + safe(dn);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

