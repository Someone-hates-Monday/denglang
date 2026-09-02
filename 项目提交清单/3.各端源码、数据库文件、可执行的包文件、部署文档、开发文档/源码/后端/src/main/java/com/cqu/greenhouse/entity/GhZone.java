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
@TableName("gh_zones")
public class GhZone implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String zoneId;
    private String name;
    private String recipeId;
    private String climateProfileId;
    private Boolean autoControl;
    private String aggregation;
    private Integer shadeOpenPercent;
    private BigDecimal coverTransmittance;
    private BigDecimal lengthM;
    private BigDecimal widthM;
    private BigDecimal lastEffectivePpfd;
    private BigDecimal lastDli;
    private LocalDateTime lastRuleAt;
    private LocalDateTime createdAt;
}
