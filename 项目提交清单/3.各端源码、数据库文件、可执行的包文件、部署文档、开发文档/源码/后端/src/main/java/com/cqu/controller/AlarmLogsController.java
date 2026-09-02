package com.cqu.controller;

import com.cqu.service.IAlarmLogsService;
import com.cqu.vo.Result;
import com.cqu.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alarm-logs")
public class AlarmLogsController {

    @Autowired
    private IAlarmLogsService alarmLogsService;

    /**
     * 告警分页列表
     */
    @GetMapping
    public Result<PageResult<AlarmLogVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) String status) {
        log.info("查询告警列表: page={}, pageSize={}, deviceId={}, alarmType={}, status={}",
                page, pageSize, deviceId, alarmType, status);
        PageResult<AlarmLogVO> result = alarmLogsService.pageAlarms(page, pageSize,
                deviceId != null ? Long.valueOf(deviceId) : null, alarmType, status);
        return Result.success(result);
    }

    /**
     * 告警详情
     */
    @GetMapping("/{id}")
    public Result<AlarmLogVO> detail(@PathVariable String id) {
        log.info("查询告警详情: id={}", id);
        AlarmLogVO detail = alarmLogsService.getAlarmDetail(Long.valueOf(id));
        return Result.success(detail);
    }

    /**
     * 解决告警
     */
    @PutMapping("/{id}/resolve")
    public Result<String> resolve(@PathVariable String id) {
        log.info("解决告警: id={}", id);
        alarmLogsService.resolveAlarm(Long.valueOf(id));
        return Result.success("处理成功");
    }

    /**
     * 告警统计
     */
    @GetMapping("/statistics")
    public Result<AlarmStatisticsVO> statistics() {
        log.info("查询告警统计");
        AlarmStatisticsVO statistics = alarmLogsService.getStatistics();
        return Result.success(statistics);
    }

    /**
     * 创建告警（硬件/系统触发，如设备离线、光照异常）
     */
    @PostMapping
    public Result<String> create(@RequestBody Map<String, Object> body) {
        Long deviceId = body.get("deviceId") != null
                ? Long.valueOf(body.get("deviceId").toString()) : null;
        String alarmType = (String) body.get("alarmType");
        String message = (String) body.get("message");
        log.info("创建告警: deviceId={}, alarmType={}, message={}", deviceId, alarmType, message);
        alarmLogsService.createAlarm(deviceId, alarmType, message);
        return Result.success("创建成功");
    }
}
