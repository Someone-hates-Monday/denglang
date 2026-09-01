-- 光棚顾问静态知识语料（可选落库；运行时默认用内存 GreenhouseKnowledgeCorpus）
-- 无 embedding 依赖，便于本机无 LLM Key 时做关键词检索扩展

CREATE TABLE IF NOT EXISTS gh_knowledge_chunks
(
    id         BIGSERIAL PRIMARY KEY,
    chunk_id   VARCHAR(64)  NOT NULL UNIQUE,
    title      VARCHAR(256) NOT NULL,
    source     VARCHAR(256),
    content    TEXT         NOT NULL,
    keywords   TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE gh_knowledge_chunks IS '智慧光棚顾问薄 RAG 语料（可选）；主路径为内存语料';

INSERT INTO gh_knowledge_chunks (chunk_id, title, source, content, keywords)
VALUES
('dendrobium-recipe', '铁皮石斛光配方', 'contracts/light-recipe.md',
 '铁皮石斛分阶段：组培目标 60–70、硬限 50–90 PPFD；栽培目标 90–120、硬限 70–140。欠光先开遮阳再补光；过光先降补光。',
 '石斛,组培,栽培,光配方,ppfd,硬限'),
('economics', '产量指数与电费口径', 'CROP-ECONOMICS-STANDARDS-REF.md',
 'yieldIndex 为 DLI 达成率演示指标，不是千克产量；电费为估算。欠光先开遮阳；过光先降灯。',
 '电费,economics,产量,能耗,性价比'),
('workorder', '工单状态机', 'RBAC-ROLES.md',
 'PENDING→APPROVED→IN_PROGRESS→COMPLETED。approve 不下发；claim 才下发。',
 '工单,审批,approve,claim,pending')
ON CONFLICT (chunk_id) DO UPDATE
SET title = EXCLUDED.title,
    source = EXCLUDED.source,
    content = EXCLUDED.content,
    keywords = EXCLUDED.keywords;
