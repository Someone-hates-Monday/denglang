package com.cqu.controller;

import com.cqu.service.IThresholdConfigService;
import com.cqu.vo.EffectiveThresholdVO;
import com.cqu.vo.Result;
import com.cqu.vo.ThresholdConfigVO;
import com.cqu.vo.ThresholdOverrideVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/threshold-config")
public class ThresholdConfigController {

    @Autowired
    private IThresholdConfigService thresholdConfigService;

    @GetMapping
    public Result<ThresholdConfigVO> get() {
        return Result.success(thresholdConfigService.getConfig());
    }

    @PutMapping
    public Result<String> update(@RequestBody Map<String, Object> body) {
        BigDecimal lightThresholdOn = body.get("lightThresholdOn") != null
                ? new BigDecimal(body.get("lightThresholdOn").toString()) : null;
        BigDecimal lightThresholdOff = body.get("lightThresholdOff") != null
                ? new BigDecimal(body.get("lightThresholdOff").toString()) : null;
        Integer heartbeatTimeout = body.get("heartbeatTimeout") != null
                ? Integer.valueOf(body.get("heartbeatTimeout").toString()) : null;
        log.info("更新全局阈值: on={}, off={}, heartbeat={}", lightThresholdOn, lightThresholdOff, heartbeatTimeout);
        thresholdConfigService.updateConfig(lightThresholdOn, lightThresholdOff, heartbeatTimeout);
        return Result.success("更新成功");
    }

    @GetMapping("/overrides")
    public Result<List<ThresholdOverrideVO>> listOverrides() {
        return Result.success(thresholdConfigService.listOverrides());
    }

    @PutMapping("/overrides")
    public Result<String> upsertOverride(@RequestBody Map<String, Object> body) {
        String scopeType = body.get("scopeType") != null ? body.get("scopeType").toString() : null;
        String scopeKey = body.get("scopeKey") != null ? body.get("scopeKey").toString() : null;
        BigDecimal on = body.get("lightThresholdOn") != null
                ? new BigDecimal(body.get("lightThresholdOn").toString()) : null;
        BigDecimal off = body.get("lightThresholdOff") != null
                ? new BigDecimal(body.get("lightThresholdOff").toString()) : null;
        log.info("保存阈值覆盖: type={}, key={}, on={}, off={}", scopeType, scopeKey, on, off);
        thresholdConfigService.upsertOverride(scopeType, scopeKey, on, off);
        return Result.success("覆盖已保存");
    }

    @DeleteMapping("/overrides")
    public Result<String> deleteOverride(
            @RequestParam String scopeType,
            @RequestParam String scopeKey) {
        log.info("删除阈值覆盖: type={}, key={}", scopeType, scopeKey);
        thresholdConfigService.deleteOverride(scopeType, scopeKey);
        return Result.success("覆盖已删除");
    }

    @GetMapping("/effective/{deviceId}")
    public Result<EffectiveThresholdVO> effective(@PathVariable String deviceId) {
        return Result.success(thresholdConfigService.resolveEffective(Long.valueOf(deviceId)));
    }
}
