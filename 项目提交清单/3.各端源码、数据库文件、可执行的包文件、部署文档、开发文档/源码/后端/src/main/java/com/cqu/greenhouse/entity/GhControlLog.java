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
@TableName("gh_control_logs")
public class GhControlLog implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceSn;
    private String zoneId;
    private String command;
    private String source;
    private String payloadJson;
    private String executionStatus;
    private LocalDateTime createdAt;
}
