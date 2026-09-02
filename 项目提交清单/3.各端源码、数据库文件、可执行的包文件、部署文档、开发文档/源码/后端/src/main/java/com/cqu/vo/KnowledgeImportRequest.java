package com.cqu.vo;

import lombok.Data;

import java.util.List;

/**
 * 知识文档批量导入请求体
 */
@Data
public class KnowledgeImportRequest {

    /** 文档列表 */
    private List<Doc> documents;

    @Data
    public static class Doc {
        /** 文档标题 */
        private String title;
        /** 文档内容 */
        private String content;
    }
}