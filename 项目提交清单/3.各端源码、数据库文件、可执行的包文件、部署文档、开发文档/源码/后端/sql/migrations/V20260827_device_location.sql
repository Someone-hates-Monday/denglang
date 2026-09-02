-- 路灯地图坐标（GCJ-02，与高德底图一致）
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7);

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);

COMMENT ON COLUMN devices.latitude IS '纬度（GCJ-02，与高德底图一致）；NULL=未标定';
COMMENT ON COLUMN devices.longitude IS '经度（GCJ-02，与高德底图一致）；NULL=未标定';

-- 演示数据：重庆大学 A 区（沙正街）一带的灯廊点位（不覆盖已手工标定的坐标）
UPDATE devices SET latitude = 29.56470, longitude = 106.46740 WHERE device_sn = 'SN-RM-001' AND latitude IS NULL;
UPDATE devices SET latitude = 29.56485, longitude = 106.46820 WHERE device_sn = 'SN-RM-002' AND latitude IS NULL;
UPDATE devices SET latitude = 29.56500, longitude = 106.46900 WHERE device_sn = 'SN-RM-003' AND latitude IS NULL;
UPDATE devices SET latitude = 29.56620, longitude = 106.46860 WHERE device_sn = 'SN-JF-001' AND latitude IS NULL;
UPDATE devices SET latitude = 29.56640, longitude = 106.46950 WHERE device_sn = 'SN-JF-002' AND latitude IS NULL;
UPDATE devices SET latitude = 29.56350, longitude = 106.46780 WHERE device_sn = 'SN-BJ-001' AND latitude IS NULL;
UPDATE devices SET latitude = 29.56330, longitude = 106.46870 WHERE device_sn = 'SN-BJ-002' AND latitude IS NULL;
UPDATE devices SET latitude = 29.56540, longitude = 106.46980 WHERE device_sn = 'SN-XQ-001' AND latitude IS NULL;
