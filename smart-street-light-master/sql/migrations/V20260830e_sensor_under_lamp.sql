-- PAR 对齐灯下冠层：测点在床轴灯正下方，高度=测量面（A 0.90 / B 0.78；L1=1.35）
UPDATE gh_devices SET pos_x = 2.5, pos_y = 1.40, pos_z = 0.90 WHERE device_sn = 'PAR-ZONE-A-01';
UPDATE gh_devices SET pos_x = 5.5, pos_y = 1.40, pos_z = 0.90 WHERE device_sn = 'PAR-ZONE-A-02';
UPDATE gh_devices SET pos_x = 2.5, pos_y = 3.50, pos_z = 0.90 WHERE device_sn = 'PAR-ZONE-A-03';
UPDATE gh_devices SET pos_x = 5.5, pos_y = 3.50, pos_z = 0.90 WHERE device_sn = 'PAR-ZONE-A-04';
UPDATE gh_devices SET pos_x = 2.5, pos_y = 5.60, pos_z = 0.90 WHERE device_sn = 'PAR-ZONE-A-05';
UPDATE gh_devices SET pos_x = 5.5, pos_y = 5.60, pos_z = 0.90 WHERE device_sn = 'PAR-ZONE-A-06';
UPDATE gh_devices SET pos_x = 4.0, pos_y = 3.50, pos_z = 1.35 WHERE device_sn = 'PAR-ZONE-A-L1-01';
UPDATE gh_devices SET pos_x = 4.0, pos_y = 5.60, pos_z = 1.35 WHERE device_sn = 'PAR-ZONE-A-L1-02';

UPDATE gh_devices SET pos_x = 10.5, pos_y = 1.40, pos_z = 0.78 WHERE device_sn = 'PAR-ZONE-B-01';
UPDATE gh_devices SET pos_x = 13.5, pos_y = 1.40, pos_z = 0.78 WHERE device_sn = 'PAR-ZONE-B-02';
UPDATE gh_devices SET pos_x = 10.5, pos_y = 3.50, pos_z = 0.78 WHERE device_sn = 'PAR-ZONE-B-03';
UPDATE gh_devices SET pos_x = 13.5, pos_y = 3.50, pos_z = 0.78 WHERE device_sn = 'PAR-ZONE-B-04';
UPDATE gh_devices SET pos_x = 10.5, pos_y = 5.60, pos_z = 0.78 WHERE device_sn = 'PAR-ZONE-B-05';
UPDATE gh_devices SET pos_x = 13.5, pos_y = 5.60, pos_z = 0.78 WHERE device_sn = 'PAR-ZONE-B-06';
