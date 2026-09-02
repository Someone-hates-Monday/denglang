-- 光棚报告表（R3 日报告草稿 / 批阅）
CREATE TABLE IF NOT EXISTS gh_reports
(
    id            BIGSERIAL PRIMARY KEY,
    report_type   VARCHAR(32)  NOT NULL, -- DAILY_LIGHT | ENERGY_YIELD | DEVICE_HEALTH | TRAINING
    title         VARCHAR(128) NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT | SUBMITTED | REVIEWED | ARCHIVED
    author_id     BIGINT,
    author_role   VARCHAR(32),
    zone_id       VARCHAR(32),
    report_date   DATE         NOT NULL DEFAULT CURRENT_DATE,
    summary_zh    TEXT,
    body_json     TEXT,
    work_order_ids TEXT,
    reviewer_id   BIGINT,
    review_note   VARCHAR(512),
    reviewed_at   TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_gh_reports_date ON gh_reports (report_date DESC, report_type);
CREATE INDEX IF NOT EXISTS idx_gh_reports_status ON gh_reports (status, created_at DESC);
