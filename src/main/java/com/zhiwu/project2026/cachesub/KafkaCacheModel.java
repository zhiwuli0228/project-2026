package com.zhiwu.project2026.cachesub;

import lombok.Getter;
import lombok.Setter;

/**
 * Kafka 缓存事件。
 */
@Getter
@Setter
public class KafkaCacheModel {

    /**
     * 同一个 pollerId 的事件需要串行更新缓存。
     */
    private int pollerId;

    private String topic;

    private String type;
}
