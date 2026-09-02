package com.cqu.service;

import com.cqu.entity.Devices;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cqu.vo.*;

import java.math.BigDecimal;

/**
 * <p>
 * 路灯设备表 服务类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
public interface IDevicesService extends IService<Devices> {

    /**
     * 设备分页列表（支持按名称/开关状态/在线状态筛选）
     */
    PageResult<DeviceVO> pageDevices(int page, int pageSize, String deviceName, String status, String onlineStatus);

    /**
     * 设备详情（含最新光照值和活跃告警数）
     */
    DeviceDetailVO getDeviceDetail(Long id);

    /**
     * 添加设备；经纬度可空，须成对出现
     */
    void addDevice(String deviceName, String deviceSn, BigDecimal latitude, BigDecimal longitude);

    /**
     * 编辑设备名称
     */
    void updateDevice(Long id, String deviceName);

    /**
     * 标定或清除设备地图坐标；两者都为 null 则清除
     */
    void updateDeviceLocation(Long id, BigDecimal latitude, BigDecimal longitude);

    /**
     * 删除设备（同时清理关联的光照记录和告警日志）
     */
    void deleteDevice(Long id);

    /**
     * 设备概览统计
     */
    DeviceStatisticsVO getStatistics();

    /**
     * 硬件状态回传（硬件执行开关指令后回传最终状态）
     *
     * @param deviceId 设备ID
     * @param status   开关状态：ON / OFF
     */
    void updateDeviceStatus(Long deviceId, String status);

    /**
     * 设备心跳上报（硬件定期发送心跳信号）
     *
     * @param deviceId 设备ID
     */
    void updateHeartbeat(Long deviceId);

    /**
     * 手动开关灯控制（进入 MANUAL 模式，忽略光照自动开关）
     *
     * @param deviceId 设备ID
     * @param status   目标开关状态：ON / OFF
     * @return 下发给硬件的指令：MANUAL_ON / MANUAL_OFF
     */
    String switchDevice(Long deviceId, String status);

    /**
     * 设置控制模式：AUTO 恢复阈值联动；MANUAL 手动锁定
     */
    void setControlMode(Long deviceId, String mode);

    /**
     * 设置设备编组；groupName 为空则移出分组
     */
    void setDeviceGroup(Long deviceId, String groupName);

    /**
     * 组内统一开关（进入 MANUAL）
     *
     * @return 成功下发的设备数量
     */
    int switchGroup(String groupName, String status);

    /**
     * 组内统一设置控制模式
     *
     * @return 受影响设备数量
     */
    int setGroupControlMode(String groupName, String mode);
}
