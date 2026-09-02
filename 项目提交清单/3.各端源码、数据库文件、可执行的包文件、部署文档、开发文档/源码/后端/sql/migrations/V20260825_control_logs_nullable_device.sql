-- 系统级操作（改阈值等）无关联设备；command 需容纳 UPDATE_THRESHOLD 等长命令名
ALTER TABLE control_logs
    ALTER COLUMN device_id DROP NOT NULL;

ALTER TABLE control_logs
    ALTER COLUMN command TYPE VARCHAR(64);

COMMENT ON COLUMN control_logs.device_id IS '设备ID；系统级操作（如改阈值）可为 NULL';
