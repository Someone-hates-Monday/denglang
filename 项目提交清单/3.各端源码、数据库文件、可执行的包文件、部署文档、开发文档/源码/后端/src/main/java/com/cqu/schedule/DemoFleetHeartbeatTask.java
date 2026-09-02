package com.cqu.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqu.entity.Devices;
import com.cqu.mapper.DevicesMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 本地演示灯廊心跳：除真机 SN-RM-001 外保持在线，便于总览地图联调。
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "streetlight.demo", name = "fleet-heartbeat", havingValue = "true", matchIfMissing = true)
public class DemoFleetHeartbeatTask {

    private static final String HARDWARE_SN = "SN-RM-001";
    private static final ZoneId CLOCK = ZoneId.of("Asia/Shanghai");

    @Autowired
    private DevicesMapper devicesMapper;

    @Scheduled(fixedRate = 45_000, initialDelay = 4_000)
    public void beat() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        List<Devices> fleet = devicesMapper.selectList(
                new LambdaQueryWrapper<Devices>().ne(Devices::getDeviceSn, HARDWARE_SN));
        for (Devices device : fleet) {
            if ("SN-YJP-005".equals(device.getDeviceSn())) {
                continue;
            }
            device.setLastHeartbeatTime(now);
            if (!"ONLINE".equals(device.getOnlineStatus())) {
                device.setOnlineStatus("ONLINE");
            }
            devicesMapper.updateById(device);
        }
    }
}
