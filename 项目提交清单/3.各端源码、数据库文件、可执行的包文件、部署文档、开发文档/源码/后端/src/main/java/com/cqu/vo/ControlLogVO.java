package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 控制日志列表项（含设备名称和操作人名称）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlLogVO {

    private String id;
    private String deviceId;
    private String deviceName;
    private String operatorId;
    private String operatorName;
    private String command;
    private String source;
    private String result;
    private String executionStatus;
    private String expectedStatus;
    private LocalDateTime createdAt;
}
