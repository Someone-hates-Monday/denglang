package com.cqu.service;

import com.cqu.entity.LightReadings;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cqu.vo.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 光照强度采集记录（时序数据） 服务类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
public interface ILightReadingsService extends IService<LightReadings> {

    /**
     * 光照记录分页列表（支持按设备/时间范围筛选，按采集时间倒序）
     */
    PageResult<LightReadingsVO> pageReadings(
            int page, int pageSize, Long deviceId, String groupName,
            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取指定设备的最新一条光照数据
     */
    LatestLightVO getLatestLight(Long deviceId);

    /**
     * 历史光照趋势。deviceId 优先；否则按 groupName 平均；都空则全体平均。
     */
    List<TrendPointVO> getTrend(Long deviceId, String groupName, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 光照数据上报（含隐式心跳刷新 + 阈值自动判定）
     * @return 下发给硬件的指令：AUTO_ON / AUTO_OFF / NONE
     */
    String reportReading(Long deviceId, BigDecimal lightIntensity);
}
