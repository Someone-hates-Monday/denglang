-- Layout v1.3: denser per-bed lamps (3/bed), raised to Z=1.85, aligned PAR sensors
-- See docs/greenhouse/LIGHTING-UPGRADE-v1.3.md

INSERT INTO gh_devices (device_sn, device_name, zone_id, device_type, model, adapter_id, pos_x, pos_y, pos_z, dimming_percent, shade_open_percent, power_on, online_status)
VALUES
 ('LAMP-ZONE-A-01', 'A南床西灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 1.75, 1.40, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-02', 'A南床中灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 4.00, 1.40, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-03', 'A南床东灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 6.25, 1.40, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-04', 'A中床西灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 1.75, 3.50, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-05', 'A中床中灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 4.00, 3.50, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-06', 'A中床东灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 6.25, 3.50, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-07', 'A北床西灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 1.75, 5.60, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-08', 'A北床中灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 4.00, 5.60, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-09', 'A北床东灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 6.25, 5.60, 1.85, 25, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-01', 'A中床上层灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 4.00, 3.50, 2.15, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-02', 'A北床上层灯', 'ZONE-A', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 4.00, 5.60, 2.15, 15, NULL, TRUE, 'ONLINE'),
 ('PAR-ZONE-A-01', 'A南床西测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 1.75, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-02', 'A南床中测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-03', 'A南床东测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.25, 1.40, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-04', 'A中床西测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 1.75, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-05', 'A中床中测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-06', 'A中床东测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.25, 3.50, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-07', 'A北床西测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 1.75, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-08', 'A北床中测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-09', 'A北床东测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.25, 5.60, 0.90, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-01', 'A中床上层测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-02', 'A北床上层测', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('LAMP-ZONE-B-01', 'B南床西灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 9.75, 1.40, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-02', 'B南床中灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 12.00, 1.40, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-03', 'B南床东灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 14.25, 1.40, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-04', 'B中床西灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 9.75, 3.50, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-05', 'B中床中灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 12.00, 3.50, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-06', 'B中床东灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 14.25, 3.50, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-07', 'B北床西灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 9.75, 5.60, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-08', 'B北床中灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 12.00, 5.60, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-09', 'B北床东灯', 'ZONE-B', 'GROW_LAMP', 'SIM_LAMP', 'sim.lamp', 14.25, 5.60, 1.85, 15, NULL, TRUE, 'ONLINE'),
 ('PAR-ZONE-B-01', 'B南床西测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 9.75, 1.40, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-02', 'B南床中测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 1.40, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-03', 'B南床东测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.25, 1.40, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-04', 'B中床西测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 9.75, 3.50, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-05', 'B中床中测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 3.50, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-06', 'B中床东测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.25, 3.50, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-07', 'B北床西测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 9.75, 5.60, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-08', 'B北床中测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 5.60, 0.78, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-09', 'B北床东测', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.25, 5.60, 0.78, NULL, NULL, NULL, 'ONLINE')
ON CONFLICT (device_sn) DO UPDATE SET
    device_name = EXCLUDED.device_name,
    pos_x = EXCLUDED.pos_x,
    pos_y = EXCLUDED.pos_y,
    pos_z = EXCLUDED.pos_z,
    online_status = EXCLUDED.online_status;

UPDATE gh_devices SET pos_x = 4.0, pos_y = 6.70, pos_z = 3.50 WHERE device_sn = 'SHADE-ZONE-A';
UPDATE gh_devices SET pos_x = 12.0, pos_y = 6.70, pos_z = 3.50 WHERE device_sn = 'SHADE-ZONE-B';
