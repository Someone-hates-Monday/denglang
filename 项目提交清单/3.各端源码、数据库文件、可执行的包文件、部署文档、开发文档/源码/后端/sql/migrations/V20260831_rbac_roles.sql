-- R1: 六角色码迁移 + 演示账号种子（对齐 docs/greenhouse/RBAC-ROLES.md）
-- 旧码: ADMIN → SYS_ADMIN, MUNICIPAL_STAFF → GROWER
-- 密码: admin123 / demo123（BCrypt Hutool 兼容）

UPDATE users SET role = 'SYS_ADMIN' WHERE role IN ('ADMIN', 'admin');
UPDATE users SET role = 'GROWER' WHERE role IN ('MUNICIPAL_STAFF', 'municipal_staff');

ALTER TABLE users ALTER COLUMN role SET DEFAULT 'GROWER';

COMMENT ON COLUMN users.role IS '角色: SITE_MANAGER|AGRONOMIST|GROWER|DEVICE_OPS|TRAINEE|SYS_ADMIN（旧 ADMIN/MUNICIPAL_STAFF 已迁移）';

-- admin123
-- $2a$10$rNgAYknaIvMHYpT18sKd7Ob0AvHvoWwk.6gb.oBiaZsMgzW9Q2idC
-- demo123 → $2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52

INSERT INTO users (username, password, role) VALUES
 ('admin',     '$2a$10$rNgAYknaIvMHYpT18sKd7Ob0AvHvoWwk.6gb.oBiaZsMgzW9Q2idC', 'SYS_ADMIN'),
 ('changzhang','$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'SITE_MANAGER'),
 ('nongyi',    '$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'AGRONOMIST'),
 ('zhongzhi',  '$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'GROWER'),
 ('yunwei',    '$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'DEVICE_OPS'),
 ('xueyuan',   '$2a$10$g3a/r.WvmpvfssJJlROeE.auh5X0iCrPEyIHrv4fJVwvwQO9jPX52', 'TRAINEE')
ON CONFLICT (username) DO UPDATE SET
    password = EXCLUDED.password,
    role = EXCLUDED.role;
