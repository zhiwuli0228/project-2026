package com.zhiwu.project2026.distributecache.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/8 18:12
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeasObject {
    // primary key, auto increment
    private int oid;
    // dn 和originalValue唯一
    private String dn;
    private String originalValue;
    private String displayValueZh;
    private String displayValueEn;
}
