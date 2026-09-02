package com.cqu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqu.entity.ThresholdConfig;
import com.cqu.vo.EffectiveThresholdVO;
import com.cqu.vo.ThresholdConfigVO;
import com.cqu.vo.ThresholdOverrideVO;

import java.math.BigDecimal;
import java.util.List;

public interface IThresholdConfigService extends IService<ThresholdConfig> {

    ThresholdConfigVO getConfig();

    void updateConfig(BigDecimal lightThresholdOn, BigDecimal lightThresholdOff, Integer heartbeatTimeout);

    /** 解析生效阈值：DEVICE > GROUP > GLOBAL */
    EffectiveThresholdVO resolveEffective(Long deviceId);

    List<ThresholdOverrideVO> listOverrides();

    void upsertOverride(String scopeType, String scopeKey, BigDecimal on, BigDecimal off);

    void deleteOverride(String scopeType, String scopeKey);
}
