package com.cqu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 大模型配置 — 读取 llm.* 属性并注册 RestTemplate
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    /** OpenAI 兼容 API Key（DeepSeek / OpenAI 等） */
    private String apiKey;

    /** OpenAI 兼容 API 根地址（建议含 /v1，如 https://api.deepseek.com/v1） */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 模型名称（DeepSeek：deepseek-chat） */
    private String model = "deepseek-chat";

    /** 生成温度 */
    private double temperature = 0.3;

    /** 单次回答最大 token（DeepSeek chat 常用上限内取值） */
    private int maxTokens = 2048;

    /** Embedding API Key（当前光棚顾问未使用，可留空） */
    private String embeddingApiKey;

    /** Embedding API 地址 */
    private String embeddingBaseUrl;

    /** Embedding 模型名称 */
    private String embeddingModel;

    /** 薄 RAG 检索返回条数 */
    private int topK = 4;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
