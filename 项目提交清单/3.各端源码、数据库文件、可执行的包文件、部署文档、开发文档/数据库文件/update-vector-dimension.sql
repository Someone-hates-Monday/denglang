-- 修改 knowledge_chunks 表的向量维度为 1536（匹配 text-embedding-3-small）
-- 如果表中有数据，需要先清空或删除重建

-- 方案1：清空表数据后修改列类型
-- DELETE FROM knowledge_chunks;
-- ALTER TABLE knowledge_chunks ALTER COLUMN embedding TYPE VECTOR(1536);

-- 方案2：删除表重建（推荐，如果表是空的）
DROP TABLE IF EXISTS knowledge_chunks;

CREATE TABLE knowledge_chunks
(
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(256),
    content    TEXT         NOT NULL,
    embedding  VECTOR(1536) NOT NULL,  -- 修改为 1536 维
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE knowledge_chunks IS 'RAG 知识库 — 路灯维护知识文档向量';