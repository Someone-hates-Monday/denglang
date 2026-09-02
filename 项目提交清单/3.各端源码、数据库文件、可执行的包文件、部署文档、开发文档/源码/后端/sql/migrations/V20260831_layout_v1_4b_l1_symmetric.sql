-- East half L1 racks: same as ZONE-A (mid/north). South beds stay single-layer.

INSERT INTO gh_devices (device_sn, device_name, zone_id, device_type, model, adapter_id, pos_x, pos_y, pos_z, dimming_percent, shade_open_percent, power_on, online_status)
VALUES
 ('LAMP-ZONE-B-L1-01', 'B-M-L1', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 12.00, 3.50, 2.15, 15, NULL, TRUE, 'ONLINE'),
 ('LAMP-ZONE-B-L1-02', 'B-N-L1', 'ZONE-B', 'GROW_LAMP', 'SIM_LED_STRIP', 'sim.lamp', 12.00, 5.60, 2.15, 15, NULL, TRUE, 'ONLINE'),
 ('PAR-ZONE-B-L1-01', 'PAR-B-M-L1', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 3.50, 1.35, NULL, NULL, NULL, 'ONLINE'),
 ('PAR-ZONE-B-L1-02', 'PAR-B-N-L1', 'ZONE-B', 'PAR_SENSOR', 'SIM_PAR', 'sim.par', 12.00, 5.60, 1.35, NULL, NULL, NULL, 'ONLINE')
ON CONFLICT (device_sn) DO UPDATE SET
    device_name = EXCLUDED.device_name,
    model = EXCLUDED.model,
    pos_x = EXCLUDED.pos_x,
    pos_y = EXCLUDED.pos_y,
    pos_z = EXCLUDED.pos_z,
    online_status = EXCLUDED.online_status;
