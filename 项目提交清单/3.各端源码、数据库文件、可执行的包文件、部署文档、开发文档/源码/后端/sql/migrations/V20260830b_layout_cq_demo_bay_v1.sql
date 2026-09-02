-- 对齐 docs/greenhouse/layouts/cq-demo-bay-v1.json
-- 棚体 16×7、灯/PAR/遮阳坐标与数量（A:4灯+3PAR，B:3灯+3PAR）

UPDATE gh_zones
SET length_m = 16.0,
    width_m  = 7.0,
    cover_transmittance = 0.650
WHERE zone_id IN ('ZONE-A', 'ZONE-B');

-- 更新既有设备坐标
UPDATE gh_devices SET pos_x = 2.0,  pos_y = 1.40, pos_z = 0.50 WHERE device_sn = 'PAR-ZONE-A-01';
UPDATE gh_devices SET pos_x = 4.0,  pos_y = 3.50, pos_z = 0.50 WHERE device_sn = 'PAR-ZONE-A-02';
UPDATE gh_devices SET pos_x = 6.0,  pos_y = 5.60, pos_z = 0.50 WHERE device_sn = 'PAR-ZONE-A-03';

UPDATE gh_devices SET pos_x = 2.0,  pos_y = 2.35, pos_z = 2.30, dimming_percent = COALESCE(dimming_percent, 20), power_on = TRUE
WHERE device_sn = 'LAMP-ZONE-A-01';
UPDATE gh_devices SET pos_x = 2.0,  pos_y = 5.35, pos_z = 2.30, dimming_percent = COALESCE(dimming_percent, 20), power_on = TRUE
WHERE device_sn = 'LAMP-ZONE-A-02';

UPDATE gh_devices SET pos_x = 4.0,  pos_y = 3.50, pos_z = 3.50 WHERE device_sn = 'SHADE-ZONE-A';

UPDATE gh_devices SET pos_x = 10.0, pos_y = 1.40, pos_z = 0.45 WHERE device_sn = 'PAR-ZONE-B-01';
UPDATE gh_devices SET pos_x = 12.0, pos_y = 3.50, pos_z = 0.45 WHERE device_sn = 'PAR-ZONE-B-02';

UPDATE gh_devices SET pos_x = 10.0, pos_y = 2.35, pos_z = 2.30, dimming_percent = COALESCE(dimming_percent, 10), power_on = TRUE
WHERE device_sn = 'LAMP-ZONE-B-01';

UPDATE gh_devices SET pos_x = 12.0, pos_y = 3.50, pos_z = 3.50 WHERE device_sn = 'SHADE-ZONE-B';

-- 补齐布局新增设备（幂等）
INSERT INTO gh_devices (device_sn, device_name, zone_id, device_type, model, adapter_id, pos_x, pos_y, pos_z, dimming_percent, shade_open_percent, power_on, online_status)
VALUES
    ('LAMP-ZONE-A-03', 'A区灯3', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 6.0, 2.35, 2.30, 20, NULL, TRUE, 'ONLINE'),
    ('LAMP-ZONE-A-04', 'A区灯4', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 6.0, 5.35, 2.30, 20, NULL, TRUE, 'ONLINE'),
    ('LAMP-ZONE-B-02', 'B区灯2', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 10.0, 5.35, 2.30, 10, NULL, TRUE, 'ONLINE'),
    ('LAMP-ZONE-B-03', 'B区灯3', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 13.5, 3.85, 2.30, 10, NULL, TRUE, 'ONLINE'),
    ('PAR-ZONE-B-03', 'B区测点3', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.0, 5.60, 0.45, NULL, NULL, NULL, 'ONLINE')
ON CONFLICT (device_sn) DO UPDATE SET
    pos_x = EXCLUDED.pos_x,
    pos_y = EXCLUDED.pos_y,
    pos_z = EXCLUDED.pos_z,
    device_name = EXCLUDED.device_name,
    zone_id = EXCLUDED.zone_id,
    dimming_percent = COALESCE(gh_devices.dimming_percent, EXCLUDED.dimming_percent),
    power_on = COALESCE(gh_devices.power_on, EXCLUDED.power_on),
    online_status = 'ONLINE';
