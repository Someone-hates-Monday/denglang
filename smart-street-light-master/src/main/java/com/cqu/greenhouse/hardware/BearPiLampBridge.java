package com.cqu.greenhouse.hardware;

import com.cqu.config.MqttConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 演示用：将指定虚拟补光灯的开关状态镜像到 BearPi（仅 ON/OFF，不传调光百分比）。
 */
@Slf4j
@Component
public class BearPiLampBridge {

    @Value("${greenhouse.hardware-bridge.enabled:false}")
    private boolean enabled;

    @Value("${greenhouse.hardware-bridge.lamp-sn:LAMP-ZONE-A-01}")
    private String lampSn;

    @Value("${greenhouse.hardware-bridge.bearpi-sn:SN-RM-001}")
    private String bearpiSn;

    /** 上次已下发到 BearPi 的开关状态，避免 AUTO 调光时重复 MQTT */
    private final AtomicBoolean lastPublishedOn = new AtomicBoolean(false);
    private volatile boolean hasPublished;

    public void onLampPowerChanged(String deviceSn, boolean powerOn, MqttConfig mqttConfig) {
        if (!enabled || deviceSn == null || !lampSn.equals(deviceSn)) {
            return;
        }
        if (hasPublished && lastPublishedOn.get() == powerOn) {
            return;
        }
        String command = powerOn ? "MANUAL_ON" : "MANUAL_OFF";
        boolean ok = mqttConfig.publishCommand(bearpiSn, command);
        if (ok) {
            lastPublishedOn.set(powerOn);
            hasPublished = true;
            log.info("BearPi 灯桥接: {} powerOn={} -> {} {}", lampSn, powerOn, bearpiSn, command);
        } else {
            log.warn("BearPi 灯桥接下发失败: {} -> {} {}", lampSn, bearpiSn, command);
        }
    }
}
