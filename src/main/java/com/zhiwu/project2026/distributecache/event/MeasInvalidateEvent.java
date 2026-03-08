package com.zhiwu.project2026.distributecache.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified cache invalidation/update event.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeasInvalidateEvent {
    private String eventId;
    private String opType;
    private Long version;
    private Long ts;
    private Integer oid;
    private String dn;
    private String moType;
    private String taskKey;
}

