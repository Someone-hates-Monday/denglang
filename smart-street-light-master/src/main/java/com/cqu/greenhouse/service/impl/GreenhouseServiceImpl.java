package com.cqu.greenhouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqu.config.MqttConfig;
import com.cqu.greenhouse.entity.*;
import com.cqu.greenhouse.mapper.*;
import com.cqu.greenhouse.service.IGreenhouseService;
import com.cqu.greenhouse.sim.ClimateProfiles;
import com.cqu.greenhouse.sim.DynamicLightTarget;
import com.cqu.greenhouse.sim.GreenhouseGeometry;
import com.cqu.greenhouse.sim.LightEconomics;
import com.cqu.greenhouse.sim.LightFieldModel;
import com.cqu.greenhouse.sim.SpectrumShares;
import com.cqu.vo.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GreenhouseServiceImpl implements IGreenhouseService {

    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    private GhZoneMapper zoneMapper;
    @Autowired
    private GhRecipeMapper recipeMapper;
    @Autowired
    private GhDeviceMapper deviceMapper;
    @Autowired
    private GhTelemetryMapper telemetryMapper;
    @Autowired
    private GhControlLogMapper controlLogMapper;
    @Autowired
    private GhWorkOrderMapper workOrderMapper;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Lazy
    @Autowired
    private MqttConfig mqttConfig;

    @org.springframework.beans.factory.annotation.Value("${greenhouse.sim.day-compress-sec:120}")
    private int dayCompressSec;

    @org.springframework.beans.factory.annotation.Value("${greenhouse.sim.interval-ms:1000}")
    private int intervalMs;

    /** zoneId → 上次自动动作（仿真分钟，浮点） */
    private final Map<String, Double> lastActionSimMinute = new ConcurrentHashMap<>();
    /** 仿真时刻（浮点分钟）：连续推进，避免整数大步跳动 */
    private volatile double simMinuteOfDay = 0;
    /** zoneId → 当日曲线采样（内存，跨日清空） */
    private final Map<String, List<Map<String, Object>>> daySeries = new ConcurrentHashMap<>();
    /** 全日采样上限：250ms×120s≈480；留余量 */
    private static final int SERIES_CAP = 720;

    /** 每 tick 推进的仿真分钟数（可为小数，全日仍约 dayCompressSec 墙钟秒）。 */
    private double minutesPerTick() {
        double perSec = 1440.0 / Math.max(1, dayCompressSec);
        return Math.max(0.05, perSec * (intervalMs / 1000.0));
    }

    @Override
    public List<GhZone> listZones() {
        return zoneMapper.selectList(new LambdaQueryWrapper<GhZone>().orderByAsc(GhZone::getZoneId));
    }

    @Override
    public Map<String, Object> getZoneEffectiveLight(String zoneId) {
        GhZone zone = requireZone(zoneId);
        List<GhDevice> devices = devicesOf(zoneId);
        double minute = currentMinuteOfDay();
        double outdoor = ClimateProfiles.outdoorParAt(zone.getClimateProfileId(), minute);
        LightFieldModel.FieldResult field = LightFieldModel.compute(zone, devices, outdoor, minute);
        LightFieldModel.FieldResult natural = LightFieldModel.compute(zone, devices, outdoor, minute, 100, false);

        double humidity = synthHumidity(minute, zone.getShadeOpenPercent());
        double tempC = synthTemp(minute, outdoor);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("zoneId", zoneId);
        out.put("name", zone.getName());
        out.put("recipeId", zone.getRecipeId());
        out.put("climateProfileId", zone.getClimateProfileId());
        out.put("geometryId", GreenhouseGeometry.GEOMETRY_ID);
        out.put("minuteOfDay", round(minute));
        out.put("dayProgress", minute / 1440.0);
        out.put("dayCompressSec", dayCompressSec);
        out.put("intervalMs", intervalMs);
        out.put("minutesPerTick", round(minutesPerTick() * 100.0) / 100.0);
        out.put("outdoorParPpfd", round(outdoor));
        out.put("sunInPpfd", round(field.outdoorInPpfd()));
        out.put("naturalPpfd", round(natural.effectivePpfd()));
        out.put("ledPpfd", round(field.ledEffectivePpfd()));
        out.put("effectivePpfd", round(field.effectivePpfd()));
        out.put("humidityPct", round(humidity));
        out.put("temperatureC", round(tempC));
        out.put("dliSoFar", zone.getLastDli());
        out.put("shadeOpenPercent", zone.getShadeOpenPercent());
        out.put("autoControl", zone.getAutoControl());
        out.put("sensorPpfd", field.sensorPpfd());
        out.put("grid", field.grid().stream().map(g -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("x", round(g.x()));
            p.put("y", round(g.y()));
            p.put("ppfd", round(g.ppfd()));
            p.put("sunPpfd", round(g.sunPpfd()));
            p.put("ledPpfd", round(g.ledPpfd()));
            p.put("rPpfd", round(g.rPpfd()));
            p.put("gPpfd", round(g.gPpfd()));
            p.put("bPpfd", round(g.bPpfd()));
            return p;
        }).toList());
        out.put("nx", field.nx());
        out.put("ny", field.ny());
        out.put("shadeTransmittance", round(field.shadeTransmittance() * 1000.0) / 1000.0);
        out.put("coverTransmittance", round(field.coverTransmittance() * 1000.0) / 1000.0);
        out.put("sunModel", field.sunModel());
        out.put("bedStats", field.bedStats().entrySet().stream().map(e -> {
            LightFieldModel.BedLightStat s = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bedId", s.bedId());
            m.put("avgPpfd", round(s.avgPpfd()));
            m.put("minPpfd", round(s.minPpfd()));
            m.put("avgLed", round(s.avgLed()));
            m.put("cellCount", s.cellCount());
            double u0 = s.avgPpfd() > 1e-3 ? s.minPpfd() / s.avgPpfd() : 0;
            m.put("uniformityU0", Math.round(u0 * 1000.0) / 1000.0);
            return m;
        }).toList());
        double lengthM = zone.getLengthM() != null
                ? zone.getLengthM().doubleValue() : GreenhouseGeometry.LENGTH_M;
        double widthM = zone.getWidthM() != null
                ? zone.getWidthM().doubleValue() : GreenhouseGeometry.WIDTH_M;
        out.put("lengthM", lengthM);
        out.put("widthM", widthM);
        out.put("gutterHeightM", GreenhouseGeometry.GUTTER_HEIGHT_M);
        out.put("ridgeHeightM", GreenhouseGeometry.RIDGE_HEIGHT_M);
        out.put("measurePlaneZ", GreenhouseGeometry.measurePlaneZ(zoneId));
        out.put("coordinateNoteZh", "西南角原点 · 长轴东西 · 南向采光");
        double[] sun = GreenhouseGeometry.solarElevationAzimuth(minute, zone.getClimateProfileId());
        out.put("solarElevationDeg", round(sun[0]));
        out.put("solarAzimuthDeg", round(sun[1]));
        out.put("sunVisible", sun[0] > 0.5);
        GhRecipe recipe = recipeMapper.selectOne(new LambdaQueryWrapper<GhRecipe>()
                .eq(GhRecipe::getRecipeId, zone.getRecipeId()));
        if (recipe != null) {
            out.put("recipe", recipe);
        }
        double dliVal = zone.getLastDli() != null ? zone.getLastDli().doubleValue() : 0;
        DynamicLightTarget.Result dyn = null;
        if (recipe != null) {
            dyn = DynamicLightTarget.compute(recipe, minute, tempC, humidity, dliVal);
            out.put("dynamicTarget", dyn.toMap());
            out.put("vpdKpa", dyn.toMap().get("vpdKpa"));
        }
        int avgDimApi = (int) devices.stream()
                .filter(d -> "GROW_LAMP".equals(d.getDeviceType()))
                .mapToInt(l -> l.getDimmingPercent() != null ? l.getDimmingPercent() : 0)
                .average().orElse(0);
        long lampCount = devices.stream().filter(d -> "GROW_LAMP".equals(d.getDeviceType())).count();
        SpectrumShares.Rgb ledShare = SpectrumShares.ledForRecipe(zone.getRecipeId());
        out.put("spectrum", Map.of(
                "sunShare", Map.of("r", SpectrumShares.SUN.r(), "g", SpectrumShares.SUN.g(), "b", SpectrumShares.SUN.b()),
                "ledShare", Map.of("r", ledShare.r(), "g", ledShare.g(), "b", ledShare.b()),
                "noteZh", "补光为作物配方三色比；日光为宽带 PAR 分解"
        ));
        out.put("economics", LightEconomics.summarize(
                zone.getRecipeId(),
                dliVal,
                dyn != null ? dyn.dliTargetMin() : 0,
                avgDimApi,
                (int) lampCount,
                zone.getShadeOpenPercent() != null ? zone.getShadeOpenPercent() : 100,
                field.effectivePpfd(),
                natural.effectivePpfd(),
                field.ledEffectivePpfd(),
                dyn != null ? dyn.instantMin() : 0,
                dyn != null ? dyn.instantMax() : 0,
                minute / 1440.0,
                null
        ));
        out.put("devices", devices);
        out.put("series", daySeries.getOrDefault(zoneId, List.of()));
        return out;
    }

    @Override
    public List<GhRecipe> listRecipes() {
        return recipeMapper.selectList(new LambdaQueryWrapper<GhRecipe>()
                .eq(GhRecipe::getEnabled, true)
                .orderByAsc(GhRecipe::getCropNameZh));
    }

    @Override
    public GhRecipe getRecipe(String recipeId) {
        return recipeMapper.selectOne(new LambdaQueryWrapper<GhRecipe>().eq(GhRecipe::getRecipeId, recipeId));
    }

    @Override
    @Transactional
    public void bindRecipe(String zoneId, String recipeId) {
        requireZone(zoneId);
        if (getRecipe(recipeId) == null) {
            throw new IllegalArgumentException("配方不存在: " + recipeId);
        }
        GhZone patch = new GhZone();
        patch.setRecipeId(recipeId);
        zoneMapper.update(patch, new LambdaQueryWrapper<GhZone>().eq(GhZone::getZoneId, zoneId));
    }

    @Override
    @Transactional
    public void setClimateProfile(String zoneId, String profileId) {
        requireZone(zoneId);
        ClimateProfiles.get(profileId);
        GhZone patch = new GhZone();
        patch.setClimateProfileId(profileId);
        zoneMapper.update(patch, new LambdaQueryWrapper<GhZone>().eq(GhZone::getZoneId, zoneId));
    }

    @Override
    @Transactional
    public void setAutoControl(String zoneId, boolean enabled) {
        requireZone(zoneId);
        GhZone patch = new GhZone();
        patch.setAutoControl(enabled);
        zoneMapper.update(patch, new LambdaQueryWrapper<GhZone>().eq(GhZone::getZoneId, zoneId));
    }

    @Override
    public List<GhDevice> listDevices(String zoneId) {
        LambdaQueryWrapper<GhDevice> q = new LambdaQueryWrapper<GhDevice>().orderByAsc(GhDevice::getDeviceSn);
        if (zoneId != null && !zoneId.isBlank()) {
            q.eq(GhDevice::getZoneId, zoneId);
        }
        return deviceMapper.selectList(q);
    }

    @Override
    public List<GhWorkOrder> listWorkOrders(String status) {
        LambdaQueryWrapper<GhWorkOrder> q = new LambdaQueryWrapper<GhWorkOrder>().orderByDesc(GhWorkOrder::getCreatedAt);
        if (status != null && !status.isBlank()) {
            q.eq(GhWorkOrder::getStatus, status);
        }
        return workOrderMapper.selectList(q.last("LIMIT 100"));
    }

    @Override
    @Transactional
    public void approveWorkOrder(Long id) {
        GhWorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (!"PENDING".equals(wo.getStatus())) {
            throw new IllegalStateException("工单状态不可审批: " + wo.getStatus());
        }
        wo.setStatus("APPROVED");
        wo.setDecidedAt(LocalDateTime.now());
        workOrderMapper.updateById(wo);
        if (wo.getTargetDeviceSn() != null && wo.getSuggestedDimmingPct() != null) {
            setDimming(wo.getTargetDeviceSn(), wo.getSuggestedDimmingPct(), "WORK_ORDER");
        }
        if (wo.getSuggestedShadePct() != null) {
            GhDevice shade = deviceMapper.selectOne(new LambdaQueryWrapper<GhDevice>()
                    .eq(GhDevice::getZoneId, wo.getZoneId())
                    .eq(GhDevice::getDeviceType, "SHADE_ACTUATOR")
                    .last("LIMIT 1"));
            if (shade != null) {
                setShadeOpen(shade.getDeviceSn(), wo.getSuggestedShadePct(), "WORK_ORDER");
            }
        }
    }

    @Override
    @Transactional
    public void rejectWorkOrder(Long id) {
        GhWorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        wo.setStatus("REJECTED");
        wo.setDecidedAt(LocalDateTime.now());
        workOrderMapper.updateById(wo);
    }

    @Override
    @Transactional
    public void completeWorkOrder(Long id) {
        GhWorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        wo.setStatus("COMPLETED");
        wo.setCompletedAt(LocalDateTime.now());
        workOrderMapper.updateById(wo);
    }

    @Override
    @Transactional
    public void setDimming(String deviceSn, int percent, String source) {
        GhDevice device = requireDevice(deviceSn);
        if (!"GROW_LAMP".equals(device.getDeviceType())) {
            throw new IllegalArgumentException("非补光灯设备");
        }
        int p = clamp(percent, 0, 100);
        device.setDimmingPercent(p);
        device.setPowerOn(p > 0);
        device.setLastSeenAt(LocalDateTime.now());
        deviceMapper.updateById(device);

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("commandId", "cmd-" + System.currentTimeMillis());
        cmd.put("deviceSn", deviceSn);
        cmd.put("command", "SET_DIMMING");
        cmd.put("dimmingPercent", p);
        cmd.put("source", source);
        mqttConfig.publishGreenhouseCommand(deviceSn, cmd);
        recordControl(deviceSn, device.getZoneId(), "SET_DIMMING", source, cmd, "SUCCESS");
        pushWs();
    }

    @Override
    @Transactional
    public void setShadeOpen(String deviceSn, int percent, String source) {
        GhDevice device = requireDevice(deviceSn);
        if (!"SHADE_ACTUATOR".equals(device.getDeviceType())) {
            throw new IllegalArgumentException("非遮阳设备");
        }
        int p = clamp(percent, 0, 100);
        // 自动控光：遮阳只走粗档，模拟机械难微调
        if ("AUTO".equals(source)) {
            p = LightEconomics.snapShadeOpen(p);
        }
        device.setShadeOpenPercent(p);
        device.setLastSeenAt(LocalDateTime.now());
        deviceMapper.updateById(device);

        GhZone zonePatch = new GhZone();
        zonePatch.setShadeOpenPercent(p);
        zoneMapper.update(zonePatch, new LambdaQueryWrapper<GhZone>().eq(GhZone::getZoneId, device.getZoneId()));

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("commandId", "cmd-" + System.currentTimeMillis());
        cmd.put("deviceSn", deviceSn);
        cmd.put("command", "SET_OPEN_PERCENT");
        cmd.put("shadeOpenPercent", p);
        cmd.put("source", source);
        mqttConfig.publishGreenhouseCommand(deviceSn, cmd);
        recordControl(deviceSn, device.getZoneId(), "SET_OPEN_PERCENT", source, cmd, "SUCCESS");
        pushWs();
    }

    @Override
    @Transactional
    public void ingestTelemetry(Map<String, Object> payload) {
        String sn = str(payload.get("deviceSn"));
        if (sn == null) {
            return;
        }
        GhDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<GhDevice>().eq(GhDevice::getDeviceSn, sn));
        if (device == null) {
            log.warn("光棚遥测未知设备: {}", sn);
            return;
        }
        BigDecimal ppfd = dec(payload.get("ppfd"));
        BigDecimal lux = dec(payload.get("lux"));
        BigDecimal temp = dec(payload.get("temperatureC"));
        BigDecimal hum = dec(payload.get("humidityPct"));
        device.setLastPpfd(ppfd);
        device.setLastLux(lux);
        device.setLastTempC(temp);
        device.setLastHumidityPct(hum);
        device.setOnlineStatus("ONLINE");
        device.setLastSeenAt(LocalDateTime.now());
        deviceMapper.updateById(device);

        GhTelemetry row = new GhTelemetry()
                .setDeviceSn(sn)
                .setZoneId(device.getZoneId())
                .setPpfd(ppfd)
                .setLux(lux)
                .setTempC(temp)
                .setHumidity(hum)
                .setCreatedAt(LocalDateTime.now());
        telemetryMapper.insert(row);
        pushWs();
    }

    @Override
    @Transactional
    public void ingestStatus(Map<String, Object> payload) {
        String sn = str(payload.get("deviceSn"));
        if (sn == null) {
            return;
        }
        GhDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<GhDevice>().eq(GhDevice::getDeviceSn, sn));
        if (device == null) {
            return;
        }
        if (payload.get("dimmingPercent") != null) {
            device.setDimmingPercent(Integer.parseInt(payload.get("dimmingPercent").toString()));
        }
        if (payload.get("shadeOpenPercent") != null) {
            int p = Integer.parseInt(payload.get("shadeOpenPercent").toString());
            device.setShadeOpenPercent(p);
            GhZone zonePatch = new GhZone();
            zonePatch.setShadeOpenPercent(p);
            zoneMapper.update(zonePatch, new LambdaQueryWrapper<GhZone>().eq(GhZone::getZoneId, device.getZoneId()));
        }
        if (payload.get("powerOn") != null) {
            device.setPowerOn(Boolean.parseBoolean(payload.get("powerOn").toString()));
        }
        if (payload.get("online") != null) {
            device.setOnlineStatus(Boolean.parseBoolean(payload.get("online").toString()) ? "ONLINE" : "OFFLINE");
        }
        device.setLastSeenAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        pushWs();
    }

    @Override
    @Transactional
    public void tickSimulation() {
        double step = minutesPerTick();
        double prev = simMinuteOfDay;
        simMinuteOfDay = (simMinuteOfDay + step) % 1440.0;
        if (simMinuteOfDay < 0) {
            simMinuteOfDay += 1440.0;
        }
        boolean newDay = simMinuteOfDay < prev;

        for (GhZone zone : listZones()) {
            if (newDay) {
                daySeries.put(zone.getZoneId(), new ArrayList<>());
                lastActionSimMinute.remove(zone.getZoneId());
            }
            List<GhDevice> devices = devicesOf(zone.getZoneId());
            double outdoor = ClimateProfiles.outdoorParAt(zone.getClimateProfileId(), simMinuteOfDay);
            LightFieldModel.FieldResult field = LightFieldModel.compute(zone, devices, outdoor, simMinuteOfDay);
            LightFieldModel.FieldResult natural = LightFieldModel.compute(zone, devices, outdoor, simMinuteOfDay, 100, false);

            double humidity = synthHumidity(simMinuteOfDay, zone.getShadeOpenPercent());
            double tempC = synthTemp(simMinuteOfDay, outdoor);

            for (GhDevice sensor : devices) {
                if (!"PAR_SENSOR".equals(sensor.getDeviceType())) {
                    continue;
                }
                Double ppfd = field.sensorPpfd().get(sensor.getDeviceSn());
                if (ppfd == null) {
                    continue;
                }
                sensor.setLastPpfd(BigDecimal.valueOf(round(ppfd)));
                sensor.setLastLux(BigDecimal.valueOf(round(ppfd * 54)));
                sensor.setLastTempC(BigDecimal.valueOf(round(tempC)));
                sensor.setLastHumidityPct(BigDecimal.valueOf(round(humidity)));
                sensor.setOnlineStatus("ONLINE");
                sensor.setLastSeenAt(LocalDateTime.now());
                deviceMapper.updateById(sensor);
            }

            // DLI：本步仿真时长（分钟）→ 秒
            double deltaDli = field.effectivePpfd() * step * 60 * 1e-6;
            BigDecimal dli = newDay ? BigDecimal.ZERO
                    : (zone.getLastDli() != null ? zone.getLastDli() : BigDecimal.ZERO);
            dli = dli.add(BigDecimal.valueOf(deltaDli)).setScale(3, RoundingMode.HALF_UP);

            GhZone zoneUpdate = new GhZone();
            zoneUpdate.setLastEffectivePpfd(BigDecimal.valueOf(round(field.effectivePpfd())));
            zoneUpdate.setLastDli(dli);
            zoneUpdate.setLastRuleAt(LocalDateTime.now());
            zoneMapper.update(zoneUpdate, new LambdaQueryWrapper<GhZone>().eq(GhZone::getZoneId, zone.getZoneId()));
            zone.setLastEffectivePpfd(zoneUpdate.getLastEffectivePpfd());
            zone.setLastDli(dli);

            int avgDim = (int) devices.stream()
                    .filter(d -> "GROW_LAMP".equals(d.getDeviceType()))
                    .mapToInt(l -> l.getDimmingPercent() != null ? l.getDimmingPercent() : 0)
                    .average().orElse(0);

            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("minuteOfDay", round(simMinuteOfDay));
            sample.put("outdoorPpfd", round(outdoor));
            sample.put("naturalPpfd", round(natural.effectivePpfd()));
            sample.put("sunInPpfd", round(field.outdoorInPpfd()));
            sample.put("ledPpfd", round(field.ledEffectivePpfd()));
            sample.put("controlledPpfd", round(field.effectivePpfd()));
            sample.put("humidityPct", round(humidity));
            sample.put("temperatureC", round(tempC));
            sample.put("shadeOpenPercent", zone.getShadeOpenPercent());
            sample.put("avgDimmingPercent", avgDim);
            GhRecipe recipeSample = getRecipe(zone.getRecipeId());
            if (recipeSample != null) {
                DynamicLightTarget.Result dyn = DynamicLightTarget.compute(
                        recipeSample, simMinuteOfDay, tempC, humidity, dli.doubleValue());
                sample.put("targetPpfdMin", round(dyn.instantMin()));
                sample.put("targetPpfdMax", round(dyn.instantMax()));
                sample.put("targetMid", round((dyn.instantMin() + dyn.instantMax()) / 2.0));
                sample.put("gapPpfd", round(field.effectivePpfd() - (dyn.instantMin() + dyn.instantMax()) / 2.0));
                sample.put("vpdKpa", round(dyn.vpdKpa() * 1000.0) / 1000.0);
                sample.put("dliSoFar", round(dli.doubleValue() * 1000.0) / 1000.0);
            }
            Map<String, Double> bedPpfd = new LinkedHashMap<>();
            for (var e : field.bedStats().entrySet()) {
                bedPpfd.put(e.getKey(), round(e.getValue().avgPpfd()));
            }
            sample.put("bedPpfd", bedPpfd);
            Map<String, Double> sensorSeries = new LinkedHashMap<>();
            for (var e : field.sensorPpfd().entrySet()) {
                sensorSeries.put(e.getKey(), round(e.getValue()));
            }
            sample.put("sensorPpfd", sensorSeries);
            daySeries.computeIfAbsent(zone.getZoneId(), k -> new ArrayList<>()).add(sample);
            List<Map<String, Object>> series = daySeries.get(zone.getZoneId());
            if (series.size() > SERIES_CAP) {
                series.subList(0, series.size() - SERIES_CAP).clear();
            }

            if (Boolean.TRUE.equals(zone.getAutoControl())) {
                applyRules(zone, devices, field, tempC, humidity);
            }
        }
        pushWs();
    }

    @Override
    public void resetSimDay() {
        // 从上午 9:00 起跑，避免长时间「夜里只见灯峰」
        simMinuteOfDay = 540;
        daySeries.clear();
        lastActionSimMinute.clear();
        for (GhZone zone : listZones()) {
            GhZone patch = new GhZone();
            patch.setLastDli(BigDecimal.ZERO);
            // 演示重置：遮阳全开 + 自动控光开，便于追动态目标带
            patch.setShadeOpenPercent(100);
            patch.setAutoControl(true);
            zoneMapper.update(patch, new LambdaQueryWrapper<GhZone>().eq(GhZone::getZoneId, zone.getZoneId()));

            List<GhDevice> devices = devicesOf(zone.getZoneId());
            for (GhDevice d : devices) {
                if ("SHADE_ACTUATOR".equals(d.getDeviceType())) {
                    d.setShadeOpenPercent(100);
                    deviceMapper.updateById(d);
                } else if ("GROW_LAMP".equals(d.getDeviceType())) {
                    // 中等补光，便于与日光对比（过强会淹没自然光）
                    d.setDimmingPercent(20);
                    d.setPowerOn(true);
                    deviceMapper.updateById(d);
                }
            }
        }
        pushWs();
    }

    @Override
    public Map<String, Object> getSimClock() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("minuteOfDay", round(simMinuteOfDay));
        out.put("dayProgress", simMinuteOfDay / 1440.0);
        out.put("dayCompressSec", dayCompressSec);
        out.put("intervalMs", intervalMs);
        out.put("minutesPerTick", round(minutesPerTick() * 100.0) / 100.0);
        return out;
    }

    @Override
    public LightFieldModel.FieldResult previewField(String zoneId) {
        GhZone zone = requireZone(zoneId);
        double minute = currentMinuteOfDay();
        return LightFieldModel.compute(zone, devicesOf(zoneId),
                ClimateProfiles.outdoorParAt(zone.getClimateProfileId(), minute), minute);
    }

    @Override
    public List<GhControlLog> recentControlLogs(int limit) {
        return controlLogMapper.selectList(new LambdaQueryWrapper<GhControlLog>()
                .orderByDesc(GhControlLog::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    @Override
    public Map<String, Object> climateProfiles() {
        Map<String, Object> out = new LinkedHashMap<>();
        ClimateProfiles.all().forEach((id, p) -> out.put(id, Map.of(
                "id", p.id(),
                "labelZh", p.labelZh(),
                "samples", p.samples()
        )));
        return out;
    }

    // -------------------- rules --------------------

    private void applyRules(GhZone zone, List<GhDevice> devices, LightFieldModel.FieldResult field,
                            double tempC, double humidity) {
        GhRecipe recipe = getRecipe(zone.getRecipeId());
        if (recipe == null) {
            return;
        }
        int cooldownMin = 10;
        Double last = lastActionSimMinute.get(zone.getZoneId());
        if (last != null) {
            double delta = simMinuteOfDay - last;
            if (delta < 0) {
                delta += 1440.0;
            }
            if (delta > 0 && delta < cooldownMin) {
                return;
            }
        }

        double effectivePpfd = field.effectivePpfd();
        double dliVal = zone.getLastDli() != null ? zone.getLastDli().doubleValue() : 0;
        DynamicLightTarget.Result dyn = DynamicLightTarget.compute(
                recipe, simMinuteOfDay, tempC, humidity, dliVal);
        double hardMin = dyn.hardMin();
        double hardMax = dyn.hardMax();
        double tMin = dyn.instantMin();
        double tMax = dyn.instantMax();
        double mid = (tMin + tMax) / 2.0;
        int dimStep = recipe.getDimmingStepPct() != null ? recipe.getDimmingStepPct() : 5;

        List<GhDevice> lamps = devices.stream().filter(d -> "GROW_LAMP".equals(d.getDeviceType())).toList();
        GhDevice shade = devices.stream().filter(d -> "SHADE_ACTUATOR".equals(d.getDeviceType())).findFirst().orElse(null);
        int currentShade = zone.getShadeOpenPercent() != null ? zone.getShadeOpenPercent() : 100;
        int avgDim = (int) lamps.stream().mapToInt(l -> l.getDimmingPercent() != null ? l.getDimmingPercent() : 0).average().orElse(0);

        Runnable mark = () -> lastActionSimMinute.put(zone.getZoneId(), simMinuteOfDay);

        if (dyn.photoperiodMask() < 0.05) {
            if (!lamps.isEmpty() && avgDim > 0 && Boolean.TRUE.equals(recipe.getAutoSupplement())) {
                int next = Math.max(0, avgDim - Math.max(dimStep, 15));
                applyDimToAll(lamps, next, "AUTO", zone.getZoneId(), "光周期外降灯→" + next + "%");
                mark.run();
            }
            return;
        }

        double outdoor = ClimateProfiles.outdoorParAt(zone.getClimateProfileId(), simMinuteOfDay);
        double naturalOpen = LightFieldModel.naturalScaleForShadeOpen(zone, outdoor, simMinuteOfDay, 100);
        int shadeClosedStep = LightEconomics.stepShadeClosed(currentShade);
        double naturalIfClosed = LightFieldModel.naturalScaleForShadeOpen(
                zone, outdoor, simMinuteOfDay, shadeClosedStep);

        // 区级过硬限：先分床降灯，再考虑遮阳
        if (effectivePpfd > hardMax || (effectivePpfd > tMax + 4 && tMax > 1)) {
            if (avgDim > 0 && !lamps.isEmpty() && Boolean.TRUE.equals(recipe.getAutoSupplement())) {
                boolean acted = adjustLampsPerBed(lamps, field, mid, tMin, tMax, dimStep, true,
                        zone.getZoneId());
                if (acted) {
                    mark.run();
                    return;
                }
            }
            if (Boolean.TRUE.equals(recipe.getAutoShade()) && shade != null && currentShade > 10) {
                boolean ok = LightEconomics.shouldCloseShade(
                        effectivePpfd, naturalOpen, naturalIfClosed, mid, hardMax, avgDim);
                if (ok && shadeClosedStep < currentShade) {
                    setShadeOpen(shade.getDeviceSn(), shadeClosedStep, "AUTO");
                    mark.run();
                }
            }
            return;
        }

        // 欠光：先开遮阳，再分床补光
        if (effectivePpfd < hardMin || (effectivePpfd < tMin - 2 && tMin > 1)) {
            if (Boolean.TRUE.equals(recipe.getAutoShade()) && shade != null && currentShade < 100) {
                int opened = LightEconomics.stepShadeOpened(currentShade);
                if (opened > currentShade) {
                    setShadeOpen(shade.getDeviceSn(), opened, "AUTO");
                    mark.run();
                    return;
                }
            }
            if (Boolean.TRUE.equals(recipe.getAutoSupplement()) && !lamps.isEmpty()) {
                boolean acted = adjustLampsPerBed(lamps, field, mid, tMin, tMax, dimStep, false,
                        zone.getZoneId());
                if (acted) {
                    mark.run();
                }
            }
            return;
        }

        // 目标带内：分床微调；遮阳仅在灯已低时谨慎关
        if (Boolean.TRUE.equals(recipe.getAutoSupplement()) && !lamps.isEmpty()) {
            boolean acted = adjustLampsPerBed(lamps, field, mid, tMin, tMax, dimStep, false,
                    zone.getZoneId());
            if (acted) {
                mark.run();
                return;
            }
        }
        if (effectivePpfd > tMax && avgDim <= 8 && Boolean.TRUE.equals(recipe.getAutoShade())
                && shade != null) {
            boolean ok = LightEconomics.shouldCloseShade(
                    effectivePpfd, naturalOpen, naturalIfClosed, mid, hardMax, avgDim);
            if (ok && shadeClosedStep < currentShade) {
                setShadeOpen(shade.getDeviceSn(), shadeClosedStep, "AUTO");
                mark.run();
            }
        }
    }

    /**
     * 按床调节：只改该床所属灯，使床面 AVG 靠近目标中值。
     *
     * @param forceDim true=过光强制降
     */
    private boolean adjustLampsPerBed(List<GhDevice> lamps, LightFieldModel.FieldResult field,
                                      double mid, double tMin, double tMax, int dimStep,
                                      boolean forceDim, String zoneId) {
        Map<String, LightFieldModel.BedLightStat> beds = field.bedStats();
        if (beds == null || beds.isEmpty()) {
            return false;
        }
        boolean any = false;
        for (Map.Entry<String, LightFieldModel.BedLightStat> e : beds.entrySet()) {
            String bedId = e.getKey();
            LightFieldModel.BedLightStat st = e.getValue();
            List<GhDevice> bedLamps = lamps.stream()
                    .filter(l -> bedId.equals(GreenhouseGeometry.lampBedId(l.getDeviceSn())))
                    .toList();
            if (bedLamps.isEmpty()) {
                continue;
            }
            int cur = (int) bedLamps.stream()
                    .mapToInt(l -> l.getDimmingPercent() != null ? l.getDimmingPercent() : 0)
                    .average().orElse(0);
            double bedPpfd = st.avgPpfd();
            int next = cur;
            if (forceDim || bedPpfd > tMax + 2) {
                int drop = Math.max(dimStep, (int) Math.min(25,
                        Math.ceil((bedPpfd - mid) / Math.max(mid, 20) * 35)));
                next = Math.max(0, cur - drop);
            } else if (bedPpfd < tMin - 1) {
                int boost = Math.max(dimStep, (int) Math.min(22,
                        Math.ceil((mid - bedPpfd) / Math.max(mid, 20) * 40)));
                next = Math.min(100, cur + boost);
            } else if (bedPpfd < tMin) {
                next = Math.min(100, cur + dimStep);
            } else if (bedPpfd > tMax) {
                next = Math.max(0, cur - dimStep);
            }
            if (next != cur) {
                applyDimToAll(bedLamps, next, "AUTO", zoneId,
                        "分床 " + bedId + " 调光→" + next + "% (床均=" + round(bedPpfd)
                                + ", 目标≈" + round(mid) + ")");
                any = true;
            }
        }
        // L1 叠层灯：弱跟随，避免长期满功率
        List<GhDevice> l1 = lamps.stream()
                .filter(l -> l.getDeviceSn() != null && l.getDeviceSn().contains("L1"))
                .toList();
        if (!l1.isEmpty()) {
            int cur = (int) l1.stream().mapToInt(l -> l.getDimmingPercent() != null ? l.getDimmingPercent() : 0)
                    .average().orElse(0);
            int next = cur;
            if (forceDim && cur > 0) {
                next = Math.max(0, cur - dimStep);
            } else if (!forceDim && field.effectivePpfd() < tMin && cur < 40) {
                next = Math.min(40, cur + dimStep);
            }
            if (next != cur) {
                applyDimToAll(l1, next, "AUTO", zoneId, "上层灯微调→" + next + "%");
                any = true;
            }
        }
        return any;
    }

    private void applyDimToAll(List<GhDevice> lamps, int next, String source,
                               String zoneId, String reason) {
        if (!"AUTO".equals(source) && next >= 80) {
            GhDevice first = lamps.get(0);
            createWorkOrder(zoneId, first.getDeviceSn(), reason, next, null);
            return;
        }
        for (GhDevice lamp : lamps) {
            setDimming(lamp.getDeviceSn(), next, source);
        }
    }

    private void createWorkOrder(String zoneId, String deviceSn, String reason, Integer dim, Integer shade) {
        Long pending = workOrderMapper.selectCount(new LambdaQueryWrapper<GhWorkOrder>()
                .eq(GhWorkOrder::getZoneId, zoneId)
                .eq(GhWorkOrder::getStatus, "PENDING"));
        if (pending != null && pending > 0) {
            return;
        }
        GhWorkOrder wo = new GhWorkOrder()
                .setZoneId(zoneId)
                .setStatus("PENDING")
                .setReason(reason)
                .setSuggestedDimmingPct(dim)
                .setSuggestedShadePct(shade)
                .setTargetDeviceSn(deviceSn)
                .setCreatedAt(LocalDateTime.now());
        workOrderMapper.insert(wo);
        log.info("光棚工单创建: zone={} reason={}", zoneId, reason);
    }

    // -------------------- helpers --------------------

    private double currentMinuteOfDay() {
        double m = simMinuteOfDay % 1440.0;
        return m < 0 ? m + 1440.0 : m;
    }

    /** 湿度：夜间高、正午低；遮阳闭合略抬升 */
    private static double synthHumidity(double minute, Integer shadeOpen) {
        double day = Math.sin(Math.PI * ((minute - 360) / 720.0));
        day = Math.max(0, Math.min(1, day));
        double open = shadeOpen != null ? shadeOpen : 100;
        double base = 85 - day * 30 + (100 - open) * 0.08;
        return Math.max(40, Math.min(95, base));
    }

    private static double synthTemp(double minute, double outdoorPar) {
        double day = Math.sin(Math.PI * ((minute - 360) / 720.0));
        day = Math.max(0, Math.min(1, day));
        return 18 + day * 12 + Math.min(6, outdoorPar / 200.0);
    }

    private GhZone requireZone(String zoneId) {
        GhZone z = zoneMapper.selectOne(new LambdaQueryWrapper<GhZone>().eq(GhZone::getZoneId, zoneId));
        if (z == null) {
            throw new IllegalArgumentException("分区不存在: " + zoneId);
        }
        return z;
    }

    private GhDevice requireDevice(String sn) {
        GhDevice d = deviceMapper.selectOne(new LambdaQueryWrapper<GhDevice>().eq(GhDevice::getDeviceSn, sn));
        if (d == null) {
            throw new IllegalArgumentException("设备不存在: " + sn);
        }
        return d;
    }

    private List<GhDevice> devicesOf(String zoneId) {
        return deviceMapper.selectList(new LambdaQueryWrapper<GhDevice>().eq(GhDevice::getZoneId, zoneId));
    }

    private void recordControl(String sn, String zoneId, String command, String source,
                               Map<String, Object> payload, String status) {
        try {
            GhControlLog logRow = new GhControlLog()
                    .setDeviceSn(sn)
                    .setZoneId(zoneId)
                    .setCommand(command)
                    .setSource(source)
                    .setPayloadJson(OM.writeValueAsString(payload))
                    .setExecutionStatus(status)
                    .setCreatedAt(LocalDateTime.now());
            controlLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("写控制日志失败", e);
        }
    }

    private void pushWs() {
        try {
            WebSocketMessage msg = WebSocketMessage.builder()
                    .type("GREENHOUSE_UPDATE")
                    .timestamp(LocalDateTime.now())
                    .data(Map.of("tick", System.currentTimeMillis()))
                    .build();
            messagingTemplate.convertAndSend("/topic/greenhouse", msg);
        } catch (Exception e) {
            log.debug("greenhouse ws push skipped: {}", e.getMessage());
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal dec(Object o) {
        if (o == null) {
            return null;
        }
        return new BigDecimal(o.toString());
    }
}
