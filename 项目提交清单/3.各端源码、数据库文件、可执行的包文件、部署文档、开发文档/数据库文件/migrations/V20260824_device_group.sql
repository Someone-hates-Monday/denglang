-- 路灯编组：同名设备归为一组，空表示未分组
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS group_name VARCHAR(64);

COMMENT ON COLUMN devices.group_name IS '编组名称，同名为一组；NULL/空=未分组';

CREATE INDEX IF NOT EXISTS idx_devices_group_name ON devices (group_name);
