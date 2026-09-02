-- 光棚告警表（M6 / P2.3）
CREATE TABLE IF NOT EXISTS gh_alarms
(
    id           BIGSERIAL PRIMARY KEY,
    zone_id      VARCHAR(32),
    device_sn    VARCHAR(64),
    alarm_type   VARCHAR(32)  NOT NULL, -- UNDER_PPFD | OVER_PPFD | DEVICE_OFFLINE | COMMAND_TIMEOUT | WORK_ORDER
    message      VARCHAR(512) NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | RESOLVED
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gh_alarms_status_time ON gh_alarms (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_gh_alarms_zone ON gh_alarms (zone_id, created_at DESC);
