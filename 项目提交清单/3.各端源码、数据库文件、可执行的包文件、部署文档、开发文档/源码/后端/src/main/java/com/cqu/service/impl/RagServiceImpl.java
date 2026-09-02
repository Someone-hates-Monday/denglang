package com.cqu.service.impl;

import com.cqu.config.LlmConfig;
import com.cqu.entity.KnowledgeChunks;
import com.cqu.mapper.KnowledgeChunksMapper;
import com.cqu.service.IRagService;
import com.cqu.vo.KnowledgeImportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements IRagService {

    private final LlmConfig llmConfig;
    private final RestTemplate restTemplate;
    private final KnowledgeChunksMapper knowledgeChunksMapper;

    private static final String SYSTEM_PROMPT = """
            你是智慧路灯系统的维护助手。请根据用户问题提供专业的路灯维护建议。
            回答要简洁、准确、专业。
            """;

    @Override
    public String ask(String question) {
        // 直接调用大模型生成回答（跳过知识库检索）
        return generate(question, Collections.emptyList());
    }

    @Override
    public int importDocuments(KnowledgeImportRequest request) {
        List<KnowledgeImportRequest.Doc> docs = request.getDocuments();
        if (docs == null || docs.isEmpty()) {
            return 0;
        }
        
        log.warn("当前模式已跳过知识库导入，请配置 Embedding API 后使用完整 RAG 功能");
        return 0;
    }

    /**
     * 构造增强 Prompt 并调用大模型生成回答
     */
    @SuppressWarnings("unchecked")
    private String generate(String question, List<KnowledgeChunks> chunks) {
        // 拼接检索到的知识库上下文
        String context;
        if (chunks.isEmpty()) {
            context = "";
        } else {
            context = "相关知识库内容：\n" + chunks.stream()
                    .map(c -> "- " + c.getContent())
                    .collect(Collectors.joining("\n")) + "\n\n";
        }

        String userPrompt = context + "用户问题：" + question;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmConfig.getApiKey());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> body = Map.of(
                "model", llmConfig.getModel(),
                "messages", messages
        );

        String url = llmConfig.getBaseUrl() + "/chat/completions";
        log.info("调用 Chat API: {}", url);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("调用 Chat API 失败: {}", e.getMessage());
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage());
        }
    }
}
