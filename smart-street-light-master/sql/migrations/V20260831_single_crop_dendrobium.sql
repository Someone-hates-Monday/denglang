-- 整跨单作物：全棚铁皮石斛（ZONE-A/B 仅为半跨控光分区，不混种）
UPDATE gh_zones
SET name = '西半跨·石斛',
    recipe_id = 'dendrobium-officinale-cultivation-v1'
WHERE zone_id = 'ZONE-A';

UPDATE gh_zones
SET name = '东半跨·石斛',
    recipe_id = 'dendrobium-officinale-cultivation-v1'
WHERE zone_id = 'ZONE-B';

-- B 区测点与 A 对齐到石斛冠层 0.90 m
UPDATE gh_devices SET pos_z = 0.90
WHERE device_sn LIKE 'PAR-ZONE-B-%' AND device_type = 'PAR_SENSOR';

-- B 区灯峰值与 A 区一致（同作物同档）
UPDATE gh_devices SET dimming_percent = 25
WHERE device_sn LIKE 'LAMP-ZONE-B-%' AND device_type = 'GROW_LAMP';
