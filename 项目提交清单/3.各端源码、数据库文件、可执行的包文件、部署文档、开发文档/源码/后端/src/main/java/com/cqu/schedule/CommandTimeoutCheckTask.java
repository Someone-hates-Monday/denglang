package com.cqu.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqu.constant.StreetLightConstants;
import com.cqu.entity.AlarmLogs;
import com.cqu.entity.ControlLogs;
import com.cqu.entity.Devices;
import com.cqu.mapper.ControlLogsMapper;
import com.cqu.mapper.DevicesMapper;
import com.cqu.service.IAlarmLogsService;
import com.cqu.service.IControlLogsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指令执行超时检测：PENDING 超过阈值未收到 status 回传 → TIMEOUT + COMMAND_TIMEOUT 告警
 */
@Slf4j
@Component
public class CommandTimeoutCheckTask {

    @Autowired
    private ControlLogsMapper controlLogsMapper;

    @Autowired
    private IControlLogsService controlLogsService;

    @Autowired
    private IAlarmLogsService alarmLogsService;

    @Autowired
    private DevicesMapper devicesMapper;

    @Scheduled(fixedRate = 15_000)
    public void checkCommandTimeout() {
        LocalDateTime deadline = LocalDateTime.now()
                .minusSeconds(StreetLightConstants.COMMAND_ACK_TIMEOUT_SECONDS);

        List<ControlLogs> pendingLogs = controlLogsMapper.selectList(
                new LambdaQueryWrapper<ControlLogs>()
                        .eq(ControlLogs::getExecutionStatus, StreetLightConstants.EXEC_PENDING)
                        .lt(ControlLogs::getCreatedAt, deadline));

        for (ControlLogs pending : pendingLogs) {
            pending.setExecutionStatus(StreetLightConstants.EXEC_TIMEOUT);
            pending.setResult(StreetLightConstants.EXEC_TIMEOUT);
            controlLogsService.updateById(pending);

            Long deviceId = pending.getDeviceId();
            if (deviceId == null) {
                continue;
            }

            // 同设备已有活跃 COMMAND_TIMEOUT 则跳过，避免刷屏
            Long activeCount = alarmLogsService.lambdaQuery()
                    .eq(AlarmLogs::getDeviceId, deviceId)
                    .eq(AlarmLogs::getAlarmType, StreetLightConstants.ALARM_COMMAND_TIMEOUT)
                    .eq(AlarmLogs::getStatus, "ACTIVE")
                    .count();
            if (activeCount > 0) {
                continue;
            }

            Devices device = devicesMapper.selectById(deviceId);
            String deviceLabel = device != null ? device.getDeviceName() : String.valueOf(deviceId);
            String message = String.format(
                    "指令 %s 下发后 %d 秒内未收到板端 status 回传（期望 %s）",
                    pending.getCommand(),
                    StreetLightConstants.COMMAND_ACK_TIMEOUT_SECONDS,
                    pending.getExpectedStatus());

            alarmLogsService.createAlarm(deviceId, StreetLightConstants.ALARM_COMMAND_TIMEOUT, message);
            log.warn("指令超时: 设备 {} ({}) command={} expected={}",
                    deviceId, deviceLabel, pending.getCommand(), pending.getExpectedStatus());
        }
    }
}
