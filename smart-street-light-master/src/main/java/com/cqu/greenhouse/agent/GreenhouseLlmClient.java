package com.cqu.greenhouse.agent;

import com.cqu.config.LlmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions（DeepSeek / OpenAI 等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreenhouseLlmClient {

    private final LlmConfig llmConfig;
    private final RestTemplate restTemplate;

    /** 启动时打印是否已配置 LLM（不输出密钥） */
    @jakarta.annotation.PostConstruct
    void logLlmStatus() {
        if (isConfigured()) {
            log.info("Greenhouse LLM 已配置 model={} baseUrl={}",
                    llmConfig.getModel(), llmConfig.getBaseUrl());
        } else {
            log.warn("Greenhouse LLM 未配置 api-key，顾问将走模板答");
        }
    }

    public boolean isConfigured() {
        String key = llmConfig.getApiKey();
        return key != null && !key.isBlank();
    }

    @SuppressWarnings("unchecked")
    public String chat(String system, List<Map<String, String>> history, String userContent) {
        if (!isConfigured()) {
            throw new IllegalStateException("LLM API key 未配置");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmConfig.getApiKey().trim());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userContent));

        String model = llmConfig.getModel() != null && !llmConfig.getModel().isBlank()
                ? llmConfig.getModel()
                : "deepseek-chat";
        int maxTokens = llmConfig.getMaxTokens() > 0 ? llmConfig.getMaxTokens() : 2048;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", llmConfig.getTemperature());
        body.put("max_tokens", maxTokens);

        String base = llmConfig.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = "https://api.deepseek.com/v1";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // 兼容只写 https://api.deepseek.com（无 /v1）
        if (!base.contains("/v1") && !base.contains("/chat/completions")) {
            base = base + "/v1";
        }
        String url = base.contains("/chat/completions") ? base : base + "/chat/completions";

        log.info("Greenhouse Agent 调用 LLM model={} max_tokens={} url={}", model, maxTokens, url);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url, new HttpEntity<>(body, headers), Map.class);
        Map<?, ?> respBody = response.getBody();
        if (respBody == null) {
            throw new RuntimeException("LLM 返回空");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM 无 choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object content = message.get("content");
        return content != null ? content.toString().trim() : "";
    }
}
