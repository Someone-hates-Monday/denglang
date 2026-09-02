package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备详情（含最新光照值和活跃告警数）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDetailVO {

    private String id;
    private String deviceName;
    private String deviceSn;
    private String status;
    private String onlineStatus;
    /** AUTO | MANUAL */
    private String controlMode;
    /** 编组名称，空=未分组 */
    private String groupName;
    /** 纬度（GCJ-02）；未标定则为 null */
    private BigDecimal latitude;
    /** 经度（GCJ-02）；未标定则为 null */
    private BigDecimal longitude;
    /** 最近一次成功指令的期望 status；无则 null */
    private String expectedStatus;
    /** 期望与实际是否一致 */
    private Boolean statusMatch;
    private LocalDateTime lastHeartbeatTime;
    private BigDecimal latestLightIntensity;
    private String activeAlarmCount;
    private LocalDateTime createdAt;
}
