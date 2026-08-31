-- ============================================================
-- 智慧路灯 — 数据库建表脚本
-- 数据库: PostgreSQL
-- ============================================================
CREATE DATABASE "smart-street-light";

-- pgvector 扩展（RAG 向量检索）
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users
(
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL UNIQUE,
    password   VARCHAR(256) NOT NULL,
    role       VARCHAR(32)  NOT NULL DEFAULT 'GROWER', -- SITE_MANAGER|AGRONOMIST|GROWER|DEVICE_OPS|TRAINEE|SYS_ADMIN
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.role IS '角色: SITE_MANAGER|AGRONOMIST|GROWER|DEVICE_OPS|TRAINEE|SYS_ADMIN（旧 ADMIN/MUNICIPAL_STAFF 见迁移）';

-- 2. 路灯设备表
CREATE TABLE IF NOT EXISTS devices
(
    id                  BIGSERIAL PRIMARY KEY,
    device_name         VARCHAR(128) NOT NULL,
    device_sn           VARCHAR(64)  NOT NULL UNIQUE,            -- 设备序列号（硬件MQTT客户端标识）
    status              VARCHAR(16)  NOT NULL DEFAULT 'OFF',     -- ON | OFF（开关状态）
    online_status       VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE', -- ONLINE | OFFLINE
    control_mode        VARCHAR(16)  NOT NULL DEFAULT 'AUTO',    -- AUTO | MANUAL
    group_name          VARCHAR(64),                             -- 编组名称，同名为一组
    latitude            NUMERIC(10, 7),                          -- 纬度 GCJ-02
    longitude           NUMERIC(10, 7),                          -- 经度 GCJ-02
    last_heartbeat_time TIMESTAMP,                               -- 最近心跳时间
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE devices IS '路灯设备表';
COMMENT ON COLUMN devices.status IS '开关状态: ON-已开灯, OFF-已关灯';
COMMENT ON COLUMN devices.online_status IS '在线状态: ONLINE-在线, OFFLINE-离线';
COMMENT ON COLUMN devices.control_mode IS '控制模式: AUTO-跟随阈值, MANUAL-手动锁定';
COMMENT ON COLUMN devices.group_name IS '编组名称，同名为一组；NULL/空=未分组';
COMMENT ON COLUMN devices.latitude IS '纬度（GCJ-02，与高德底图一致）；NULL=未标定';
COMMENT ON COLUMN devices.longitude IS '经度（GCJ-02，与高德底图一致）；NULL=未标定';
COMMENT ON COLUMN devices.device_sn IS '硬件唯一序列号，MQTT主题标识';
CREATE INDEX IF NOT EXISTS idx_devices_group_name ON devices (group_name);

-- 3. 光照记录表（时序数据）
CREATE TABLE IF NOT EXISTS light_readings
(
    id              BIGSERIAL PRIMARY KEY,
    device_id       BIGINT        NOT NULL,
    light_intensity NUMERIC(8, 2) NOT NULL, -- 光照强度值
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);
COMMENT ON TABLE light_readings IS '光照强度采集记录（时序数据）';
CREATE INDEX IF NOT EXISTS idx_light_readings_device_time
    ON light_readings (device_id, created_at DESC);

-- 4. 控制日志表
CREATE TABLE IF NOT EXISTS control_logs
(
    id                BIGSERIAL PRIMARY KEY,
    device_id         BIGINT,                                  -- 系统级操作（改阈值等）可为 NULL
    operator_id       BIGINT,                                  -- 手动操作人（AUTO时为NULL）
    command           VARCHAR(64) NOT NULL DEFAULT 'OFF',      -- ON | OFF | UPDATE_THRESHOLD ...
    source            VARCHAR(16) NOT NULL DEFAULT 'MANUAL',  -- AUTO | MANUAL
    result            VARCHAR(16) NOT NULL DEFAULT 'SUCCESS', -- SUCCESS | FAIL | PENDING
    execution_status  VARCHAR(16) NOT NULL DEFAULT 'SUCCESS', -- PENDING | SUCCESS | TIMEOUT
    expected_status   VARCHAR(16),                             -- ON | OFF（开关指令期望回传）
    created_at        TIMESTAMP   NOT NULL DEFAULT now()
);
COMMENT ON TABLE control_logs IS '路灯控制指令日志';
COMMENT ON COLUMN control_logs.device_id IS '设备ID；系统级操作（如改阈值）可为 NULL';
COMMENT ON COLUMN control_logs.source IS '指令来源: AUTO-光照联动自动, MANUAL-手动远程';
COMMENT ON COLUMN control_logs.execution_status IS '指令执行状态: PENDING | SUCCESS | TIMEOUT';
COMMENT ON COLUMN control_logs.expected_status IS '期望板端回传 status: ON | OFF';
CREATE INDEX IF NOT EXISTS idx_control_logs_time ON control_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_control_logs_pending
    ON control_logs (device_id, execution_status, created_at DESC)
    WHERE execution_status = 'PENDING';

-- 5. 阈值配置表（单行配置）
CREATE TABLE IF NOT EXISTS threshold_config
(
    id                  BIGSERIAL PRIMARY KEY,
    light_threshold_on  NUMERIC(8, 2) NOT NULL DEFAULT 30, -- 光照低于此值自动开灯
    light_threshold_off NUMERIC(8, 2) NOT NULL DEFAULT 80, -- 光照高于此值自动关灯
    heartbeat_timeout   INT           NOT NULL DEFAULT 60, -- 心跳超时秒数，超时判定离线
    updated_at          TIMESTAMP     NOT NULL DEFAULT now()
);
COMMENT ON TABLE threshold_config IS '系统阈值配置表';
-- 预置默认配置（只有一行）
INSERT INTO threshold_config (light_threshold_on, light_threshold_off, heartbeat_timeout)
VALUES (30, 80, 60);

-- 5b. 阈值覆盖（设备 / 编组；优先于全局 threshold_config）
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

-- 6. 告警记录表
CREATE TABLE IF NOT EXISTS alarm_logs
(
    id          BIGSERIAL PRIMARY KEY,
    device_id   BIGINT      NOT NULL,
    alarm_type  VARCHAR(32) NOT NULL,                  -- 告警类型（如 OFFLINE）
    message     VARCHAR(512),                          -- 告警详情
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | RESOLVED
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP
);
COMMENT ON TABLE alarm_logs IS '告警记录表';
COMMENT ON COLUMN alarm_logs.status IS '告警状态: ACTIVE-活跃, RESOLVED-已解决';
CREATE INDEX IF NOT EXISTS idx_alarm_logs_device ON alarm_logs (device_id, status);

-- 7. RAG 知识库表（pgvector 向量存储）
CREATE TABLE IF NOT EXISTS knowledge_chunks
(
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(256),          -- 文档标题/来源
    content    TEXT         NOT NULL, -- 文本块内容
    embedding  VECTOR(1024) NOT NULL, -- 文本向量（BGE-large-zh 默认 1024 维）
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE knowledge_chunks IS 'RAG 知识库 — 路灯维护知识文档向量';
-- 向量索引（先灌数据再建索引，否则 IVFFlat 无效）
-- CREATE INDEX IF NOT EXISTS idx_knowledge_embedding ON knowledge_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
