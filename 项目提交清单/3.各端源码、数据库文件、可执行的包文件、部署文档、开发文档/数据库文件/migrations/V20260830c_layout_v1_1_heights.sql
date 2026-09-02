-- cq-demo-bay-v1.1：高架床、冠层测光、灯下距 0.55m、遮阳卷轴在北侧
UPDATE gh_devices SET pos_z = 0.90 WHERE device_sn IN ('PAR-ZONE-A-01', 'PAR-ZONE-A-02', 'PAR-ZONE-A-03');
UPDATE gh_devices SET pos_z = 0.78 WHERE device_sn IN ('PAR-ZONE-B-01', 'PAR-ZONE-B-02', 'PAR-ZONE-B-03');

UPDATE gh_devices SET pos_z = 1.45 WHERE device_sn LIKE 'LAMP-ZONE-%';

UPDATE gh_devices SET pos_x = 4.0,  pos_y = 6.70, pos_z = 3.50 WHERE device_sn = 'SHADE-ZONE-A';
UPDATE gh_devices SET pos_x = 12.0, pos_y = 6.70, pos_z = 3.50 WHERE device_sn = 'SHADE-ZONE-B';
