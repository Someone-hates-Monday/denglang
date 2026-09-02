package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备最新光照值
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestLightVO {

    private String deviceId;
    private BigDecimal lightIntensity;
    private LocalDateTime createdAt;
}
