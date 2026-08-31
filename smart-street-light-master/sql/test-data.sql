-- ============================================================
-- 智慧路灯 — 测试 / 演示数据
-- 数据库: PostgreSQL
-- 说明: 依赖 schema.sql；含 1 台真机位 + 多台模拟路灯，
--       光照按昼夜曲线生成，开关/告警与阈值逻辑对齐。
-- 密码明文 → BCrypt（Hutool 10 轮）:
--   admin123  → $2a$10$rNgAYknaIvMHYpT18sKd7Ob0AvHvoWwk.6gb.oBiaZsMgzW9Q2idC
--   123456    → $2a$10$oDZI6djgYk86X9PEhdPWuuAZ9NhUL69GyCORiUw.Vnv8vd5JUfxg.
--   staff123  → $2a$10$llHBmc6QnS/HiiioooPyt.Rk2Ml7VGIWRZKv5VnAyE9O4c9hA3Y7a
-- ============================================================

DELETE FROM control_logs;
DELETE FROM light_readings;
DELETE FROM alarm_logs;
DELETE FROM threshold_config;
DELETE FROM devices;
DELETE FROM users;

ALTER SEQUENCE users_id_seq RESTART WITH 1;
ALTER SEQUENCE devices_id_seq RESTART WITH 1;
ALTER SEQUENCE light_readings_id_seq RESTART WITH 1;
ALTER SEQUENCE control_logs_id_seq RESTART WITH 1;
ALTER SEQUENCE alarm_logs_id_seq RESTART WITH 1;
ALTER SEQUENCE threshold_config_id_seq RESTART WITH 1;

-- ============================================================
-- 1. 用户
-- ============================================================
-- admin123 / demo123；六角色对齐 RBAC-ROLES.md
INSERT INTO users (username, password, role) VALUES
    ('admin',     '$2a$10$rNgAYknaIvMHYpT18sKd7Ob0AvHvoWwk.6gb.oBiaZsMgzW9Q2idC', 'SYS_ADMIN'),
    ('changzhang','$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'SITE_MANAGER'),
    ('nongyi',    '$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'AGRONOMIST'),
    ('zhongzhi',  '$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'GROWER'),
    ('yunwei',    '$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'DEVICE_OPS'),
    ('xueyuan',   '$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'TRAINEE'),
    ('zhangsan',  '$2a$10$oDZI6djgYk86X9PEhdPWuuAZ9NhUL69GyCORiUw.Vnv8vd5JUfxg.', 'GROWER'),
    ('lisi',      '$2a$10$oDZI6djgYk86X9PEhdPWuuAZ9NhUL69GyCORiUw.Vnv8vd5JUfxg.', 'GROWER'),
    ('wangwu',    '$2a$10$oDZI6djgYk86X9PEhdPWuuAZ9NhUL69GyCORiUw.Vnv8vd5JUfxg.', 'GROWER');

-- ============================================================
-- 2. 路灯设备（id 1 = 真机 BearPi 占位 SN-RM-001）
--    模拟灯覆盖：编组 / 在线 / 开关 / 自动·手动 / 一致性异常
-- ============================================================
INSERT INTO devices (
    device_name, device_sn, status, online_status, control_mode, group_name,
    latitude, longitude, last_heartbeat_time, created_at
) VALUES
    -- 真机位：默认离线，联调成功后由 MQTT 刷新（重庆大学 A 区沙正街一带）
    ('人民路001号路灯', 'SN-RM-001', 'OFF', 'OFFLINE', 'AUTO', '人民路',
     29.56470, 106.46740,
     CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '30 days'),
    -- 人民路模拟：夜间开灯、在线、自动
    ('人民路002号路灯', 'SN-RM-002', 'ON',  'ONLINE',  'AUTO', '人民路',
     29.56485, 106.46820,
     CURRENT_TIMESTAMP - INTERVAL '20 seconds', CURRENT_TIMESTAMP - INTERVAL '25 days'),
    -- 人民路模拟：昼间关灯、在线、自动
    ('人民路003号路灯', 'SN-RM-003', 'OFF', 'ONLINE',  'AUTO', '人民路',
     29.56500, 106.46900,
     CURRENT_TIMESTAMP - INTERVAL '15 seconds', CURRENT_TIMESTAMP - INTERVAL '20 days'),
    -- 解放大道：手动锁定开灯
    ('解放大道东段灯', 'SN-JF-001', 'ON',  'ONLINE',  'MANUAL', '解放大道',
     29.56620, 106.46860,
     CURRENT_TIMESTAMP - INTERVAL '40 seconds', CURRENT_TIMESTAMP - INTERVAL '18 days'),
    -- 解放大道模拟：在线（7 盏 mock 默认均 ONLINE；真机位 SN-RM-001 仍 OFFLINE）
    ('解放大道西段灯', 'SN-JF-002', 'OFF', 'ONLINE', 'AUTO', '解放大道',
     29.56640, 106.46950,
     CURRENT_TIMESTAMP - INTERVAL '35 seconds', CURRENT_TIMESTAMP - INTERVAL '15 days'),
    -- 滨江：在线关灯
    ('滨江步道A灯', 'SN-BJ-001', 'OFF', 'ONLINE',  'AUTO', '滨江路',
     29.56350, 106.46780,
     CURRENT_TIMESTAMP - INTERVAL '25 seconds', CURRENT_TIMESTAMP - INTERVAL '12 days'),
    -- 滨江：一致性异常 — 云端期望 OFF，板端仍 ON（C1 演示）
    ('滨江步道B灯', 'SN-BJ-002', 'ON',  'ONLINE',  'AUTO', '滨江路',
     29.56330, 106.46870,
     CURRENT_TIMESTAMP - INTERVAL '30 seconds', CURRENT_TIMESTAMP - INTERVAL '12 days'),
    -- 未分组：校园主道
    ('校园主道路灯', 'SN-XQ-001', 'OFF', 'ONLINE',  'AUTO', NULL,
     29.56540, 106.46980,
     CURRENT_TIMESTAMP - INTERVAL '50 seconds', CURRENT_TIMESTAMP - INTERVAL '10 days');

-- ============================================================
-- 3. 阈值（开灯 <30，关灯 >80，心跳 180s）
-- ============================================================
INSERT INTO threshold_config (light_threshold_on, light_threshold_off, heartbeat_timeout)
VALUES (30, 80, 60);

-- ============================================================
-- 4. 光照时序（近 3 天，每 30 分钟一点）
--    城市道路冠层近似 lux：夜低、午高、晨昏过渡；每灯加固定偏置 + 噪声
-- ============================================================
WITH hours AS (
    SELECT generate_series(
               date_trunc('hour', CURRENT_TIMESTAMP) - INTERVAL '3 days',
               date_trunc('hour', CURRENT_TIMESTAMP),
               INTERVAL '30 minutes'
           ) AS ts
),
base AS (
    SELECT
        ts,
        EXTRACT(HOUR FROM ts)::int AS h,
        EXTRACT(MINUTE FROM ts)::int AS m,
        -- 演示量级日变化（峰值约 400 lux，与室内真机同量级，避免中午 1000+）
        CASE
            WHEN EXTRACT(HOUR FROM ts) BETWEEN 0 AND 4  THEN 1.5 + (EXTRACT(MINUTE FROM ts) / 60.0)
            WHEN EXTRACT(HOUR FROM ts) = 5             THEN 6 + EXTRACT(MINUTE FROM ts) * 0.4
            WHEN EXTRACT(HOUR FROM ts) = 6             THEN 35 + EXTRACT(MINUTE FROM ts) * 0.9
            WHEN EXTRACT(HOUR FROM ts) = 7             THEN 90 + EXTRACT(MINUTE FROM ts) * 1.2
            WHEN EXTRACT(HOUR FROM ts) BETWEEN 8 AND 10 THEN 180 + (EXTRACT(HOUR FROM ts) - 8) * 50
                                                         + EXTRACT(MINUTE FROM ts) * 0.5
            WHEN EXTRACT(HOUR FROM ts) BETWEEN 11 AND 13 THEN 360 + (12 - ABS(EXTRACT(HOUR FROM ts) - 12)) * 20
                                                         + SIN(EXTRACT(MINUTE FROM ts) / 10.0) * 15
            WHEN EXTRACT(HOUR FROM ts) BETWEEN 14 AND 16 THEN 280 - (EXTRACT(HOUR FROM ts) - 14) * 40
                                                         + EXTRACT(MINUTE FROM ts) * 0.3
            WHEN EXTRACT(HOUR FROM ts) = 17            THEN 120 - EXTRACT(MINUTE FROM ts) * 1.2
            WHEN EXTRACT(HOUR FROM ts) = 18            THEN 55 - EXTRACT(MINUTE FROM ts) * 0.6
            WHEN EXTRACT(HOUR FROM ts) = 19            THEN 22 - EXTRACT(MINUTE FROM ts) * 0.25
            WHEN EXTRACT(HOUR FROM ts) BETWEEN 20 AND 23 THEN 8 - (EXTRACT(HOUR FROM ts) - 20) * 1.2
                                                         + EXTRACT(MINUTE FROM ts) * 0.02
            ELSE 20
        END AS lux0
    FROM hours
),
devs AS (
    -- 偏置：树荫 / 朝向；噪声种子用 device_id
    SELECT * FROM (VALUES
        (1::bigint, '人民路001号路灯', -8.0),   -- 真机位也给历史曲线便于 Web 展示
        (2::bigint, '人民路002号路灯',  5.0),
        (3::bigint, '人民路003号路灯', 12.0),
        (4::bigint, '解放大道东段灯',  -3.0),
        (5::bigint, '解放大道西段灯',  0.0),
        (6::bigint, '滨江步道A灯',     18.0),  -- 临江更亮
        (7::bigint, '滨江步道B灯',     10.0),
        (8::bigint, '校园主道路灯',   -15.0)   -- 树荫更暗
    ) AS t(device_id, device_name, bias)
)
INSERT INTO light_readings (device_id, light_intensity, created_at)
SELECT
    d.device_id,
    ROUND(
        GREATEST(
            0.2,
            b.lux0 + d.bias
              + ((d.device_id * 17 + b.h * 3 + b.m) % 11) - 5  -- 伪随机 ±5
        )::numeric,
        2
    ),
    b.ts
FROM base b
CROSS JOIN devs d
-- 离线西段灯：只保留到 3 小时前的数据（模拟停报）
WHERE NOT (d.device_id = 5 AND b.ts > CURRENT_TIMESTAMP - INTERVAL '3 hours');

-- 把「最新」采样对齐到当前期望开关态（便于演示自动策略）
-- 人民路002：夜间低 lux → ON
UPDATE light_readings SET light_intensity = 12.40
WHERE id = (
    SELECT id FROM light_readings WHERE device_id = 2 ORDER BY created_at DESC LIMIT 1
);
-- 人民路003 / 滨江A / 校园：昼间高 lux → OFF
UPDATE light_readings SET light_intensity = 356.20
WHERE id = (
    SELECT id FROM light_readings WHERE device_id = 3 ORDER BY created_at DESC LIMIT 1
);
UPDATE light_readings SET light_intensity = 412.80
WHERE id = (
    SELECT id FROM light_readings WHERE device_id = 6 ORDER BY created_at DESC LIMIT 1
);
UPDATE light_readings SET light_intensity = 268.50
WHERE id = (
    SELECT id FROM light_readings WHERE device_id = 8 ORDER BY created_at DESC LIMIT 1
);
-- 滨江B：最近仍偏低（本应关灯）但 status 仍为 ON → 一致性异常
UPDATE light_readings SET light_intensity = 95.60
WHERE id = (
    SELECT id FROM light_readings WHERE device_id = 7 ORDER BY created_at DESC LIMIT 1
);
-- 解放东段 MANUAL 开着：白天也开（人为干预）
UPDATE light_readings SET light_intensity = 520.10
WHERE id = (
    SELECT id FROM light_readings WHERE device_id = 4 ORDER BY created_at DESC LIMIT 1
);

-- ============================================================
-- 5. 控制日志（含 PENDING / SUCCESS / TIMEOUT + expected_status）
-- ============================================================
INSERT INTO control_logs (
    device_id, operator_id, command, source, result,
    execution_status, expected_status, created_at
) VALUES
    -- 今日 AUTO 开关（Dashboard「今日自动开关」）
    (2, NULL, 'ON',  'AUTO',   'SUCCESS', 'SUCCESS', 'ON',  CURRENT_TIMESTAMP - INTERVAL '25 minutes'),
    (3, NULL, 'OFF', 'AUTO',   'SUCCESS', 'SUCCESS', 'OFF', CURRENT_TIMESTAMP - INTERVAL '40 minutes'),
    (6, NULL, 'OFF', 'AUTO',   'SUCCESS', 'SUCCESS', 'OFF', CURRENT_TIMESTAMP - INTERVAL '55 minutes'),
    (8, NULL, 'OFF', 'AUTO',   'SUCCESS', 'SUCCESS', 'OFF', CURRENT_TIMESTAMP - INTERVAL '70 minutes'),
    (2, NULL, 'OFF', 'AUTO',   'SUCCESS', 'SUCCESS', 'OFF', CURRENT_TIMESTAMP - INTERVAL '8 hours'),
    (2, NULL, 'ON',  'AUTO',   'SUCCESS', 'SUCCESS', 'ON',  CURRENT_TIMESTAMP - INTERVAL '11 hours'),
    -- 手动
    (4, 1, 'ON',  'MANUAL', 'SUCCESS', 'SUCCESS', 'ON',  CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    (1, 1, 'ON',  'MANUAL', 'SUCCESS', 'TIMEOUT', 'ON',  CURRENT_TIMESTAMP - INTERVAL '90 minutes'),
    -- 滨江B：已下发关灯 SUCCESS 期望 OFF，但设备 status 仍 ON（C1）
    (7, NULL, 'OFF', 'AUTO', 'SUCCESS', 'SUCCESS', 'OFF', CURRENT_TIMESTAMP - INTERVAL '15 minutes'),
    -- 历史
    (5, NULL, 'ON',  'AUTO',   'SUCCESS', 'SUCCESS', 'ON',  CURRENT_TIMESTAMP - INTERVAL '1 day'),
    (5, 1,    'OFF', 'MANUAL', 'SUCCESS', 'TIMEOUT', 'OFF', CURRENT_TIMESTAMP - INTERVAL '2 days'),
    (3, 1,    'ON',  'MANUAL', 'SUCCESS', 'SUCCESS', 'ON',  CURRENT_TIMESTAMP - INTERVAL '3 days');

-- ============================================================
-- 6. 告警（活跃 + 已处理，覆盖 OFFLINE / COMMAND_TIMEOUT / LIGHT_ABNORMAL）
-- ============================================================
INSERT INTO alarm_logs (device_id, alarm_type, message, status, created_at, resolved_at) VALUES
    (5, 'OFFLINE',
     '设备解放大道西段灯历史心跳超时（已恢复在线）',
     'RESOLVED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days' + INTERVAL '40 minutes'),
    (1, 'OFFLINE',
     '设备人民路001号路灯心跳超时，已自动标记为离线',
     'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '100 minutes', NULL),
    (1, 'COMMAND_TIMEOUT',
     '设备人民路001号路灯指令 ON 超过 30s 未收到 status 回执',
     'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '88 minutes', NULL),
    (7, 'LIGHT_ABNORMAL',
     '设备滨江步道B灯高光照下仍保持开灯，期望与实际不一致',
     'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '12 minutes', NULL),
    (2, 'HEARTBEAT_TIMEOUT',
     '设备人民路002号路灯短暂心跳丢失后已恢复',
     'RESOLVED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days' + INTERVAL '20 minutes'),
    (3, 'COMMAND_TIMEOUT',
     '设备人民路003号路灯历史指令超时（已处理）',
     'RESOLVED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '4 days'),
    (8, 'LIGHT_ABNORMAL',
     '设备校园主道路灯低光照持续关灯超过 30 分钟（已处理）',
     'RESOLVED', CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP - INTERVAL '5 days');

-- ============================================================
-- 验证
-- ============================================================
SELECT 'users' AS table_name, COUNT(*) AS rows FROM users
UNION ALL SELECT 'devices', COUNT(*) FROM devices
UNION ALL SELECT 'light_readings', COUNT(*) FROM light_readings
UNION ALL SELECT 'control_logs', COUNT(*) FROM control_logs
UNION ALL SELECT 'threshold_config', COUNT(*) FROM threshold_config
UNION ALL SELECT 'alarm_logs', COUNT(*) FROM alarm_logs
ORDER BY table_name;

SELECT id, device_name, device_sn, status, online_status, control_mode, group_name
FROM devices
ORDER BY id;
