package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdOverrideVO {

    private String id;
    /** DEVICE | GROUP */
    private String scopeType;
    private String scopeKey;
    /** 展示名：设备名或编组名 */
    private String scopeLabel;
    private BigDecimal lightThresholdOn;
    private BigDecimal lightThresholdOff;
    private LocalDateTime updatedAt;
}
