package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 光照趋势图数据点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointVO {

    private LocalDateTime time;
    private BigDecimal value;
}
