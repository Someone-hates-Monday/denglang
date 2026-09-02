-- 设备 / 编组阈值覆盖（无覆盖则回退全局 threshold_config）
CREATE TABLE IF NOT EXISTS threshold_overrides
(
    id                  BIGSERIAL PRIMARY KEY,
    scope_type          VARCHAR(16)   NOT NULL, -- DEVICE | GROUP
    scope_key           VARCHAR(128)  NOT NULL, -- deviceId 或 groupName
    light_threshold_on  NUMERIC(8, 2) NOT NULL,
    light_threshold_off NUMERIC(8, 2) NOT NULL,
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uq_threshold_override_scope UNIQUE (scope_type, scope_key),
    CONSTRAINT chk_threshold_override_order CHECK (light_threshold_on < light_threshold_off),
    CONSTRAINT chk_threshold_override_type CHECK (scope_type IN ('DEVICE', 'GROUP'))
);

COMMENT ON TABLE threshold_overrides IS '阈值覆盖：DEVICE=单设备，GROUP=编组；优先于全局 threshold_config';
CREATE INDEX IF NOT EXISTS idx_threshold_overrides_scope ON threshold_overrides (scope_type, scope_key);
