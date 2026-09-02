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
 * 路灯控制指令日志
 * </p>
 *
 * @author 
 * @since 2026-06-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("control_logs")
public class ControlLogs implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.NONE)
    private Long id;

    private Long deviceId;

    private Long operatorId;

    private String command;

    /**
     * 指令来源: AUTO-光照联动自动, MANUAL-手动远程
     */
    private String source;

    private String result;

    /**
     * 指令执行状态: PENDING | SUCCESS | TIMEOUT
     */
    private String executionStatus;

    /**
     * 期望板端回传 status: ON | OFF
     */
    private String expectedStatus;

    private LocalDateTime createdAt;


}
