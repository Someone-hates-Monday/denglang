package com.cqu.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqu.entity.Devices;
import com.cqu.entity.ThresholdConfig;
import com.cqu.mapper.DevicesMapper;
import com.cqu.mapper.ThresholdConfigMapper;
import com.cqu.service.IAlarmLogsService;
import com.cqu.vo.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 心跳超时检测定时任务
 * 定期扫描所有在线设备，心跳超时则标记离线、创建告警、推送通知
 */
@Slf4j
@Component
public class HeartbeatCheckTask {

    @Autowired
    private DevicesMapper devicesMapper;

    @Autowired
    private ThresholdConfigMapper thresholdConfigMapper;

    @Autowired
    private IAlarmLogsService alarmLogsService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 每 30 秒扫描一次
     */
    @Scheduled(fixedRate = 30_000)
    public void checkHeartbeat() {
        // 获取心跳超时配置
        ThresholdConfig config = thresholdConfigMapper.selectById(1L);
        if (config == null) {
            return;
        }
        int timeoutSeconds = config.getHeartbeatTimeout();
        // 与 JDBC timestamp(无时区) 约定：统一用上海本地墙钟，避免 PG 容器 UTC NOW 与 JVM 差 8h 误判离线
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        LocalDateTime deadline = now.minusSeconds(timeoutSeconds);

        // 查询所有在线设备
        List<Devices> onlineDevices = devicesMapper.selectList(
                new LambdaQueryWrapper<Devices>().eq(Devices::getOnlineStatus, "ONLINE"));

        for (Devices device : onlineDevices) {
            // 从未收到过心跳，或最后一次心跳已超时
            if (device.getLastHeartbeatTime() == null
                    || device.getLastHeartbeatTime().isBefore(deadline)) {
                // 标记离线
                device.setOnlineStatus("OFFLINE");
                devicesMapper.updateById(device);

                // 创建离线告警
                alarmLogsService.createAlarm(device.getId(), "OFFLINE",
                        "设备心跳超时，已自动标记离线");

                log.info("心跳超时: 设备 {} ({}) 已标记离线", device.getId(), device.getDeviceName());

                // WebSocket 推送离线状态变更
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("deviceId", device.getId());
                data.put("deviceName", device.getDeviceName());
                data.put("onlineStatus", "OFFLINE");
                data.put("lastHeartbeatTime", device.getLastHeartbeatTime());
                WebSocketMessage msg = WebSocketMessage.builder()
                        .type("DEVICE_ONLINE_STATUS_CHANGED")
                        .timestamp(now)
                        .data(data)
                        .build();
                log.info("WebSocket 推送 → /topic/device-online: 设备 {} ({}) 离线（心跳超时 {}秒）", device.getId(), device.getDeviceName(), timeoutSeconds);
                messagingTemplate.convertAndSend("/topic/device-online", msg);
            }
        }
    }
}
