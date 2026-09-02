package com.cqu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("threshold_overrides")
public class ThresholdOverride implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** DEVICE | GROUP */
    private String scopeType;

    /** deviceId 字符串或编组名 */
    private String scopeKey;

    private BigDecimal lightThresholdOn;

    private BigDecimal lightThresholdOff;

    private LocalDateTime updatedAt;
}
