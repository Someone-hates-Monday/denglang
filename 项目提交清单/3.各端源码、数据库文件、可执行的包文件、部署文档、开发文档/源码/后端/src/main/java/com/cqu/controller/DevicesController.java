package com.cqu.controller;

import com.cqu.service.IDevicesService;
import com.cqu.vo.Result;
import com.cqu.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/devices")
public class DevicesController {

    @Autowired
    private IDevicesService devicesService;

    /**
     * 设备分页列表
     */
    @GetMapping
    public Result<PageResult<DeviceVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String onlineStatus) {
        log.info("查询设备列表: page={}, pageSize={}, deviceName={}, status={}, onlineStatus={}",
                page, pageSize, deviceName, status, onlineStatus);
        PageResult<DeviceVO> result = devicesService.pageDevices(page, pageSize, deviceName, status, onlineStatus);
        return Result.success(result);
    }

    /**
     * 设备详情
     */
    @GetMapping("/{id}")
    public Result<DeviceDetailVO> detail(@PathVariable String id) {
        log.info("查询设备详情: id={}", id);
        DeviceDetailVO detail = devicesService.getDeviceDetail(Long.valueOf(id));
        return Result.success(detail);
    }

    /**
     * 添加设备
     */
    @PostMapping
    public Result<String> add(@RequestBody Map<String, Object> body) {
        String deviceName = (String) body.get("deviceName");
        String deviceSn = (String) body.get("deviceSn");
        BigDecimal latitude = toDecimal(body.get("latitude"));
        BigDecimal longitude = toDecimal(body.get("longitude"));
        log.info("添加设备: deviceName={}, deviceSn={}, lat={}, lng={}",
                deviceName, deviceSn, latitude, longitude);
        devicesService.addDevice(deviceName, deviceSn, latitude, longitude);
        return Result.success("添加成功");
    }

    /**
     * 编辑设备
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String deviceName = (String) body.get("deviceName");
        log.info("编辑设备: id={}, deviceName={}", id, deviceName);
        devicesService.updateDevice(Long.valueOf(id), deviceName);
        return Result.success("修改成功");
    }

    /**
     * 标定设备地图坐标；latitude/longitude 都为空则清除
     */
    @PutMapping("/{id}/location")
    public Result<String> updateLocation(@PathVariable String id, @RequestBody Map<String, Object> body) {
        BigDecimal latitude = toDecimal(body.get("latitude"));
        BigDecimal longitude = toDecimal(body.get("longitude"));
        log.info("标定位置: id={}, lat={}, lng={}", id, latitude, longitude);
        devicesService.updateDeviceLocation(Long.valueOf(id), latitude, longitude);
        return Result.success(latitude == null ? "已清除位置" : "位置已更新");
    }

    /**
     * 删除设备
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable String id) {
        log.info("删除设备: id={}", id);
        devicesService.deleteDevice(Long.valueOf(id));
        return Result.success("删除成功");
    }

    /**
     * 设备概览统计
     */
    @GetMapping("/statistics")
    public Result<DeviceStatisticsVO> statistics() {
        log.info("查询设备概览统计");
        DeviceStatisticsVO statistics = devicesService.getStatistics();
        return Result.success(statistics);
    }

    /**
     * 硬件状态回传（硬件执行开关指令后回传最终状态）
     */
    @PostMapping("/status-callback")
    public Result<String> statusCallback(@RequestBody Map<String, Object> body) {
        Long deviceId = body.get("deviceId") != null
                ? Long.valueOf(body.get("deviceId").toString()) : null;
        String status = (String) body.get("status");
        log.info("硬件状态回传: deviceId={}, status={}", deviceId, status);
        devicesService.updateDeviceStatus(deviceId, status);
        return Result.success("状态更新成功");
    }

    /**
     * 设备心跳上报（硬件定期发送心跳信号，响应中带回指令）
     */
    @PostMapping("/heartbeat")
    public Result<Map<String, String>> heartbeat(@RequestBody Map<String, Object> body) {
        Long deviceId = body.get("deviceId") != null
                ? Long.valueOf(body.get("deviceId").toString()) : null;
        log.info("设备心跳上报: deviceId={}", deviceId);
        devicesService.updateHeartbeat(deviceId);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("command", "NONE");
        return Result.success(response);
    }

    /**
     * 手动开关灯控制（进入 MANUAL 模式，忽略光照自动开关）
     */
    @PostMapping("/{id}/switch")
    public Result<Map<String, String>> switchDevice(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        log.info("手动开关灯: deviceId={}, status={}", id, status);
        String command = devicesService.switchDevice(Long.valueOf(id), status);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("command", command);
        response.put("controlMode", "MANUAL");
        return Result.success(response);
    }

    /**
     * 设置控制模式：AUTO 恢复阈值联动；MANUAL 手动锁定
     */
    @PutMapping("/{id}/control-mode")
    public Result<String> setControlMode(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String mode = (String) body.get("mode");
        log.info("设置控制模式: deviceId={}, mode={}", id, mode);
        devicesService.setControlMode(Long.valueOf(id), mode);
        return Result.success("模式已更新为 " + mode);
    }

    /**
     * 设置设备编组；groupName 为空则移出分组
     */
    @PutMapping("/{id}/group")
    public Result<String> setDeviceGroup(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String groupName = body.get("groupName") != null ? body.get("groupName").toString() : null;
        log.info("设置编组: deviceId={}, groupName={}", id, groupName);
        devicesService.setDeviceGroup(Long.valueOf(id), groupName);
        return Result.success(groupName == null || groupName.isBlank() ? "已移出编组" : "已加入编组 " + groupName.trim());
    }

    /**
     * 编组统一开关灯
     */
    @PostMapping("/group-switch")
    public Result<Map<String, Object>> switchGroup(@RequestBody Map<String, Object> body) {
        String groupName = body.get("groupName") != null ? body.get("groupName").toString() : null;
        String status = (String) body.get("status");
        log.info("编组统一开关: groupName={}, status={}", groupName, status);
        int count = devicesService.switchGroup(groupName, status);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", count);
        response.put("command", "ON".equals(status) ? "MANUAL_ON" : "MANUAL_OFF");
        response.put("controlMode", "MANUAL");
        return Result.success(response);
    }

    /**
     * 编组统一控制模式
     */
    @PutMapping("/group-control-mode")
    public Result<Map<String, Object>> setGroupControlMode(@RequestBody Map<String, Object> body) {
        String groupName = body.get("groupName") != null ? body.get("groupName").toString() : null;
        String mode = (String) body.get("mode");
        log.info("编组统一模式: groupName={}, mode={}", groupName, mode);
        int count = devicesService.setGroupControlMode(groupName, mode);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", count);
        response.put("mode", mode);
        return Result.success(response);
    }

    private static BigDecimal toDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new RuntimeException("经纬度格式不正确");
        }
    }
}
