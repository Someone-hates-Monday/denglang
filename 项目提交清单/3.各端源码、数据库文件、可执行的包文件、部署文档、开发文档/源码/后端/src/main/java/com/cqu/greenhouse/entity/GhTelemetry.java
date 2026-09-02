package com.cqu.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("gh_telemetry")
public class GhTelemetry implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceSn;
    private String zoneId;
    private BigDecimal ppfd;
    private BigDecimal lux;
    private BigDecimal tempC;
    private BigDecimal humidity;
    private LocalDateTime createdAt;
}
