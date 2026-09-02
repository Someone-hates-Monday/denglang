package com.cqu.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("gh_work_orders")
public class GhWorkOrder implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String zoneId;
    private String status;
    private String reason;
    private Integer suggestedDimmingPct;
    private Integer suggestedShadePct;
    private String targetDeviceSn;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private LocalDateTime completedAt;
}
