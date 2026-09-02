package com.cqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.entity.AlarmLogs;
import com.cqu.entity.ControlLogs;
import com.cqu.entity.Devices;
import com.cqu.entity.LightReadings;
import com.cqu.mapper.AlarmLogsMapper;
import com.cqu.mapper.ControlLogsMapper;
import com.cqu.mapper.DevicesMapper;
import com.cqu.mapper.LightReadingsMapper;
import com.cqu.config.MqttConfig;
import com.cqu.service.IControlLogsService;
import com.cqu.service.IDevicesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqu.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 路灯设备表 服务实现类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
@Slf4j
@Service
public class DevicesServiceImpl extends ServiceImpl<DevicesMapper, Devices> implements IDevicesService {

    @Autowired
    private LightReadingsMapper lightReadingsMapper;

    @Autowired
    private AlarmLogsMapper alarmLogsMapper;

    @Autowired
    private ControlLogsMapper controlLogsMapper;

    @Autowired
    private IControlLogsService controlLogsService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MqttConfig mqttConfig;

    @Override
    public PageResult<DeviceVO> pageDevices(int page, int pageSize, String deviceName, String status, String onlineStatus) {
        LambdaQueryWrapper<Devices> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(deviceName != null && !deviceName.isBlank(), Devices::getDeviceName, deviceName);
        wrapper.eq(status != null && !status.isBlank(), Devices::getStatus, status);
        wrapper.eq(onlineStatus != null && !onlineStatus.isBlank(), Devices::getOnlineStatus, onlineStatus);
        // 按 ID 稳定排序，避免开关刷新后一二号路灯位置对调
        wrapper.orderByAsc(Devices::getId);

        Page<Devices> pageResult = this.page(new Page<>(page, pageSize), wrapper);

        List<DeviceVO> records = pageResult.getRecords().stream()
                .map(this::toDeviceVO)
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    public DeviceDetailVO getDeviceDetail(Long id) {
        Devices device = this.getById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        // 查询最新光照值
        LambdaQueryWrapper<LightReadings> lightWrapper = new LambdaQueryWrapper<>();
        lightWrapper.eq(LightReadings::getDeviceId, id)
                .orderByDesc(LightReadings::getCreatedAt)
                .last("LIMIT 1");
        LightReadings latestLight = lightReadingsMapper.selectOne(lightWrapper);

        // 查询活跃告警数
        LambdaQueryWrapper<AlarmLogs> alarmWrapper = new LambdaQueryWrapper<>();
        alarmWrapper.eq(AlarmLogs::getDeviceId, id)
                .eq(AlarmLogs::getStatus, "ACTIVE");
        Long activeAlarmCount = alarmLogsMapper.selectCount(alarmWrapper);

        String expected = resolveExpectedStatus(device.getId());
        return DeviceDetailVO.builder()
                .id(String.valueOf(device.getId()))
                .deviceName(device.getDeviceName())
                .deviceSn(device.getDeviceSn())
                .status(device.getStatus())
                .onlineStatus(device.getOnlineStatus())
                .controlMode(resolveControlMode(device.getControlMode()))
                .groupName(normalizeGroupName(device.getGroupName()))
                .latitude(device.getLatitude())
                .longitude(device.getLongitude())
                .expectedStatus(expected)
                .statusMatch(isStatusMatch(device.getStatus(), expected))
                .lastHeartbeatTime(device.getLastHeartbeatTime())
                .latestLightIntensity(latestLight != null ? latestLight.getLightIntensity() : null)
                .activeAlarmCount(String.valueOf(activeAlarmCount))
                .createdAt(device.getCreatedAt())
                .build();
    }

    @Override
    public void addDevice(String deviceName, String deviceSn, BigDecimal latitude, BigDecimal longitude) {
        if (deviceName == null || deviceName.isBlank()) {
            throw new RuntimeException("设备名称不能为空");
        }
        if (deviceSn == null || deviceSn.isBlank()) {
            throw new RuntimeException("设备序列号不能为空");
        }

        // 检查序列号是否已存在
        LambdaQueryWrapper<Devices> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Devices::getDeviceSn, deviceSn);
        if (this.count(wrapper) > 0) {
            throw new RuntimeException("设备序列号已存在");
        }

        Devices device = new Devices();
        device.setDeviceName(deviceName);
        device.setDeviceSn(deviceSn);
        device.setControlMode("AUTO");
        applyLocation(device, latitude, longitude);
        this.save(device);
        controlLogsService.recordLog(device.getId(), "ADD_DEVICE", "SUCCESS");
    }

    @Override
    public void updateDevice(Long id, String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            throw new RuntimeException("设备名称不能为空");
        }

        Devices device = this.getById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        device.setDeviceName(deviceName);
        this.updateById(device);
        controlLogsService.recordLog(id, "UPDATE_DEVICE", "SUCCESS");
    }

    @Override
    public void updateDeviceLocation(Long id, BigDecimal latitude, BigDecimal longitude) {
        Devices device = this.getById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }
        applyLocation(device, latitude, longitude);
        // 允许把坐标清成 null（updateById 默认忽略 null）
        this.lambdaUpdate()
                .eq(Devices::getId, id)
                .set(Devices::getLatitude, device.getLatitude())
                .set(Devices::getLongitude, device.getLongitude())
                .update();
        controlLogsService.recordLog(id, "UPDATE_LOCATION", "SUCCESS");
    }

    @Override
    public void deleteDevice(Long id) {
        Devices device = this.getById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        // 删除关联的光照记录
        LambdaQueryWrapper<LightReadings> lightWrapper = new LambdaQueryWrapper<>();
        lightWrapper.eq(LightReadings::getDeviceId, id);
        lightReadingsMapper.delete(lightWrapper);

        // 删除关联的告警日志
        LambdaQueryWrapper<AlarmLogs> alarmWrapper = new LambdaQueryWrapper<>();
        alarmWrapper.eq(AlarmLogs::getDeviceId, id);
        alarmLogsMapper.delete(alarmWrapper);

        this.removeById(id);
        controlLogsService.recordLog(id, "DELETE_DEVICE", "SUCCESS");
    }

    @Override
    public DeviceStatisticsVO getStatistics() {
        Long totalCount = this.count();
        Long onlineCount = this.lambdaQuery().eq(Devices::getOnlineStatus, "ONLINE").count();
        Long offlineCount = this.lambdaQuery().eq(Devices::getOnlineStatus, "OFFLINE").count();
        Long onCount = this.lambdaQuery().eq(Devices::getStatus, "ON").count();
        Long offCount = this.lambdaQuery().eq(Devices::getStatus, "OFF").count();

        return DeviceStatisticsVO.builder()
                .totalCount(String.valueOf(totalCount))
                .onlineCount(String.valueOf(onlineCount))
                .offlineCount(String.valueOf(offlineCount))
                .onCount(String.valueOf(onCount))
                .offCount(String.valueOf(offCount))
                .build();
    }

    @Override
    public void updateDeviceStatus(Long deviceId, String status) {
        if (deviceId == null || status == null || status.isBlank()) {
            throw new RuntimeException("设备ID和状态不能为空");
        }
        if (!"ON".equals(status) && !"OFF".equals(status)) {
            throw new RuntimeException("状态值只能为 ON 或 OFF");
        }

        Devices device = this.getById(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        String oldStatus = device.getStatus();
        device.setStatus(status);
        this.updateById(device);

        // 匹配最近一条 PENDING 指令并标记 SUCCESS
        controlLogsService.confirmPendingByStatus(deviceId, status);

        // WebSocket 推送设备状态变更
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", deviceId);
        data.put("deviceName", device.getDeviceName());
        data.put("oldStatus", oldStatus);
        data.put("status", status);
        WebSocketMessage msg = WebSocketMessage.builder()
                .type("DEVICE_STATUS_CHANGED")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
        log.info("WebSocket 推送 → /topic/device-status: 设备 {} ({}) 硬件回传状态 {} → {}", deviceId, device.getDeviceName(), oldStatus, status);
        messagingTemplate.convertAndSend("/topic/device-status", msg);
    }

    @Override
    public void updateHeartbeat(Long deviceId) {
        if (deviceId == null) {
            throw new RuntimeException("设备ID不能为空");
        }

        Devices device = this.getById(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        boolean wasOffline = !"ONLINE".equals(device.getOnlineStatus());

        device.setOnlineStatus("ONLINE");
        device.setLastHeartbeatTime(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        this.updateById(device);

        // 超时离线检测已由 HeartbeatCheckTask 定时任务实现（每30秒扫描）

        // WebSocket 推送在线状态变更（仅状态变更时推送：从离线恢复到在线）
        if (wasOffline) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deviceId", deviceId);
            data.put("deviceName", device.getDeviceName());
            data.put("onlineStatus", "ONLINE");
            data.put("lastHeartbeatTime", device.getLastHeartbeatTime());
            WebSocketMessage msg = WebSocketMessage.builder()
                    .type("DEVICE_ONLINE_STATUS_CHANGED")
                    .timestamp(LocalDateTime.now())
                    .data(data)
                    .build();
            log.info("WebSocket 推送 → /topic/device-online: 设备 {} ({}) 上线（心跳恢复触发）", deviceId, device.getDeviceName());
            messagingTemplate.convertAndSend("/topic/device-online", msg);
        }
    }

    @Override
    public String switchDevice(Long deviceId, String status) {
        if (deviceId == null || status == null || status.isBlank()) {
            throw new RuntimeException("设备ID和状态不能为空");
        }
        if (!"ON".equals(status) && !"OFF".equals(status)) {
            throw new RuntimeException("状态值只能为 ON 或 OFF");
        }

        Devices device = this.getById(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        String oldStatus = device.getStatus();
        device.setStatus(status);
        // 手动操作进入 MANUAL：后续光照上报不再触发 AUTO_ON / AUTO_OFF
        device.setControlMode("MANUAL");
        this.updateById(device);

        // WebSocket 推送设备状态变更
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", deviceId);
        data.put("deviceName", device.getDeviceName());
        data.put("oldStatus", oldStatus);
        data.put("status", status);
        data.put("controlMode", "MANUAL");
        WebSocketMessage msg = WebSocketMessage.builder()
                .type("DEVICE_STATUS_CHANGED")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
        log.info("WebSocket 推送 → /topic/device-status: 设备 {} ({}) 手动开关 {} → {}（进入 MANUAL）",
                deviceId, device.getDeviceName(), oldStatus, status);
        messagingTemplate.convertAndSend("/topic/device-status", msg);

        // 先下发 MQTT，成功后再记 PENDING（避免 Broker 未连时误报 30s 超时）
        String command = "MANUAL_" + status;
        boolean published = mqttConfig.publishCommand(device.getDeviceSn(), command);
        if (!published) {
            throw new RuntimeException("MQTT 未连接，指令未下发到板端，请检查 EMQX 与后端 MQTT 配置");
        }
        controlLogsService.recordPendingCommand(deviceId, command, "MANUAL", status);

        return command;
    }

    @Override
    public void setControlMode(Long deviceId, String mode) {
        if (deviceId == null || mode == null || mode.isBlank()) {
            throw new RuntimeException("设备ID和模式不能为空");
        }
        if (!"AUTO".equals(mode) && !"MANUAL".equals(mode)) {
            throw new RuntimeException("模式只能为 AUTO 或 MANUAL");
        }

        Devices device = this.getById(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        String oldMode = resolveControlMode(device.getControlMode());
        device.setControlMode(mode);
        this.updateById(device);

        controlLogsService.recordLog(deviceId, "MODE_" + mode, "SUCCESS", "MANUAL");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", deviceId);
        data.put("deviceName", device.getDeviceName());
        data.put("oldControlMode", oldMode);
        data.put("controlMode", mode);
        data.put("status", device.getStatus());
        WebSocketMessage msg = WebSocketMessage.builder()
                .type("DEVICE_CONTROL_MODE_CHANGED")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
        log.info("设备 {} ({}) 控制模式 {} → {}", deviceId, device.getDeviceName(), oldMode, mode);
        messagingTemplate.convertAndSend("/topic/device-status", msg);
    }

    @Override
    public void setDeviceGroup(Long deviceId, String groupName) {
        if (deviceId == null) {
            throw new RuntimeException("设备ID不能为空");
        }
        Devices device = this.getById(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }
        String normalized = normalizeGroupName(groupName);
        this.lambdaUpdate()
                .eq(Devices::getId, deviceId)
                .set(Devices::getGroupName, normalized)
                .update();
        controlLogsService.recordLog(deviceId,
                normalized == null ? "UNGROUP" : "GROUP_" + normalized,
                "SUCCESS", "MANUAL");
        log.info("设备 {} ({}) 编组 → {}", deviceId, device.getDeviceName(),
                normalized == null ? "（未分组）" : normalized);
    }

    @Override
    public int switchGroup(String groupName, String status) {
        String normalized = requireGroupName(groupName);
        if (status == null || status.isBlank() || (!status.equals("ON") && !status.equals("OFF"))) {
            throw new RuntimeException("开关状态必须为 ON 或 OFF");
        }
        List<Devices> members = listByGroup(normalized);
        if (members.isEmpty()) {
            throw new RuntimeException("编组不存在或组内无设备: " + normalized);
        }
        int ok = 0;
        for (Devices d : members) {
            switchDevice(d.getId(), status);
            ok++;
        }
        log.info("编组「{}」统一开关 → {}，共 {} 台", normalized, status, ok);
        return ok;
    }

    @Override
    public int setGroupControlMode(String groupName, String mode) {
        String normalized = requireGroupName(groupName);
        if (mode == null || mode.isBlank() || (!mode.equals("AUTO") && !mode.equals("MANUAL"))) {
            throw new RuntimeException("控制模式必须为 AUTO 或 MANUAL");
        }
        List<Devices> members = listByGroup(normalized);
        if (members.isEmpty()) {
            throw new RuntimeException("编组不存在或组内无设备: " + normalized);
        }
        int ok = 0;
        for (Devices d : members) {
            setControlMode(d.getId(), mode);
            ok++;
        }
        log.info("编组「{}」统一模式 → {}，共 {} 台", normalized, mode, ok);
        return ok;
    }

    private List<Devices> listByGroup(String groupName) {
        return this.lambdaQuery()
                .eq(Devices::getGroupName, groupName)
                .orderByAsc(Devices::getId)
                .list();
    }

    private static String requireGroupName(String groupName) {
        String normalized = normalizeGroupName(groupName);
        if (normalized == null) {
            throw new RuntimeException("编组名称不能为空");
        }
        return normalized;
    }

    /** trim；空串视为 null（未分组） */
    private static String normalizeGroupName(String groupName) {
        if (groupName == null) {
            return null;
        }
        String t = groupName.trim();
        return t.isEmpty() ? null : t;
    }

    private static String resolveControlMode(String mode) {
        return (mode == null || mode.isBlank()) ? "AUTO" : mode;
    }

    private DeviceVO toDeviceVO(Devices device) {
        String expected = resolveExpectedStatus(device.getId());
        return DeviceVO.builder()
                .id(String.valueOf(device.getId()))
                .deviceName(device.getDeviceName())
                .deviceSn(device.getDeviceSn())
                .status(device.getStatus())
                .onlineStatus(device.getOnlineStatus())
                .controlMode(resolveControlMode(device.getControlMode()))
                .groupName(normalizeGroupName(device.getGroupName()))
                .latitude(device.getLatitude())
                .longitude(device.getLongitude())
                .expectedStatus(expected)
                .statusMatch(isStatusMatch(device.getStatus(), expected))
                .lastHeartbeatTime(device.getLastHeartbeatTime())
                .createdAt(device.getCreatedAt())
                .build();
    }

    /** 经纬度须成对；都为 null 表示未标定 / 清除 */
    private static void applyLocation(Devices device, BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null && longitude == null) {
            device.setLatitude(null);
            device.setLongitude(null);
            return;
        }
        if (latitude == null || longitude == null) {
            throw new RuntimeException("经纬度必须同时填写");
        }
        if (latitude.compareTo(new BigDecimal("-90")) < 0 || latitude.compareTo(new BigDecimal("90")) > 0) {
            throw new RuntimeException("纬度范围应为 -90~90");
        }
        if (longitude.compareTo(new BigDecimal("-180")) < 0 || longitude.compareTo(new BigDecimal("180")) > 0) {
            throw new RuntimeException("经度范围应为 -180~180");
        }
        device.setLatitude(latitude);
        device.setLongitude(longitude);
    }

    /** 最近一次 SUCCESS 且带期望状态的指令 */
    private String resolveExpectedStatus(Long deviceId) {
        ControlLogs last = controlLogsMapper.selectOne(
                new LambdaQueryWrapper<ControlLogs>()
                        .eq(ControlLogs::getDeviceId, deviceId)
                        .eq(ControlLogs::getExecutionStatus, "SUCCESS")
                        .in(ControlLogs::getExpectedStatus, "ON", "OFF")
                        .orderByDesc(ControlLogs::getCreatedAt)
                        .last("LIMIT 1"));
        return last != null ? last.getExpectedStatus() : null;
    }

    private static boolean isStatusMatch(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equalsIgnoreCase(actual);
    }
}
