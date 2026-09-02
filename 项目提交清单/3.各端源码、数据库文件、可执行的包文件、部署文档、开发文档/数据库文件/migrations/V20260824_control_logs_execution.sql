-- M1: 指令闭环 — control_logs 增加执行状态追踪
-- 已有库执行：psql -U postgres -d smart-street-light -f sql/migrations/V20260824_control_logs_execution.sql

ALTER TABLE control_logs
    ADD COLUMN IF NOT EXISTS execution_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS';

ALTER TABLE control_logs
    ADD COLUMN IF NOT EXISTS expected_status VARCHAR(16);

COMMENT ON COLUMN control_logs.execution_status IS '指令执行状态: PENDING | SUCCESS | TIMEOUT';
COMMENT ON COLUMN control_logs.expected_status IS '期望板端回传 status: ON | OFF（仅开关指令）';

CREATE INDEX IF NOT EXISTS idx_control_logs_pending
    ON control_logs (device_id, execution_status, created_at DESC)
    WHERE execution_status = 'PENDING';
