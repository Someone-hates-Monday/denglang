package com.cqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqu.entity.Devices;
import com.cqu.entity.ThresholdConfig;
import com.cqu.entity.ThresholdOverride;
import com.cqu.mapper.DevicesMapper;
import com.cqu.mapper.ThresholdConfigMapper;
import com.cqu.mapper.ThresholdOverrideMapper;
import com.cqu.service.IControlLogsService;
import com.cqu.service.IThresholdConfigService;
import com.cqu.vo.EffectiveThresholdVO;
import com.cqu.vo.ThresholdConfigVO;
import com.cqu.vo.ThresholdOverrideVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ThresholdConfigServiceImpl extends ServiceImpl<ThresholdConfigMapper, ThresholdConfig>
        implements IThresholdConfigService {

    private static final Long CONFIG_ID = 1L;

    @Autowired
    private IControlLogsService controlLogsService;

    @Autowired
    private ThresholdOverrideMapper thresholdOverrideMapper;

    @Autowired
    private DevicesMapper devicesMapper;

    @Override
    public ThresholdConfigVO getConfig() {
        ThresholdConfig config = this.getById(CONFIG_ID);
        if (config == null) {
            throw new RuntimeException("阈值配置不存在，请先初始化数据库");
        }
        return ThresholdConfigVO.builder()
                .id(String.valueOf(config.getId()))
                .lightThresholdOn(config.getLightThresholdOn())
                .lightThresholdOff(config.getLightThresholdOff())
                .heartbeatTimeout(config.getHeartbeatTimeout())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    @Override
    public void updateConfig(BigDecimal lightThresholdOn, BigDecimal lightThresholdOff, Integer heartbeatTimeout) {
        validatePair(lightThresholdOn, lightThresholdOff);
        if (heartbeatTimeout == null || heartbeatTimeout <= 0) {
            throw new RuntimeException("心跳超时时间必须大于0");
        }
        ThresholdConfig config = this.getById(CONFIG_ID);
        if (config == null) {
            throw new RuntimeException("阈值配置不存在");
        }
        config.setLightThresholdOn(lightThresholdOn);
        config.setLightThresholdOff(lightThresholdOff);
        config.setHeartbeatTimeout(heartbeatTimeout);
        config.setUpdatedAt(LocalDateTime.now());
        this.updateById(config);
        controlLogsService.recordLog(null, "UPDATE_THRESHOLD", "SUCCESS");
    }

    @Override
    public EffectiveThresholdVO resolveEffective(Long deviceId) {
        ThresholdConfig global = this.getById(CONFIG_ID);
        if (global == null) {
            throw new RuntimeException("阈值配置不存在");
        }
        Devices device = deviceId != null ? devicesMapper.selectById(deviceId) : null;

        if (device != null) {
            ThresholdOverride deviceOv = findOverride("DEVICE", String.valueOf(device.getId()));
            if (deviceOv != null) {
                return EffectiveThresholdVO.builder()
                        .lightThresholdOn(deviceOv.getLightThresholdOn())
                        .lightThresholdOff(deviceOv.getLightThresholdOff())
                        .source("DEVICE")
                        .sourceKey(String.valueOf(device.getId()))
                        .build();
            }
            String group = device.getGroupName();
            if (group != null && !group.isBlank()) {
                ThresholdOverride groupOv = findOverride("GROUP", group.trim());
                if (groupOv != null) {
                    return EffectiveThresholdVO.builder()
                            .lightThresholdOn(groupOv.getLightThresholdOn())
                            .lightThresholdOff(groupOv.getLightThresholdOff())
                            .source("GROUP")
                            .sourceKey(group.trim())
                            .build();
                }
            }
        }

        return EffectiveThresholdVO.builder()
                .lightThresholdOn(global.getLightThresholdOn())
                .lightThresholdOff(global.getLightThresholdOff())
                .source("GLOBAL")
                .sourceKey(null)
                .build();
    }

    @Override
    public List<ThresholdOverrideVO> listOverrides() {
        List<ThresholdOverride> list = thresholdOverrideMapper.selectList(
                new LambdaQueryWrapper<ThresholdOverride>().orderByAsc(ThresholdOverride::getScopeType)
                        .orderByAsc(ThresholdOverride::getScopeKey));
        return list.stream().map(this::toOverrideVO).collect(Collectors.toList());
    }

    @Override
    public void upsertOverride(String scopeType, String scopeKey, BigDecimal on, BigDecimal off) {
        String type = normalizeType(scopeType);
        String key = normalizeKey(scopeType, scopeKey);
        validatePair(on, off);

        ThresholdOverride existing = findOverride(type, key);
        if (existing == null) {
            ThresholdOverride row = new ThresholdOverride();
            row.setScopeType(type);
            row.setScopeKey(key);
            row.setLightThresholdOn(on);
            row.setLightThresholdOff(off);
            row.setUpdatedAt(LocalDateTime.now());
            thresholdOverrideMapper.insert(row);
        } else {
            existing.setLightThresholdOn(on);
            existing.setLightThresholdOff(off);
            existing.setUpdatedAt(LocalDateTime.now());
            thresholdOverrideMapper.updateById(existing);
        }
        controlLogsService.recordLog(null, "UPSERT_THRESHOLD_" + type, "SUCCESS");
    }

    @Override
    public void deleteOverride(String scopeType, String scopeKey) {
        String type = normalizeType(scopeType);
        String key = normalizeKey(scopeType, scopeKey);
        thresholdOverrideMapper.delete(
                new LambdaQueryWrapper<ThresholdOverride>()
                        .eq(ThresholdOverride::getScopeType, type)
                        .eq(ThresholdOverride::getScopeKey, key));
        controlLogsService.recordLog(null, "DELETE_THRESHOLD_" + type, "SUCCESS");
    }

    private ThresholdOverride findOverride(String type, String key) {
        return thresholdOverrideMapper.selectOne(
                new LambdaQueryWrapper<ThresholdOverride>()
                        .eq(ThresholdOverride::getScopeType, type)
                        .eq(ThresholdOverride::getScopeKey, key)
                        .last("LIMIT 1"));
    }

    private ThresholdOverrideVO toOverrideVO(ThresholdOverride o) {
        String label = o.getScopeKey();
        if ("DEVICE".equals(o.getScopeType())) {
            try {
                Devices d = devicesMapper.selectById(Long.valueOf(o.getScopeKey()));
                if (d != null) {
                    label = d.getDeviceName() + " (" + d.getDeviceSn() + ")";
                }
            } catch (NumberFormatException ignored) {
                // keep key
            }
        }
        return ThresholdOverrideVO.builder()
                .id(String.valueOf(o.getId()))
                .scopeType(o.getScopeType())
                .scopeKey(o.getScopeKey())
                .scopeLabel(label)
                .lightThresholdOn(o.getLightThresholdOn())
                .lightThresholdOff(o.getLightThresholdOff())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    private static void validatePair(BigDecimal on, BigDecimal off) {
        if (on == null || off == null) {
            throw new RuntimeException("阈值参数不能为空");
        }
        if (on.compareTo(off) >= 0) {
            throw new RuntimeException("开灯阈值必须小于关灯阈值");
        }
    }

    private String normalizeType(String scopeType) {
        if (scopeType == null) {
            throw new RuntimeException("scopeType 不能为空");
        }
        String t = scopeType.trim().toUpperCase();
        if (!"DEVICE".equals(t) && !"GROUP".equals(t)) {
            throw new RuntimeException("scopeType 仅支持 DEVICE 或 GROUP");
        }
        return t;
    }

    private String normalizeKey(String scopeType, String scopeKey) {
        if (scopeKey == null || scopeKey.isBlank()) {
            throw new RuntimeException("scopeKey 不能为空");
        }
        String key = scopeKey.trim();
        if ("DEVICE".equalsIgnoreCase(scopeType)) {
            Devices d = devicesMapper.selectById(Long.valueOf(key));
            if (d == null) {
                throw new RuntimeException("设备不存在");
            }
            return String.valueOf(d.getId());
        }
        return key;
    }
}
