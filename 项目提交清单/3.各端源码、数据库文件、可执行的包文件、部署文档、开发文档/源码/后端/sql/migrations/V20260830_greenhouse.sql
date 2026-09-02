-- 智能光棚域表（与路灯表并存，同库）
-- 应用：init-db.ps1 迁移目录按文件名排序执行

CREATE TABLE IF NOT EXISTS gh_recipes
(
    id                 BIGSERIAL PRIMARY KEY,
    recipe_id          VARCHAR(64)  NOT NULL UNIQUE,
    crop               VARCHAR(64)  NOT NULL,
    crop_name_zh       VARCHAR(64)  NOT NULL,
    stage              VARCHAR(32)  NOT NULL DEFAULT 'VEGETATIVE',
    version            INT          NOT NULL DEFAULT 1,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    photoperiod_hours  NUMERIC(4, 1) NOT NULL DEFAULT 12,
    ppfd_target_min    NUMERIC(8, 2) NOT NULL,
    ppfd_target_max    NUMERIC(8, 2) NOT NULL,
    ppfd_hard_min      NUMERIC(8, 2) NOT NULL,
    ppfd_hard_max      NUMERIC(8, 2) NOT NULL,
    dli_target_min     NUMERIC(8, 3),
    dli_target_max     NUMERIC(8, 3),
    prefer_natural     BOOLEAN      NOT NULL DEFAULT TRUE,
    auto_supplement    BOOLEAN      NOT NULL DEFAULT TRUE,
    auto_shade         BOOLEAN      NOT NULL DEFAULT TRUE,
    dimming_step_pct   INT          NOT NULL DEFAULT 5,
    shade_step_pct     INT          NOT NULL DEFAULT 10,
    cooldown_sec       INT          NOT NULL DEFAULT 60,
    approve_dim_above  INT          NOT NULL DEFAULT 80,
    approve_shade_above INT         NOT NULL DEFAULT 80,
    created_at         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gh_zones
(
    id                   BIGSERIAL PRIMARY KEY,
    zone_id              VARCHAR(32)  NOT NULL UNIQUE,
    name                 VARCHAR(128) NOT NULL,
    recipe_id            VARCHAR(64)  NOT NULL REFERENCES gh_recipes (recipe_id),
    climate_profile_id   VARCHAR(64)  NOT NULL DEFAULT 'cq-winter-fog',
    auto_control         BOOLEAN      NOT NULL DEFAULT TRUE,
    aggregation          VARCHAR(16)  NOT NULL DEFAULT 'AVG',
    shade_open_percent   INT          NOT NULL DEFAULT 100,
    cover_transmittance  NUMERIC(4, 3) NOT NULL DEFAULT 0.650,
    length_m             NUMERIC(8, 2) NOT NULL DEFAULT 16,
    width_m              NUMERIC(8, 2) NOT NULL DEFAULT 7,
    last_effective_ppfd  NUMERIC(8, 2),
    last_dli             NUMERIC(8, 3) NOT NULL DEFAULT 0,
    last_rule_at         TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gh_devices
(
    id                 BIGSERIAL PRIMARY KEY,
    device_sn          VARCHAR(64)  NOT NULL UNIQUE,
    device_name        VARCHAR(128) NOT NULL,
    zone_id            VARCHAR(32)  NOT NULL REFERENCES gh_zones (zone_id),
    device_type        VARCHAR(32)  NOT NULL, -- PAR_SENSOR | GROW_LAMP | SHADE_ACTUATOR
    model              VARCHAR(64)  NOT NULL,
    adapter_id         VARCHAR(64)  NOT NULL DEFAULT 'sim.par',
    pos_x              NUMERIC(8, 3),
    pos_y              NUMERIC(8, 3),
    pos_z              NUMERIC(8, 3),
    online_status      VARCHAR(16)  NOT NULL DEFAULT 'ONLINE',
    dimming_percent    INT,
    shade_open_percent INT,
    power_on           BOOLEAN,
    last_ppfd          NUMERIC(8, 2),
    last_lux           NUMERIC(10, 2),
    last_temp_c        NUMERIC(6, 2),
    last_humidity_pct  NUMERIC(6, 2),
    last_seen_at       TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_gh_devices_zone ON gh_devices (zone_id);

CREATE TABLE IF NOT EXISTS gh_telemetry
(
    id         BIGSERIAL PRIMARY KEY,
    device_sn  VARCHAR(64)  NOT NULL,
    zone_id    VARCHAR(32)  NOT NULL,
    ppfd       NUMERIC(8, 2),
    lux        NUMERIC(10, 2),
    temp_c     NUMERIC(6, 2),
    humidity   NUMERIC(6, 2),
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_gh_telemetry_zone_time ON gh_telemetry (zone_id, created_at DESC);

CREATE TABLE IF NOT EXISTS gh_control_logs
(
    id                BIGSERIAL PRIMARY KEY,
    device_sn         VARCHAR(64),
    zone_id           VARCHAR(32),
    command           VARCHAR(64)  NOT NULL,
    source            VARCHAR(16)  NOT NULL DEFAULT 'AUTO', -- AUTO | MANUAL | WORK_ORDER
    payload_json      TEXT,
    execution_status  VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS', -- PENDING | SUCCESS | TIMEOUT | FAIL
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_gh_control_logs_time ON gh_control_logs (created_at DESC);

CREATE TABLE IF NOT EXISTS gh_work_orders
(
    id                     BIGSERIAL PRIMARY KEY,
    zone_id                VARCHAR(32)  NOT NULL,
    status                 VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED | COMPLETED
    reason                 VARCHAR(512) NOT NULL,
    suggested_dimming_pct  INT,
    suggested_shade_pct    INT,
    target_device_sn       VARCHAR(64),
    created_at             TIMESTAMP    NOT NULL DEFAULT now(),
    decided_at             TIMESTAMP,
    completed_at           TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gh_work_orders_status ON gh_work_orders (status, created_at DESC);

-- 配方种子
INSERT INTO gh_recipes (recipe_id, crop, crop_name_zh, stage, ppfd_target_min, ppfd_target_max, ppfd_hard_min, ppfd_hard_max,
                        dli_target_min, dli_target_max, photoperiod_hours)
VALUES ('dendrobium-officinale-tissue-v1', 'DENDROBIUM_OFFICINALE', '铁皮石斛', 'TISSUE', 60, 70, 50, 90, 2.160, 3.020, 12),
       ('dendrobium-officinale-cultivation-v1', 'DENDROBIUM_OFFICINALE', '铁皮石斛', 'CULTIVATION', 90, 120, 70, 140, 3.888, 5.184, 12),
       ('fragaria-greenhouse-winter-v1', 'FRAGARIA_ANANASSA', '设施草莓', 'WINTER', 250, 400, 150, 550, 17.000, 25.000, 12),
       ('anoectochilus-formosanus-biomass-v1', 'ANOECTOCHILUS_FORMOSANUS', '台湾金线莲', 'BIOMASS', 25, 35, 15, 55, 1.260, 1.764, 14)
ON CONFLICT (recipe_id) DO NOTHING;

-- 兼容旧配方 ID
INSERT INTO gh_recipes (recipe_id, crop, crop_name_zh, stage, ppfd_target_min, ppfd_target_max, ppfd_hard_min, ppfd_hard_max,
                        dli_target_min, dli_target_max, photoperiod_hours)
VALUES ('dendrobium-officinale-veg-v1', 'DENDROBIUM_OFFICINALE', '铁皮石斛', 'TISSUE', 60, 70, 50, 90, 2.160, 3.020, 12)
ON CONFLICT (recipe_id) DO NOTHING;

INSERT INTO gh_zones (zone_id, name, recipe_id, climate_profile_id, shade_open_percent, length_m, width_m)
VALUES ('ZONE-A', 'A区·铁皮石斛', 'dendrobium-officinale-tissue-v1', 'cq-winter-fog', 100, 16.0, 7.0),
       ('ZONE-B', 'B区·金线莲/草莓切换', 'anoectochilus-formosanus-biomass-v1', 'cq-winter-fog', 100, 16.0, 7.0)
ON CONFLICT (zone_id) DO NOTHING;

-- 坐标真源：docs/greenhouse/layouts/cq-demo-bay-v1.json（西南角原点 · +X东 · +Y北）
INSERT INTO gh_devices (device_sn, device_name, zone_id, device_type, model, adapter_id, pos_x, pos_y, pos_z, dimming_percent, shade_open_percent, power_on, online_status)
VALUES
    ('PAR-ZONE-A-01', 'A区测点1', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.0, 1.40, 0.50, NULL, NULL, NULL, 'ONLINE'),
    ('PAR-ZONE-A-02', 'A区测点2', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.0, 3.50, 0.50, NULL, NULL, NULL, 'ONLINE'),
    ('PAR-ZONE-A-03', 'A区测点3', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.0, 5.60, 0.50, NULL, NULL, NULL, 'ONLINE'),
    ('LAMP-ZONE-A-01', 'A区灯1', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 2.0, 2.35, 2.30, 20, NULL, TRUE, 'ONLINE'),
    ('LAMP-ZONE-A-02', 'A区灯2', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 2.0, 5.35, 2.30, 20, NULL, TRUE, 'ONLINE'),
    ('LAMP-ZONE-A-03', 'A区灯3', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 6.0, 2.35, 2.30, 20, NULL, TRUE, 'ONLINE'),
    ('LAMP-ZONE-A-04', 'A区灯4', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 6.0, 5.35, 2.30, 20, NULL, TRUE, 'ONLINE'),
    ('SHADE-ZONE-A', 'A区遮阳', 'ZONE-A', 'SHADE_ACTUATOR', 'SIM_SHADE', 'sim.shade', 4.0, 3.50, 3.50, NULL, 100, NULL, 'ONLINE'),
    ('PAR-ZONE-B-01', 'B区测点1', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.0, 1.40, 0.45, NULL, NULL, NULL, 'ONLINE'),
    ('PAR-ZONE-B-02', 'B区测点2', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.0, 3.50, 0.45, NULL, NULL, NULL, 'ONLINE'),
    ('PAR-ZONE-B-03', 'B区测点3', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.0, 5.60, 0.45, NULL, NULL, NULL, 'ONLINE'),
    ('LAMP-ZONE-B-01', 'B区灯1', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 10.0, 2.35, 2.30, 10, NULL, TRUE, 'ONLINE'),
    ('LAMP-ZONE-B-02', 'B区灯2', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 10.0, 5.35, 2.30, 10, NULL, TRUE, 'ONLINE'),
    ('LAMP-ZONE-B-03', 'B区灯3', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 13.5, 3.85, 2.30, 10, NULL, TRUE, 'ONLINE'),
    ('SHADE-ZONE-B', 'B区遮阳', 'ZONE-B', 'SHADE_ACTUATOR', 'SIM_SHADE', 'sim.shade', 12.0, 3.50, 3.50, NULL, 100, NULL, 'ONLINE')
ON CONFLICT (device_sn) DO NOTHING;
