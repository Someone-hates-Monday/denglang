package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 生效阈值（含来源） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveThresholdVO {

    private BigDecimal lightThresholdOn;
    private BigDecimal lightThresholdOff;
    /** GLOBAL | GROUP | DEVICE */
    private String source;
    private String sourceKey;
}
