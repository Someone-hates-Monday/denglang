package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket 统一推送消息信封
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {

    /** 消息类型：LIGHT_REPORTED / DEVICE_STATUS_CHANGED / DEVICE_ONLINE_STATUS_CHANGED / ALARM_CREATED */
    private String type;

    /** 推送时间 */
    private LocalDateTime timestamp;

    /** 消息体 */
    private Object data;
}
