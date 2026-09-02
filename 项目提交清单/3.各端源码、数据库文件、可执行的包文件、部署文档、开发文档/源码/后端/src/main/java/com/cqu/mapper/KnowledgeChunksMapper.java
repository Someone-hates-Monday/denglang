package com.cqu.mapper;

import com.cqu.entity.KnowledgeChunks;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * RAG 知识库 — 路灯维护知识文档向量 Mapper 接口
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
@Mapper
public interface KnowledgeChunksMapper extends BaseMapper<KnowledgeChunks> {

    /**
     * 基于向量余弦相似度检索最相似的文档块
     *
     * @param embeddingStr 查询向量的文本表示（如 [0.1,0.2,...]）
     * @param topK         返回数量
     * @return 相似文档列表（按相似度降序）
     */
    List<KnowledgeChunks> searchByEmbedding(@Param("embeddingStr") String embeddingStr,
                                            @Param("topK") int topK);
}