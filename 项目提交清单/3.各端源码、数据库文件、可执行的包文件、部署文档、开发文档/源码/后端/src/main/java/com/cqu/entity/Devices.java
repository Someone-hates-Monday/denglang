package com.cqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 路灯设备表
 * </p>
 *
 * @author 
 * @since 2026-06-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("devices")
public class Devices implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.NONE)
    private Long id;

    private String deviceName;

    /**
     * 硬件唯一序列号，MQTT主题标识
     */
    private String deviceSn;

    /**
     * 开关状态: ON-已开灯, OFF-已关灯
     */
    private String status;

    /**
     * 在线状态: ONLINE-在线, OFFLINE-离线
     */
    private String onlineStatus;

    /**
     * 控制模式: AUTO-跟随阈值自动开关, MANUAL-手动锁定（忽略 AUTO）
     */
    private String controlMode;

    /**
     * 编组名称：同名设备为一组；null/空表示未分组
     */
    private String groupName;

    /**
     * 纬度（GCJ-02，与高德底图一致）
     */
    private BigDecimal latitude;

    /**
     * 经度（GCJ-02，与高德底图一致）
     */
    private BigDecimal longitude;

    private LocalDateTime lastHeartbeatTime;

    private LocalDateTime createdAt;


}
