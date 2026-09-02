package com.cqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.constant.StreetLightConstants;
import com.cqu.entity.ControlLogs;
import com.cqu.entity.Devices;
import com.cqu.entity.Users;
import com.cqu.mapper.ControlLogsMapper;
import com.cqu.mapper.DevicesMapper;
import com.cqu.mapper.UsersMapper;
import com.cqu.service.IControlLogsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqu.utils.UserHolder;
import com.cqu.vo.ControlLogVO;
import com.cqu.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 路灯控制指令日志 服务实现类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
@Service
public class ControlLogsServiceImpl extends ServiceImpl<ControlLogsMapper, ControlLogs> implements IControlLogsService {

    @Autowired
    private DevicesMapper devicesMapper;

    @Autowired
    private UsersMapper usersMapper;

    @Override
    public PageResult<ControlLogVO> pageLogs(int page, int pageSize, Long deviceId, String command,
                                             Long operatorId, String source) {
        LambdaQueryWrapper<ControlLogs> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(deviceId != null, ControlLogs::getDeviceId, deviceId);
        wrapper.eq(command != null && !command.isBlank(), ControlLogs::getCommand, command);
        wrapper.eq(operatorId != null, ControlLogs::getOperatorId, operatorId);
        wrapper.eq(source != null && !source.isBlank(), ControlLogs::getSource, source);
        wrapper.orderByDesc(ControlLogs::getCreatedAt);

        Page<ControlLogs> pageResult = this.page(new Page<>(page, pageSize), wrapper);

        Map<Long, String> deviceNameMap = buildDeviceNameMap(pageResult.getRecords());
        Map<Long, String> operatorNameMap = buildOperatorNameMap(pageResult.getRecords());

        List<ControlLogVO> records = pageResult.getRecords().stream()
                .map(log -> toControlLogVO(log, deviceNameMap.get(log.getDeviceId()), operatorNameMap.get(log.getOperatorId())))
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    public ControlLogVO getDetail(Long id) {
        ControlLogs log = this.getById(id);
        if (log == null) {
            throw new RuntimeException("控制日志不存在");
        }

        String deviceName = null;
        if (log.getDeviceId() != null) {
            Devices device = devicesMapper.selectById(log.getDeviceId());
            if (device != null) {
                deviceName = device.getDeviceName();
            }
        }

        String operatorName = null;
        if (log.getOperatorId() != null) {
            Users operator = usersMapper.selectById(log.getOperatorId());
            if (operator != null) {
                operatorName = operator.getUsername();
            }
        }

        return toControlLogVO(log, deviceName, operatorName);
    }

    @Override
    public void recordLog(Long deviceId, String command, String result) {
        recordLog(deviceId, command, result, "MANUAL");
    }

    @Override
    public void recordLog(Long deviceId, String command, String result, String source) {
        ControlLogs log = new ControlLogs();
        log.setDeviceId(deviceId);
        log.setOperatorId(UserHolder.getCurrent());
        log.setCommand(command);
        log.setSource(source);
        log.setResult(result);
        log.setExecutionStatus(StreetLightConstants.EXEC_SUCCESS);
        this.save(log);
    }

    @Override
    public Long recordPendingCommand(Long deviceId, String command, String source, String expectedStatus) {
        supersedePendingCommands(deviceId);
        ControlLogs log = new ControlLogs();
        log.setDeviceId(deviceId);
        log.setOperatorId(UserHolder.getCurrent());
        log.setCommand(command);
        log.setSource(source);
        log.setExpectedStatus(expectedStatus);
        log.setResult(StreetLightConstants.EXEC_PENDING);
        log.setExecutionStatus(StreetLightConstants.EXEC_PENDING);
        this.save(log);
        return log.getId();
    }

    @Override
    public void supersedePendingCommands(Long deviceId) {
        if (deviceId == null) {
            return;
        }
        List<ControlLogs> stale = this.lambdaQuery()
                .eq(ControlLogs::getDeviceId, deviceId)
                .eq(ControlLogs::getExecutionStatus, StreetLightConstants.EXEC_PENDING)
                .list();
        for (ControlLogs log : stale) {
            log.setExecutionStatus(StreetLightConstants.EXEC_SUCCESS);
            log.setResult("SUPERSEDED");
            this.updateById(log);
        }
    }

    @Override
    public void confirmPendingByStatus(Long deviceId, String status) {
        if (deviceId == null || status == null || status.isBlank()) {
            return;
        }

        ControlLogs pending = this.lambdaQuery()
                .eq(ControlLogs::getDeviceId, deviceId)
                .eq(ControlLogs::getExecutionStatus, StreetLightConstants.EXEC_PENDING)
                .eq(ControlLogs::getExpectedStatus, status)
                .orderByDesc(ControlLogs::getCreatedAt)
                .last("LIMIT 1")
                .one();

        if (pending == null) {
            return;
        }

        pending.setExecutionStatus(StreetLightConstants.EXEC_SUCCESS);
        pending.setResult(StreetLightConstants.EXEC_SUCCESS);
        this.updateById(pending);
    }

    private Map<Long, String> buildDeviceNameMap(List<ControlLogs> logs) {
        List<Long> deviceIds = logs.stream()
                .map(ControlLogs::getDeviceId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        if (deviceIds.isEmpty()) {
            return Map.of();
        }

        return devicesMapper.selectBatchIds(deviceIds).stream()
                .collect(Collectors.toMap(Devices::getId, Devices::getDeviceName));
    }

    private Map<Long, String> buildOperatorNameMap(List<ControlLogs> logs) {
        List<Long> operatorIds = logs.stream()
                .map(ControlLogs::getOperatorId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        if (operatorIds.isEmpty()) {
            return Map.of();
        }

        return usersMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(Users::getId, Users::getUsername));
    }

    private ControlLogVO toControlLogVO(ControlLogs log, String deviceName, String operatorName) {
        return ControlLogVO.builder()
                .id(String.valueOf(log.getId()))
                .deviceId(String.valueOf(log.getDeviceId()))
                .deviceName(deviceName)
                .operatorId(log.getOperatorId() != null ? String.valueOf(log.getOperatorId()) : null)
                .operatorName(operatorName)
                .command(log.getCommand())
                .source(log.getSource())
                .result(log.getResult())
                .executionStatus(log.getExecutionStatus())
                .expectedStatus(log.getExpectedStatus())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
