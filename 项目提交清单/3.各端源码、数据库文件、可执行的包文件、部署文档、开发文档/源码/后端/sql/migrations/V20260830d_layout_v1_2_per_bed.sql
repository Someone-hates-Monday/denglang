-- v1.2：每床双灯+双 PAR；石斛 L1 补光/测点；坐标对齐床中心线
-- 清理后按布局重插（幂等 upsert）

-- ZONE-A L0：三床 ×（西灯+东灯）+（西PAR+东PAR）
INSERT INTO gh_devices (device_sn, device_name, zone_id, device_type, model, adapter_id, pos_x, pos_y, pos_z, dimming_percent, shade_open_percent, power_on, online_status)
VALUES
 ('LAMP-ZONE-A-01', 'A南床西灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 2.5, 1.40, 1.45, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-02', 'A南床东灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 5.5, 1.40, 1.45, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-03', 'A中床西灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 2.5, 3.50, 1.45, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-04', 'A中床东灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 5.5, 3.50, 1.45, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-05', 'A北床西灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 2.5, 5.60, 1.45, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-06', 'A北床东灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 5.5, 5.60, 1.45, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-01', 'A中床上层灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 4.0, 3.50, 1.95, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-02', 'A北床上层灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 4.0, 5.60, 1.95, 15, NULL, TRUE, 'ONLINE'),
 ('PAR-ZONE-A-01', 'A南床西测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.0, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-02', 'A南床东测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.0, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-03', 'A中床西测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.0, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-04', 'A中床东测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.0, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-05', 'A北床西测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.0, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-06', 'A北床东测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.0, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-01', 'A中床上层测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.0, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-02', 'A北床上层测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.0, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('LAMP-ZONE-B-01', 'B南床西灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 10.5, 1.40, 1.45, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-02', 'B南床东灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 13.5, 1.40, 1.45, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-03', 'B中床西灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 10.5, 3.50, 1.45, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-04', 'B中床东灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 13.5, 3.50, 1.45, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-05', 'B北床西灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 10.5, 5.60, 1.45, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-06', 'B北床东灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 13.5, 5.60, 1.45, 15, NULL, TRUE, 'ONLINE'),
 ('PAR-ZONE-B-01', 'B南床西测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.0, 1.40, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-02', 'B南床东测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.0, 1.40, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-03', 'B中床西测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.0, 3.50, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-04', 'B中床东测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.0, 3.50, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-05', 'B北床西测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.0, 5.60, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-06', 'B北床东测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.0, 5.60, 0.78, NULL, NULL, NULL, 'ONLINE')
ON CONFLICT (device_sn) DO UPDATE SET
    device_name = EXCLUDED.device_name,
    zone_id = EXCLUDED.zone_id,
    pos_x = EXCLUDED.pos_x,
    pos_y = EXCLUDED.pos_y,
    pos_z = EXCLUDED.pos_z,
    dimming_percent = COALESCE(gh_devices.dimming_percent, EXCLUDED.dimming_percent),
    power_on = COALESCE(gh_devices.power_on, EXCLUDED.power_on),
    online_status = 'ONLINE';

UPDATE gh_devices SET pos_x = 4.0, pos_y = 6.70, pos_z = 3.50 WHERE device_sn = 'SHADE-ZONE-A';
UPDATE gh_devices SET pos_x = 12.0, pos_y = 6.70, pos_z = 3.50 WHERE device_sn = 'SHADE-ZONE-B';
