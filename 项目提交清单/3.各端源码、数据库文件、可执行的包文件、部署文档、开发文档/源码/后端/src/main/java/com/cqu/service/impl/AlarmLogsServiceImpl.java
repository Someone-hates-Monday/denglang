package com.cqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.entity.AlarmLogs;
import com.cqu.entity.Devices;
import com.cqu.mapper.AlarmLogsMapper;
import com.cqu.mapper.DevicesMapper;
import com.cqu.service.IAlarmLogsService;
import com.cqu.service.IControlLogsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqu.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 告警记录表 服务实现类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
@Slf4j
@Service
public class AlarmLogsServiceImpl extends ServiceImpl<AlarmLogsMapper, AlarmLogs> implements IAlarmLogsService {

    @Autowired
    private DevicesMapper devicesMapper;

    @Autowired
    private IControlLogsService controlLogsService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public PageResult<AlarmLogVO> pageAlarms(int page, int pageSize, Long deviceId, String alarmType, String status) {
        LambdaQueryWrapper<AlarmLogs> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(deviceId != null, AlarmLogs::getDeviceId, deviceId);
        wrapper.eq(alarmType != null && !alarmType.isBlank(), AlarmLogs::getAlarmType, alarmType);
        wrapper.eq(status != null && !status.isBlank(), AlarmLogs::getStatus, status);
        wrapper.orderByDesc(AlarmLogs::getCreatedAt);

        Page<AlarmLogs> pageResult = this.page(new Page<>(page, pageSize), wrapper);

        // 批量获取设备名称
        Map<Long, String> deviceNameMap = buildDeviceNameMap(pageResult.getRecords());

        List<AlarmLogVO> records = pageResult.getRecords().stream()
                .map(a -> toAlarmLogVO(a, deviceNameMap.get(a.getDeviceId())))
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    public AlarmLogVO getAlarmDetail(Long id) {
        AlarmLogs alarm = this.getById(id);
        if (alarm == null) {
            throw new RuntimeException("告警记录不存在");
        }

        String deviceName = null;
        Devices device = devicesMapper.selectById(alarm.getDeviceId());
        if (device != null) {
            deviceName = device.getDeviceName();
        }

        return toAlarmLogVO(alarm, deviceName);
    }

    @Override
    public void resolveAlarm(Long id) {
        AlarmLogs alarm = this.getById(id);
        if (alarm == null) {
            throw new RuntimeException("告警记录不存在");
        }
        if (!"ACTIVE".equals(alarm.getStatus())) {
            throw new RuntimeException("该告警已被处理");
        }

        alarm.setStatus("RESOLVED");
        alarm.setResolvedAt(LocalDateTime.now());
        this.updateById(alarm);
        controlLogsService.recordLog(alarm.getDeviceId(), "RESOLVE_ALARM", "SUCCESS");
    }

    @Override
    public AlarmStatisticsVO getStatistics() {
        // 活跃告警总数
        Long activeCount = this.lambdaQuery()
                .eq(AlarmLogs::getStatus, "ACTIVE")
                .count();

        // 按类型分组统计
        List<AlarmLogs> activeAlarms = this.lambdaQuery()
                .eq(AlarmLogs::getStatus, "ACTIVE")
                .list();

        List<AlarmStatisticsVO.AlarmTypeCount> byType = activeAlarms.stream()
                .collect(Collectors.groupingBy(AlarmLogs::getAlarmType, Collectors.counting()))
                .entrySet().stream()
                .map(e -> AlarmStatisticsVO.AlarmTypeCount.builder()
                        .alarmType(e.getKey())
                        .count(String.valueOf(e.getValue()))
                        .build())
                .collect(Collectors.toList());

        return AlarmStatisticsVO.builder()
                .activeCount(String.valueOf(activeCount))
                .byType(byType)
                .build();
    }

    @Override
    public void createAlarm(Long deviceId, String alarmType, String message) {
        if (deviceId == null || alarmType == null || alarmType.isBlank()) {
            throw new RuntimeException("设备ID和告警类型不能为空");
        }

        // 同设备同类型已有 ACTIVE 告警则不重复建（避免心跳抖动刷屏）
        long activeSame = this.lambdaQuery()
                .eq(AlarmLogs::getDeviceId, deviceId)
                .eq(AlarmLogs::getAlarmType, alarmType)
                .eq(AlarmLogs::getStatus, "ACTIVE")
                .count();
        if (activeSame > 0) {
            return;
        }

        AlarmLogs alarm = new AlarmLogs();
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(alarmType);
        alarm.setMessage(message);
        alarm.setStatus("ACTIVE");
        this.save(alarm);

        // WebSocket 推送新告警到前端
        String deviceName = null;
        Devices device = devicesMapper.selectById(deviceId);
        if (device != null) {
            deviceName = device.getDeviceName();
        }

        AlarmLogVO vo = toAlarmLogVO(alarm, deviceName);
        WebSocketMessage msg = WebSocketMessage.builder()
                .type("ALARM_CREATED")
                .timestamp(LocalDateTime.now())
                .data(vo)
                .build();
        log.info("WebSocket 推送 → /topic/alarms: 设备 {} ({}) 告警类型={} 内容={}", deviceId, deviceName, alarmType, message);
        messagingTemplate.convertAndSend("/topic/alarms", msg);
    }

    private Map<Long, String> buildDeviceNameMap(List<AlarmLogs> alarms) {
        List<Long> deviceIds = alarms.stream()
                .map(AlarmLogs::getDeviceId)
                .distinct()
                .collect(Collectors.toList());

        if (deviceIds.isEmpty()) {
            return Map.of();
        }

        return devicesMapper.selectBatchIds(deviceIds).stream()
                .collect(Collectors.toMap(Devices::getId, Devices::getDeviceName));
    }

    private AlarmLogVO toAlarmLogVO(AlarmLogs alarm, String deviceName) {
        return AlarmLogVO.builder()
                .id(String.valueOf(alarm.getId()))
                .deviceId(String.valueOf(alarm.getDeviceId()))
                .deviceName(deviceName)
                .alarmType(alarm.getAlarmType())
                .message(alarm.getMessage())
                .status(alarm.getStatus())
                .createdAt(alarm.getCreatedAt())
                .resolvedAt(alarm.getResolvedAt())
                .build();
    }
}
