package com.cqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 告警记录表
 * </p>
 *
 * @author 
 * @since 2026-06-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("alarm_logs")
public class AlarmLogs implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.NONE)
    private Long id;

    private Long deviceId;

    private String alarmType;

    private String message;

    /**
     * 告警状态: ACTIVE-活跃, RESOLVED-已解决
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;


}
