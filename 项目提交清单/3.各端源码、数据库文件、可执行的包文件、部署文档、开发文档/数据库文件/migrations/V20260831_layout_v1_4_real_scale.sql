-- Layout v1.4 real-scale: 1.2m beds, 5 LED-strip modules/bed, denser PAR
-- Aligns with docs/greenhouse/layouts/cq-demo-bay-v1.json (version 1.4)

-- Drop old L0 lamps/PAR (01-09); keep L1; reinsert denser set
DELETE FROM gh_devices
WHERE device_type IN ('GROW_LAMP', 'PAR_SENSOR')
  AND device_sn ~ '^LAMP-ZONE-[AB]-[0-9]+$'
  AND device_sn !~ 'L1';

DELETE FROM gh_devices
WHERE device_type = 'PAR_SENSOR'
  AND device_sn ~ '^PAR-ZONE-[AB]-[0-9]+$'
  AND device_sn !~ 'L1';

INSERT INTO gh_devices (device_sn, device_name, zone_id, device_type, model, adapter_id, pos_x, pos_y, pos_z, dimming_percent, shade_open_percent, power_on, online_status)
VALUES
 -- ZONE-A L0: 5 strips/bed · Y=1.40/3.50/5.60 · X≈1.15..6.85
 ('LAMP-ZONE-A-01', 'A-S-1', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 1.15, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-02', 'A-S-2', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 2.55, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-03', 'A-S-3', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 4.00, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-04', 'A-S-4', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 5.45, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-05', 'A-S-5', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 6.85, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-06', 'A-M-1', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 1.15, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-07', 'A-M-2', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 2.55, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-08', 'A-M-3', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 4.00, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-09', 'A-M-4', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 5.45, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-10', 'A-M-5', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 6.85, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-11', 'A-N-1', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 1.15, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-12', 'A-N-2', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 2.55, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-13', 'A-N-3', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 4.00, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-14', 'A-N-4', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 5.45, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-15', 'A-N-5', 'ZONE-A', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 6.85, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 -- ZONE-A PAR (同灯 XY)
 ('PAR-ZONE-A-01', 'PAR-A-S-1', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 1.15, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-02', 'PAR-A-S-2', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.55, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-03', 'PAR-A-S-3', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-04', 'PAR-A-S-4', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 5.45, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-05', 'PAR-A-S-5', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.85, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-06', 'PAR-A-M-1', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 1.15, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-07', 'PAR-A-M-2', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.55, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-08', 'PAR-A-M-3', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-09', 'PAR-A-M-4', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 5.45, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-10', 'PAR-A-M-5', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.85, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-11', 'PAR-A-N-1', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 1.15, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-12', 'PAR-A-N-2', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.55, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-13', 'PAR-A-N-3', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-14', 'PAR-A-N-4', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 5.45, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-15', 'PAR-A-N-5', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.85, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 -- ZONE-B L0
 ('LAMP-ZONE-B-01', 'B-S-1', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 9.15, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-02', 'B-S-2', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 10.55, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-03', 'B-S-3', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 12.00, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-04', 'B-S-4', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 13.45, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-05', 'B-S-5', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 14.85, 1.40, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-06', 'B-M-1', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 9.15, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-07', 'B-M-2', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 10.55, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-08', 'B-M-3', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 12.00, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-09', 'B-M-4', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 13.45, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-10', 'B-M-5', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 14.85, 3.50, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-11', 'B-N-1', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 9.15, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-12', 'B-N-2', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 10.55, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-13', 'B-N-3', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 12.00, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-14', 'B-N-4', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 13.45, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-15', 'B-N-5', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 14.85, 5.60, 1.85, 20, NULL, TRUE, 'ONLINE'),
 ('PAR-ZONE-B-01', 'PAR-B-S-1', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 9.15, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-02', 'PAR-B-S-2', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.55, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-03', 'PAR-B-S-3', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-04', 'PAR-B-S-4', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 13.45, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-05', 'PAR-B-S-5', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.85, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-06', 'PAR-B-M-1', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 9.15, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-07', 'PAR-B-M-2', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.55, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-08', 'PAR-B-M-3', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-09', 'PAR-B-M-4', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 13.45, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-10', 'PAR-B-M-5', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.85, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-11', 'PAR-B-N-1', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 9.15, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-12', 'PAR-B-N-2', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.55, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-13', 'PAR-B-N-3', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-14', 'PAR-B-N-4', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 13.45, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-15', 'PAR-B-N-5', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.85, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE')
ON CONFLICT (device_sn) DO UPDATE SET
    device_name = EXCLUDED.device_name,
    model = EXCLUDED.model,
    pos_x = EXCLUDED.pos_x,
    pos_y = EXCLUDED.pos_y,
    pos_z = EXCLUDED.pos_z,
    dimming_percent = COALESCE(gh_devices.dimming_percent, EXCLUDED.dimming_percent),
    online_status = EXCLUDED.online_status;

-- L1 灯/测点对齐中北床中心；B 区 PAR 冠层统一 0.90
UPDATE gh_devices SET pos_x = 4.0, pos_y = 3.50, pos_z = 2.15, model = 'SIM_LED_STRIP'
WHERE device_sn = 'LAMP-ZONE-A-L1-01';
UPDATE gh_devices SET pos_x = 4.0, pos_y = 5.60, pos_z = 2.15, model = 'SIM_LED_STRIP'
WHERE device_sn = 'LAMP-ZONE-A-L1-02';
UPDATE gh_devices SET pos_x = 4.0, pos_y = 3.50, pos_z = 1.35 WHERE device_sn = 'PAR-ZONE-A-L1-01';
UPDATE gh_devices SET pos_x = 4.0, pos_y = 5.60, pos_z = 1.35 WHERE device_sn = 'PAR-ZONE-A-L1-02';
