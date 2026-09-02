package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警记录列表项（含设备名称）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmLogVO {

    private String id;
    private String deviceId;
    private String deviceName;
    private String alarmType;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
