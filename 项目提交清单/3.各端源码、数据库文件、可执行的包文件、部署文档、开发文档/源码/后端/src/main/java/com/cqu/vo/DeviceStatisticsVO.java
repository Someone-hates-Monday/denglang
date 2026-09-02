package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备概览统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatisticsVO {

    private String totalCount;
    private String onlineCount;
    private String offlineCount;
    private String onCount;
    private String offCount;
}
