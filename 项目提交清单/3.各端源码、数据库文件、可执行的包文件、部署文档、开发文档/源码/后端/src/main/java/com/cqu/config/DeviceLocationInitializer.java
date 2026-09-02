package com.cqu.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqu.entity.Devices;
import com.cqu.mapper.DevicesMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 启动时写入重庆各区演示路灯（真实商圈/道路方位），接入 devices 表供地图与开关使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceLocationInitializer implements ApplicationRunner {

    private static final ZoneId CLOCK = ZoneId.of("Asia/Shanghai");
    private static final String HARDWARE_SN = "SN-RM-001";

    private final DataSource dataSource;
    private final DevicesMapper devicesMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (!ensureColumns()) {
            return;
        }
        FleetFile fleet = loadFleet();
        if (fleet == null || fleet.groups == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(CLOCK);
        int inserted = 0;
        int updated = 0;
        for (FleetGroup group : fleet.groups) {
            if (group.lamps == null) {
                continue;
            }
            for (FleetLamp lamp : group.lamps) {
                Devices existing = devicesMapper.selectOne(
                        new LambdaQueryWrapper<Devices>().eq(Devices::getDeviceSn, lamp.sn));
                if (existing == null) {
                    Devices device = new Devices();
                    device.setDeviceName(lamp.name);
                    device.setDeviceSn(lamp.sn);
                    device.setStatus(lamp.status == null ? "OFF" : lamp.status);
                    device.setOnlineStatus(HARDWARE_SN.equals(lamp.sn) ? "OFFLINE"
                            : (lamp.online == null ? "ONLINE" : lamp.online));
                    device.setControlMode("AUTO");
                    device.setGroupName(group.name);
                    device.setLatitude(bd(lamp.lat));
                    device.setLongitude(bd(lamp.lng));
                    device.setLastHeartbeatTime(HARDWARE_SN.equals(lamp.sn) ? now.minusHours(2) : now);
                    device.setCreatedAt(now);
                    devicesMapper.insert(device);
                    inserted++;
                } else {
                    existing.setDeviceName(lamp.name);
                    existing.setGroupName(group.name);
                    existing.setLatitude(bd(lamp.lat));
                    existing.setLongitude(bd(lamp.lng));
                    if (!HARDWARE_SN.equals(lamp.sn)) {
                        existing.setOnlineStatus(lamp.online == null ? "ONLINE" : lamp.online);
                        existing.setLastHeartbeatTime(now);
                    }
                    devicesMapper.updateById(existing);
                    updated++;
                }
            }
        }
        remapLegacyDemoLamps();
        log.info("重庆演示灯廊已接入后端：新增 {} 盏，更新 {} 盏", inserted, updated);
    }

    /** 旧库残留的解放大道/滨江演示灯并入三峡广场，避免堆在沙正街。 */
    private void remapLegacyDemoLamps() {
        record Spot(double lat, double lng) {}
        java.util.Map<String, Spot> leftover = java.util.Map.of(
                "SN-JF-001", new Spot(29.5570, 106.4548),
                "SN-JF-002", new Spot(29.5564, 106.4554),
                "SN-BJ-001", new Spot(29.5560, 106.4540),
                "SN-BJ-002", new Spot(29.5582, 106.4564));
        for (var e : leftover.entrySet()) {
            Devices existing = devicesMapper.selectOne(
                    new LambdaQueryWrapper<Devices>().eq(Devices::getDeviceSn, e.getKey()));
            if (existing == null) {
                continue;
            }
            existing.setGroupName("三峡广场");
            existing.setLatitude(bd(e.getValue().lat()));
            existing.setLongitude(bd(e.getValue().lng()));
            devicesMapper.updateById(existing);
        }
    }

    private FleetFile loadFleet() {
        try (InputStream in = new ClassPathResource("demo/chongqing-fleet.json").getInputStream()) {
            return objectMapper.readValue(in, FleetFile.class);
        } catch (Exception e) {
            log.warn("读取重庆灯廊演示数据失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean ensureColumns() {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE devices ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7)");
            st.execute("ALTER TABLE devices ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7)");
            return true;
        } catch (SQLException e) {
            log.warn("补齐 devices 经纬度列失败: {}", e.getMessage());
            return false;
        }
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(7, RoundingMode.HALF_UP);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FleetFile {
        public List<FleetGroup> groups;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FleetGroup {
        public String name;
        public String district;
        public String color;
        public List<FleetLamp> lamps;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FleetLamp {
        public String sn;
        public String name;
        public double lat;
        public double lng;
        public String status;
        public String online;
    }
}
