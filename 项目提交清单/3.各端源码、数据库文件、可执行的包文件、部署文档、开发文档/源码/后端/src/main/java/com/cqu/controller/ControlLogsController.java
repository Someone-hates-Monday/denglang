package com.cqu.controller;

import com.cqu.service.IControlLogsService;
import com.cqu.vo.Result;
import com.cqu.vo.ControlLogVO;
import com.cqu.vo.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/control-logs")
public class ControlLogsController {

    @Autowired
    private IControlLogsService controlLogsService;

    /**
     * 控制日志分页列表
     */
    @GetMapping
    public Result<PageResult<ControlLogVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String command,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String source) {
        log.info("查询控制日志: page={}, pageSize={}, deviceId={}, command={}, operatorId={}, source={}",
                page, pageSize, deviceId, command, operatorId, source);
        PageResult<ControlLogVO> result = controlLogsService.pageLogs(page, pageSize,
                deviceId != null ? Long.valueOf(deviceId) : null, command,
                operatorId != null ? Long.valueOf(operatorId) : null, source);
        return Result.success(result);
    }

    /**
     * 控制日志详情
     */
    @GetMapping("/{id}")
    public Result<ControlLogVO> detail(@PathVariable String id) {
        log.info("查询控制日志详情: id={}", id);
        ControlLogVO detail = controlLogsService.getDetail(Long.valueOf(id));
        return Result.success(detail);
    }
}
