package com.cqu.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqu.entity.AlarmLogs;
import com.cqu.entity.Devices;
import com.cqu.mapper.AlarmLogsMapper;
import com.cqu.mapper.DevicesMapper;
import com.cqu.vo.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地兜底保活（默认关闭）。有 Docker fleet-sim 时不要开启，否则与 MQTT 模拟双轨冲突。
 * 真机 SN-RM-001 永远不保活，只靠板端 MQTT 心跳。
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "streetlight.demo", name = "mock-keepalive", havingValue = "true")
public class DemoMockKeepaliveTask {

    private static final String HARDWARE_SN = "SN-RM-001";
    private static final ZoneId CLOCK = ZoneId.of("Asia/Shanghai");

    @Autowired
    private DevicesMapper devicesMapper;

    @Autowired
    private AlarmLogsMapper alarmLogsMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 60_000, initialDelay = 5_000)
    public void keepaliveMocks() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        List<Devices> mocks = devicesMapper.selectList(
                new LambdaQueryWrapper<Devices>().ne(Devices::getDeviceSn, HARDWARE_SN));
        int recovered = 0;
        for (Devices device : mocks) {
            boolean wasOffline = !"ONLINE".equals(device.getOnlineStatus());
            device.setOnlineStatus("ONLINE");
            device.setLastHeartbeatTime(now);
            devicesMapper.updateById(device);
            if (wasOffline) {
                recovered++;
                resolveActiveOffline(device.getId());
                pushOnline(device, now);
            }
        }
        if (recovered > 0) {
            log.info("本地模拟灯保活：恢复 {} 台为 ONLINE", recovered);
        }
    }

    private void resolveActiveOffline(Long deviceId) {
        List<AlarmLogs> actives = alarmLogsMapper.selectList(
                new LambdaQueryWrapper<AlarmLogs>()
                        .eq(AlarmLogs::getDeviceId, deviceId)
                        .eq(AlarmLogs::getAlarmType, "OFFLINE")
                        .eq(AlarmLogs::getStatus, "ACTIVE"));
        LocalDateTime now = LocalDateTime.now(CLOCK);
        for (AlarmLogs alarm : actives) {
            alarm.setStatus("RESOLVED");
            alarm.setResolvedAt(now);
            alarmLogsMapper.updateById(alarm);
        }
    }

    private void pushOnline(Devices device, LocalDateTime now) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", device.getId());
        data.put("deviceName", device.getDeviceName());
        data.put("onlineStatus", "ONLINE");
        data.put("lastHeartbeatTime", now);
        WebSocketMessage msg = WebSocketMessage.builder()
                .type("DEVICE_ONLINE_STATUS_CHANGED")
                .timestamp(now)
                .data(data)
                .build();
        messagingTemplate.convertAndSend("/topic/device-online", msg);
    }
}
