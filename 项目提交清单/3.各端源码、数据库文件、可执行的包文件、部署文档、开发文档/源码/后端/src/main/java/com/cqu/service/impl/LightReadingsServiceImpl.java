package com.cqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.entity.Devices;
import com.cqu.entity.LightReadings;
import com.cqu.mapper.DevicesMapper;
import com.cqu.mapper.LightReadingsMapper;
import com.cqu.config.MqttConfig;
import com.cqu.service.IControlLogsService;
import com.cqu.service.ILightReadingsService;
import com.cqu.service.IThresholdConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqu.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 光照强度采集记录（时序数据） 服务实现类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
@Slf4j
@Service
public class LightReadingsServiceImpl extends ServiceImpl<LightReadingsMapper, LightReadings> implements ILightReadingsService {

    @Autowired
    private DevicesMapper devicesMapper;

    @Autowired
    private IThresholdConfigService thresholdConfigService;

    @Autowired
    private IControlLogsService controlLogsService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MqttConfig mqttConfig;

    @Override
    public PageResult<LightReadingsVO> pageReadings(
            int page, int pageSize, Long deviceId, String groupName,
            LocalDateTime startTime, LocalDateTime endTime) {
        List<Long> deviceIds = resolveDeviceIds(deviceId, groupName);
        LambdaQueryWrapper<LightReadings> wrapper = new LambdaQueryWrapper<>();
        if (deviceIds != null) {
            if (deviceIds.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
            wrapper.in(LightReadings::getDeviceId, deviceIds);
        }
        wrapper.ge(startTime != null, LightReadings::getCreatedAt, startTime);
        wrapper.le(endTime != null, LightReadings::getCreatedAt, endTime);
        wrapper.orderByDesc(LightReadings::getCreatedAt);

        Page<LightReadings> pageResult = this.page(new Page<>(page, pageSize), wrapper);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(pageResult.getRecords());
        List<LightReadingsVO> records = pageResult.getRecords().stream()
                .map(r -> toLightReadingsVO(r, deviceNameMap.get(r.getDeviceId())))
                .collect(Collectors.toList());
        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    public LatestLightVO getLatestLight(Long deviceId) {
        LambdaQueryWrapper<LightReadings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LightReadings::getDeviceId, deviceId)
                .orderByDesc(LightReadings::getCreatedAt)
                .last("LIMIT 1");
        LightReadings reading = this.getOne(wrapper);

        if (reading == null) {
            throw new RuntimeException("该设备暂无光照数据");
        }

        return LatestLightVO.builder()
                .deviceId(String.valueOf(reading.getDeviceId()))
                .lightIntensity(reading.getLightIntensity())
                .createdAt(reading.getCreatedAt())
                .build();
    }

    @Override
    public List<TrendPointVO> getTrend(Long deviceId, String groupName, LocalDateTime startTime, LocalDateTime endTime) {
        List<Long> deviceIds = resolveDeviceIds(deviceId, groupName);
        if (deviceIds != null && deviceIds.isEmpty()) {
            return List.of();
        }

        // 单设备：原始点；总体/编组：按分钟平均
        boolean aggregate = deviceId == null;
        LambdaQueryWrapper<LightReadings> wrapper = new LambdaQueryWrapper<>();
        if (deviceIds != null) {
            wrapper.in(LightReadings::getDeviceId, deviceIds);
        }
        wrapper.ge(startTime != null, LightReadings::getCreatedAt, startTime)
                .le(endTime != null, LightReadings::getCreatedAt, endTime)
                .orderByAsc(LightReadings::getCreatedAt);

        List<LightReadings> list = this.list(wrapper);
        if (!aggregate) {
            return list.stream()
                    .map(r -> TrendPointVO.builder()
                            .time(r.getCreatedAt())
                            .value(r.getLightIntensity())
                            .build())
                    .collect(Collectors.toList());
        }

        Map<LocalDateTime, Map<Long, List<BigDecimal>>> buckets = new LinkedHashMap<>();
        for (LightReadings r : list) {
            LocalDateTime key = r.getCreatedAt().withSecond(0).withNano(0);
            buckets
                    .computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .computeIfAbsent(r.getDeviceId(), id -> new ArrayList<>())
                    .add(r.getLightIntensity());
        }
        List<TrendPointVO> points = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Map<Long, List<BigDecimal>>> e : buckets.entrySet()) {
            // 先按设备求该分钟均值，再对设备均值取平均，避免上报频率不同拉偏总体
            BigDecimal deviceSum = BigDecimal.ZERO;
            int deviceCount = 0;
            for (List<BigDecimal> samples : e.getValue().values()) {
                if (samples.isEmpty()) {
                    continue;
                }
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal v : samples) {
                    sum = sum.add(v);
                }
                deviceSum = deviceSum.add(
                        sum.divide(BigDecimal.valueOf(samples.size()), 4, RoundingMode.HALF_UP));
                deviceCount++;
            }
            if (deviceCount == 0) {
                continue;
            }
            BigDecimal avg = deviceSum.divide(BigDecimal.valueOf(deviceCount), 2, RoundingMode.HALF_UP);
            points.add(TrendPointVO.builder().time(e.getKey()).value(avg).build());
        }
        return points;
    }

    /** deviceId 优先；否则 groupName；都空则全部（返回 null 表示不限制） */
    private List<Long> resolveDeviceIds(Long deviceId, String groupName) {
        if (deviceId != null) {
            return List.of(deviceId);
        }
        if (groupName != null && !groupName.isBlank()) {
            return devicesMapper.selectList(
                            new LambdaQueryWrapper<Devices>()
                                    .eq(Devices::getGroupName, groupName.trim()))
                    .stream()
                    .map(Devices::getId)
                    .collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public String reportReading(Long deviceId, BigDecimal lightIntensity) {
        if (lightIntensity == null) {
            throw new RuntimeException("光照强度不能为空");
        }

        // 光照上报即视为心跳，刷新设备在线状态
        Devices device = devicesMapper.selectById(deviceId);
        if (device != null) {
            boolean wasOffline = !"ONLINE".equals(device.getOnlineStatus());
            device.setOnlineStatus("ONLINE");
            device.setLastHeartbeatTime(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
            devicesMapper.updateById(device);

            if (wasOffline) {
                // 首次光照 → 设备上线，推送 /topic/device-online
                Map<String, Object> onlineData = new LinkedHashMap<>();
                onlineData.put("deviceId", deviceId);
                onlineData.put("deviceName", device.getDeviceName());
                onlineData.put("onlineStatus", "ONLINE");
                onlineData.put("lastHeartbeatTime", device.getLastHeartbeatTime());
                WebSocketMessage onlineMsg = WebSocketMessage.builder()
                        .type("DEVICE_ONLINE_STATUS_CHANGED")
                        .timestamp(LocalDateTime.now())
                        .data(onlineData)
                        .build();
                log.info("WebSocket 推送 → /topic/device-online: 设备 {} ({}) 上线（光照上报触发）", deviceId, device.getDeviceName());
                messagingTemplate.convertAndSend("/topic/device-online", onlineMsg);
            }
        }

        LightReadings reading = new LightReadings();
        reading.setDeviceId(deviceId);
        reading.setLightIntensity(lightIntensity);
        this.save(reading);

        // WebSocket 推送光照数据到前端
        LatestLightVO vo = LatestLightVO.builder()
                .deviceId(String.valueOf(reading.getDeviceId()))
                .lightIntensity(reading.getLightIntensity())
                .createdAt(reading.getCreatedAt())
                .build();
        WebSocketMessage msg = WebSocketMessage.builder()
                .type("LIGHT_REPORTED")
                .timestamp(LocalDateTime.now())
                .data(vo)
                .build();
        log.info("WebSocket 推送 → /topic/light-readings: 设备 {} 光照值={}", deviceId, lightIntensity);
        messagingTemplate.convertAndSend("/topic/light-readings", msg);

        // 光照阈值自动开关灯判定，返回下发给硬件的指令
        String command = checkAndAutoControl(deviceId, lightIntensity);

        // 通过 MQTT 下发自动开关指令给硬件（成功后再记 PENDING）
        if (!"NONE".equals(command) && device != null) {
            if (mqttConfig.publishCommand(device.getDeviceSn(), command)) {
                String expected = command.endsWith("ON") ? "ON" : "OFF";
                controlLogsService.recordPendingCommand(deviceId, command, "AUTO", expected);
            } else {
                log.warn("自动开关指令 MQTT 下发失败: deviceSn={} command={}", device.getDeviceSn(), command);
            }
        }

        return command;
    }

    /**
     * 光照阈值自动开关灯判定（事件驱动：光照数据来一条判一条）
     * 光照 < 开灯阈值 → 自动开灯；光照 > 关灯阈值 → 自动关灯
     */
    private String checkAndAutoControl(Long deviceId, BigDecimal lightIntensity) {
        // 获取设备当前状态
        Devices device = devicesMapper.selectById(deviceId);
        if (device == null) {
            log.warn("自动开关判定：设备 {} 不存在", deviceId);
            return "NONE";
        }

        // 手动模式：不跟随光照自动开关（需前端点「恢复自动」）
        if ("MANUAL".equals(device.getControlMode())) {
            log.debug("自动开关跳过: 设备 {} 处于 MANUAL 模式", deviceId);
            return "NONE";
        }

        // 获取生效阈值（设备覆盖 > 编组覆盖 > 全局）
        EffectiveThresholdVO effective = thresholdConfigService.resolveEffective(deviceId);
        BigDecimal thresholdOn = effective.getLightThresholdOn();
        BigDecimal thresholdOff = effective.getLightThresholdOff();
        log.debug("自动开关阈值来源={} key={} on={} off={}",
                effective.getSource(), effective.getSourceKey(), thresholdOn, thresholdOff);

        String currentStatus = device.getStatus();
        String targetStatus = null;
        String command = null;

        // 光照低于开灯阈值 → 自动开灯
        if (lightIntensity.compareTo(thresholdOn) < 0 && !"ON".equals(currentStatus)) {
            targetStatus = "ON";
            command = "AUTO_ON";
        }
        // 光照高于关灯阈值 → 自动关灯
        else if (lightIntensity.compareTo(thresholdOff) > 0 && !"OFF".equals(currentStatus)) {
            targetStatus = "OFF";
            command = "AUTO_OFF";
        }

        if (targetStatus == null) {
            return "NONE"; // 无需操作
        }

        // 更新设备开关状态
        String oldStatus = currentStatus;
        device.setStatus(targetStatus);
        devicesMapper.updateById(device);

        log.info("自动开关: 设备 {} 光照={}, {} → {}", deviceId, lightIntensity, oldStatus, targetStatus);

        // WebSocket 推送设备状态变更
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", deviceId);
        data.put("deviceName", device.getDeviceName());
        data.put("oldStatus", oldStatus);
        data.put("status", targetStatus);
        WebSocketMessage msg = WebSocketMessage.builder()
                .type("DEVICE_STATUS_CHANGED")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
        log.info("WebSocket 推送 → /topic/device-status: 设备 {} ({}) 自动开关 {} → {}", deviceId, device.getDeviceName(), oldStatus, targetStatus);
        messagingTemplate.convertAndSend("/topic/device-status", msg);

        return command;
    }

    /**
     * 从光照记录列表中提取所有设备ID，批量查询设备名称
     */
    private Map<Long, String> buildDeviceNameMap(List<LightReadings> readings) {
        List<Long> deviceIds = readings.stream()
                .map(LightReadings::getDeviceId)
                .distinct()
                .collect(Collectors.toList());

        if (deviceIds.isEmpty()) {
            return Map.of();
        }

        return devicesMapper.selectBatchIds(deviceIds).stream()
                .collect(Collectors.toMap(Devices::getId, Devices::getDeviceName));
    }

    private LightReadingsVO toLightReadingsVO(LightReadings reading, String deviceName) {
        return LightReadingsVO.builder()
                .id(String.valueOf(reading.getId()))
                .deviceId(String.valueOf(reading.getDeviceId()))
                .deviceName(deviceName)
                .lightIntensity(reading.getLightIntensity())
                .createdAt(reading.getCreatedAt())
                .build();
    }
}
