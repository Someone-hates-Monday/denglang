package com.cqu.service;

import com.cqu.entity.AlarmLogs;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cqu.vo.*;

/**
 * <p>
 * 告警记录表 服务类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
public interface IAlarmLogsService extends IService<AlarmLogs> {

    /**
     * 告警分页列表（支持按设备/类型/状态筛选，按产生时间倒序）
     */
    PageResult<AlarmLogVO> pageAlarms(int page, int pageSize, Long deviceId, String alarmType, String status);

    /**
     * 告警详情
     */
    AlarmLogVO getAlarmDetail(Long id);

    /**
     * 解决告警（ACTIVE → RESOLVED）
     */
    void resolveAlarm(Long id);

    /**
     * 告警统计（活跃总数 + 按类型分组）
     */
    AlarmStatisticsVO getStatistics();

    /**
     * 创建告警（由硬件/系统触发，如设备离线、光照异常）
     *
     * @param deviceId  关联设备ID
     * @param alarmType 告警类型（如 OFFLINE、LIGHT_ABNORMAL）
     * @param message   告警详情描述
     */
    void createAlarm(Long deviceId, String alarmType, String message);
}
