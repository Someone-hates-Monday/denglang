-- 手动模式：MANUAL 时忽略光照自动开关
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS control_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO';

COMMENT ON COLUMN devices.control_mode IS '控制模式: AUTO-跟随阈值, MANUAL-手动锁定（忽略 AUTO_ON/OFF）';
