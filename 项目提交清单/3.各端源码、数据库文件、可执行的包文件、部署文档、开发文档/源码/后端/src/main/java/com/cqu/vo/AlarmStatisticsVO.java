package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 告警统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmStatisticsVO {

    private String activeCount;

    private List<AlarmTypeCount> byType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlarmTypeCount {
        private String alarmType;
        private String count;
    }
}
