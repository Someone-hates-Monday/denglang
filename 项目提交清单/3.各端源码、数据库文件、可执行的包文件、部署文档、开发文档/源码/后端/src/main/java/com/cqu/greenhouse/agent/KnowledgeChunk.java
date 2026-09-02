package com.cqu.greenhouse.agent;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 静态知识语料条目（薄 RAG / 关键词检索）
 */
@Data
@Accessors(chain = true)
public class KnowledgeChunk {
    private String id;
    private String title;
    private String source;
    private String content;
    private List<String> keywords = new ArrayList<>();
}
