package com.cqu.controller;

import com.cqu.service.ILightReadingsService;
import com.cqu.vo.Result;
import com.cqu.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/light-readings")
public class LightReadingsController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ILightReadingsService lightReadingsService;

    /**
     * 光照记录分页列表
     */
    @GetMapping
    public Result<PageResult<LightReadingsVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        log.info("查询光照记录: page={}, pageSize={}, deviceId={}, groupName={}",
                page, pageSize, deviceId, groupName);

        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);

        Long deviceIdLong = deviceId != null && !deviceId.isBlank() ? Long.valueOf(deviceId) : null;
        PageResult<LightReadingsVO> result =
                lightReadingsService.pageReadings(page, pageSize, deviceIdLong, groupName, start, end);
        return Result.success(result);
    }

    /**
     * 设备最新光照
     */
    @GetMapping("/latest/{deviceId}")
    public Result<LatestLightVO> latest(@PathVariable String deviceId) {
        log.info("查询设备最新光照: deviceId={}", deviceId);
        LatestLightVO latest = lightReadingsService.getLatestLight(Long.valueOf(deviceId));
        return Result.success(latest);
    }

    /**
     * 历史光照趋势：deviceId / groupName / 全体（都不传）
     */
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String groupName,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        log.info("查询光照趋势: deviceId={}, groupName={}, startTime={}, endTime={}",
                deviceId, groupName, startTime, endTime);

        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        Long deviceIdLong = deviceId != null && !deviceId.isBlank() ? Long.valueOf(deviceId) : null;

        List<TrendPointVO> trend = lightReadingsService.getTrend(deviceIdLong, groupName, start, end);
        return Result.success(trend);
    }

    /**
     * 光照数据上报（硬件调用，响应中带回开关指令）
     */
    @PostMapping
    public Result<Map<String, String>> report(@RequestBody Map<String, Object> body) {
        Long deviceId = body.get("deviceId") != null
                ? Long.valueOf(body.get("deviceId").toString()) : null;
        BigDecimal lightIntensity = body.get("lightIntensity") != null
                ? new BigDecimal(body.get("lightIntensity").toString()) : null;
        log.info("光照数据上报: deviceId={}, lightIntensity={}", deviceId, lightIntensity);
        String command = lightReadingsService.reportReading(deviceId, lightIntensity);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("command", command);
        return Result.success(response);
    }

    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        // 兼容 ISO 8601 格式（如 2026-07-09T08:03:28.810）和空格分隔格式
        String normalized = timeStr.trim().replace("T", " ");
        // 截掉毫秒部分（.xxx）
        int dotIdx = normalized.indexOf(".");
        if (dotIdx > 0) {
            normalized = normalized.substring(0, dotIdx);
        }
        return LocalDateTime.parse(normalized, FORMATTER);
    }
}
