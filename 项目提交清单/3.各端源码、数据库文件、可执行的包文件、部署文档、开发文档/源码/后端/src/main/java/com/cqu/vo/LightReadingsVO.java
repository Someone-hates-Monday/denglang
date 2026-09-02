package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 光照记录列表项（含设备名称）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LightReadingsVO {

    private String id;
    private String deviceId;
    private String deviceName;
    private BigDecimal lightIntensity;
    private LocalDateTime createdAt;
}
