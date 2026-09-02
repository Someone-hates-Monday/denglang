package com.cqu.vo;

import lombok.Data;

/**
 * 大模型聊天 — 请求体
 */
@Data
public class ChatRequest {

    /** 用户输入的消息 */
    private String message;
}
