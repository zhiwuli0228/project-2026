package com.zhiwu.project2026.distributecache.model;

import lombok.*;

import java.util.List;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/8 17:57
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TaskModel {

    private int taskId;
    private String taskKey;

    private String moType;

    private List<String> measTypeKeys;
}
