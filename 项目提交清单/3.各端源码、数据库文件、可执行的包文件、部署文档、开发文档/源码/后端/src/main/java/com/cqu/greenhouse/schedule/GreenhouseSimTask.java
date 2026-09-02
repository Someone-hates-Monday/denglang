package com.cqu.greenhouse.schedule;

import com.cqu.greenhouse.service.IGreenhouseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 内置光棚仿真：重庆日型 + 光场 + 规则。默认 local 开启。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "greenhouse.sim", name = "enabled", havingValue = "true")
public class GreenhouseSimTask {

    @Autowired
    private IGreenhouseService greenhouseService;

    @Value("${greenhouse.sim.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${greenhouse.sim.interval-ms:5000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            greenhouseService.tickSimulation();
        } catch (Exception e) {
            log.warn("温室仿真 tick 失败: {}", e.getMessage());
        }
    }
}
