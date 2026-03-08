package com.zhiwu.project2026.distributecache.event;

import com.zhiwu.project2026.distributecache.service.MeasObjectQueryService;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer can delegate to this handler.
 */
@Component
public class MeasInvalidateEventHandler {

    private final MeasObjectQueryService queryService;

    public MeasInvalidateEventHandler(MeasObjectQueryService queryService) {
        this.queryService = queryService;
    }

    public void onEvent(MeasInvalidateEvent event) {
        if (event == null || event.getOid() == null) {
            return;
        }
        String opType = event.getOpType() == null ? "" : event.getOpType().toUpperCase();
        if ("DELETE".equals(opType)) {
            queryService.invalidateByOid(event.getOid());
            if (event.getDn() != null) {
                queryService.invalidateByDn(event.getDn());
            }
            if (event.getTaskKey() != null || event.getMoType() != null) {
                queryService.invalidateTask(event.getTaskKey(), event.getMoType());
            }
            return;
        }

        // For UPSERT or other mutation, clear local cache entry to avoid stale reads.
        queryService.invalidateByOid(event.getOid());
        if (event.getDn() != null) {
            queryService.invalidateByDn(event.getDn());
        }
        if (event.getTaskKey() != null || event.getMoType() != null) {
            queryService.invalidateTask(event.getTaskKey(), event.getMoType());
        }
    }
}

