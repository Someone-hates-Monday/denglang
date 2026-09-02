package com.cqu.greenhouse.agent;

import lombok.Data;

@Data
public class AgentChatRequest {
    /** 可选；空则服务端新建会话 */
    private String sessionId;
    /** 用户问题 */
    private String message;
    /** 可选关注分区，默认 ZONE-A */
    private String zoneId;
}
