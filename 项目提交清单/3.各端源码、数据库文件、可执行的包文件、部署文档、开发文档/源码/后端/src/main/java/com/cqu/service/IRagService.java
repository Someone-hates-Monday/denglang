package com.cqu.service;

import com.cqu.vo.KnowledgeImportRequest;

/**
 * RAG 检索增强生成服务
 */
public interface IRagService {

    /**
     * RAG 问答：检索知识库 + 大模型生成回答
     *
     * @param question 用户问题
     * @return 大模型基于检索结果的回答
     */
    String ask(String question);

    /**
     * 批量导入知识文档（自动生成 embedding 向量并存储）
     *
     * @param request 包含文档列表的导入请求
     * @return 成功导入的文档数量
     */
    int importDocuments(KnowledgeImportRequest request);
}