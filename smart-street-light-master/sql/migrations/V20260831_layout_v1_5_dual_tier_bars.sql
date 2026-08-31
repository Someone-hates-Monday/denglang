-- v1.5 双层独立补光：下层灯收到搁架下；L1 与 L0 同密（每床 5 灯 + 5 PAR）
-- 灯型改为园艺红蓝灯带 HORTI_BAR_RB_150（冠层峰值 150），贴合石斛栽培带 90–120

-- 南床单层：灯仍在冠层上方（无搁架）
UPDATE gh_devices
SET pos_z = 1.75, model = 'HORTI_BAR_RB_150'
WHERE device_type = 'GROW_LAMP'
  AND device_sn ~ '^LAMP-ZONE-[AB]-0[1-5]$';

-- 中/北床下层：灯收到 L1 搁架下方，直接照 L0 冠层（不再被搁架挡住）
UPDATE gh_devices
SET pos_z = 1.20, model = 'HORTI_BAR_RB_150'
WHERE device_type = 'GROW_LAMP'
  AND device_sn ~ '^LAMP-ZONE-[AB]-(0[6-9]|1[0-5])$';

INSERT INTO gh_devices (device_sn, device_name, zone_id, device_type, model, adapter_id, pos_x, pos_y, pos_z, dimming_percent, shade_open_percent, power_on, online_status)
VALUES
 -- ZONE-A L1 中床
 ('LAMP-ZONE-A-L1-01', 'A-M-L1-1', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 1.15, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-02', 'A-M-L1-2', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 2.55, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-03', 'A-M-L1-3', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 4.00, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-04', 'A-M-L1-4', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 5.45, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-05', 'A-M-L1-5', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 6.85, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 -- ZONE-A L1 北床
 ('LAMP-ZONE-A-L1-06', 'A-N-L1-1', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 1.15, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-07', 'A-N-L1-2', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 2.55, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-08', 'A-N-L1-3', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 4.00, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-09', 'A-N-L1-4', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 5.45, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-A-L1-10', 'A-N-L1-5', 'ZONE-A', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 6.85, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('PAR-ZONE-A-L1-01', 'PAR-A-M-L1-1', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 1.15, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-02', 'PAR-A-M-L1-2', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.55, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-03', 'PAR-A-M-L1-3', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-04', 'PAR-A-M-L1-4', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 5.45, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-05', 'PAR-A-M-L1-5', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.85, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-06', 'PAR-A-N-L1-1', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 1.15, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-07', 'PAR-A-N-L1-2', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 2.55, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-08', 'PAR-A-N-L1-3', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 4.00, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-09', 'PAR-A-N-L1-4', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 5.45, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-A-L1-10', 'PAR-A-N-L1-5', 'ZONE-A', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 6.85, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 -- ZONE-B L1 中床
 ('LAMP-ZONE-B-L1-01', 'B-M-L1-1', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 9.15, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-02', 'B-M-L1-2', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 10.55, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-03', 'B-M-L1-3', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 12.00, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-04', 'B-M-L1-4', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 13.45, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-05', 'B-M-L1-5', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 14.85, 3.50, 1.70, 20, NULL, TRUE, 'ONLINE'),
 -- ZONE-B L1 北床
 ('LAMP-ZONE-B-L1-06', 'B-N-L1-1', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 9.15, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-07', 'B-N-L1-2', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 10.55, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-08', 'B-N-L1-3', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 12.00, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-09', 'B-N-L1-4', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 13.45, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-10', 'B-N-L1-5', 'ZONE-B', 'GROW_LAMP', 'HORTI_BAR_RB_150', 'sim.lamp', 14.85, 5.60, 1.70, 20, NULL, TRUE, 'ONLINE'),
 ('PAR-ZONE-B-L1-01', 'PAR-B-M-L1-1', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 9.15, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-02', 'PAR-B-M-L1-2', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.55, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-03', 'PAR-B-M-L1-3', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-04', 'PAR-B-M-L1-4', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 13.45, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-05', 'PAR-B-M-L1-5', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.85, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-06', 'PAR-B-N-L1-1', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 9.15, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-07', 'PAR-B-N-L1-2', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 10.55, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-08', 'PAR-B-N-L1-3', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-09', 'PAR-B-N-L1-4', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 13.45, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-10', 'PAR-B-N-L1-5', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 14.85, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE')
ON CONFLICT (device_sn) DO UPDATE SET
    device_name = EXCLUDED.device_name,
    model = EXCLUDED.model,
    pos_x = EXCLUDED.pos_x,
    pos_y = EXCLUDED.pos_y,
    pos_z = EXCLUDED.pos_z,
    dimming_percent = EXCLUDED.dimming_percent,
    power_on = EXCLUDED.power_on,
    online_status = EXCLUDED.online_status;
