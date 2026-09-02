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
@TableName("gh_devices")
public class GhDevice implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceSn;
    private String deviceName;
    private String zoneId;
    private String deviceType;
    private String model;
    private String adapterId;
    private BigDecimal posX;
    private BigDecimal posY;
    private BigDecimal posZ;
    private String onlineStatus;
    private Integer dimmingPercent;
    private Integer shadeOpenPercent;
    private Boolean powerOn;
    private BigDecimal lastPpfd;
    private BigDecimal lastLux;
    private BigDecimal lastTempC;
    private BigDecimal lastHumidityPct;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
}
