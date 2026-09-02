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
@TableName("gh_recipes")
public class GhRecipe implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recipeId;
    private String crop;
    private String cropNameZh;
    private String stage;
    private Integer version;
    private Boolean enabled;
    private BigDecimal photoperiodHours;
    private BigDecimal ppfdTargetMin;
    private BigDecimal ppfdTargetMax;
    private BigDecimal ppfdHardMin;
    private BigDecimal ppfdHardMax;
    private BigDecimal dliTargetMin;
    private BigDecimal dliTargetMax;
    private Boolean preferNatural;
    private Boolean autoSupplement;
    private Boolean autoShade;
    private Integer dimmingStepPct;
    private Integer shadeStepPct;
    private Integer cooldownSec;
    private Integer approveDimAbove;
    private Integer approveShadeAbove;
    private LocalDateTime createdAt;
}
