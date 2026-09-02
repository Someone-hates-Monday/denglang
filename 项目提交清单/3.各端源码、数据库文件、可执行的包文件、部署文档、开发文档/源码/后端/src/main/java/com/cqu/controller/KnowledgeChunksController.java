package com.cqu.controller;

import com.cqu.service.IRagService;
import com.cqu.vo.ChatRequest;
import com.cqu.vo.KnowledgeImportRequest;
import com.cqu.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * RAG 知识库 — 路灯维护知识文档向量 前端控制器
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
@Slf4j
@RestController
@RequestMapping("/knowledge-chunks")
@RequiredArgsConstructor
public class KnowledgeChunksController {

    private final IRagService ragService;

    /**
     * RAG 问答接口：检索知识库 + 大模型生成回答
     */
    @PostMapping("/rag")
    public Result<String> rag(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Result.fail("消息不能为空");
        }
        try {
            String answer = ragService.ask(request.getMessage());
            return Result.success(answer);
        } catch (Exception e) {
            log.error("RAG 问答失败：{}", e.getMessage(), e);
            return Result.fail("问答服务异常：" + e.getMessage());
        }
    }

    /**
     * AI 聊天接口（同 RAG 问答）
     */
    @PostMapping("/chat")
    public Result<String> chat(@RequestBody ChatRequest request) {
        return rag(request);
    }

    /**
     * 批量导入知识文档（自动生成 embedding 向量并存储到知识库）
     * <p>
     * 使用示例：
     * <pre>
     * {
     *   "documents": [
     *     { "title": "路灯维护手册", "content": "路灯定期检查应包括..." },
     *     { "title": "故障排查指南", "content": "当路灯不亮时，首先检查..." }
     *   ]
     * }
     * </pre>
     */
    @PostMapping("/import")
    public Result<Integer> importDocuments(@RequestBody KnowledgeImportRequest request) {
        if (request.getDocuments() == null || request.getDocuments().isEmpty()) {
            return Result.fail("文档列表不能为空");
        }
        try {
            int count = ragService.importDocuments(request);
            return Result.success(count);
        } catch (Exception e) {
            log.error("导入知识文档失败：{}", e.getMessage(), e);
            return Result.fail("导入失败：" + e.getMessage());
        }
    }
}