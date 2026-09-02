package com.cqu.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import com.cqu.config.PgVectorTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * RAG 知识库 — 路灯维护知识文档向量
 * </p>
 *
 * @author 
 * @since 2026-06-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("knowledge_chunks")
public class KnowledgeChunks implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.NONE)
    private Long id;

    private String title;

    private String content;

    @TableField(typeHandler = PgVectorTypeHandler.class)
    private float[] embedding;

    private LocalDateTime createdAt;


}
