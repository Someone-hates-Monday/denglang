package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 阈值配置展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdConfigVO {

    private String id;
    private BigDecimal lightThresholdOn;
    private BigDecimal lightThresholdOff;
    private Integer heartbeatTimeout;
    private LocalDateTime updatedAt;
}
