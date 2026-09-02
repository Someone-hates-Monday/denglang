package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVO {

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
    /** 最近一次成功指令的期望 status（ON/OFF）；无则 null */
    private String expectedStatus;
    /** 期望与实际 status 是否一致；无期望时视为一致 */
    private Boolean statusMatch;
    private LocalDateTime lastHeartbeatTime;
    private LocalDateTime createdAt;
}
