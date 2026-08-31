package com.cqu.greenhouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqu.config.MqttConfig;
import com.cqu.greenhouse.entity.*;
import com.cqu.greenhouse.mapper.*;
import com.cqu.greenhouse.service.IGreenhouseService;
import com.cqu.greenhouse.sim.AutoLightRegulator;
import com.cqu.greenhouse.sim.ClimateProfiles;
import com.cqu.greenhouse.sim.DynamicLightTarget;
import com.cqu.greenhouse.sim.GreenhouseGeometry;
import com.cqu.greenhouse.sim.LightEconomics;
import com.cqu.greenhouse.sim.LightFieldModel;
import com.cqu.greenhouse.sim.SpectrumShares;
import com.cqu.security.ForbiddenException;
import com.cqu.security.RoleCodes;
import com.cqu.vo.WebSocketMessage;
import com.cqu.utils.UserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private GhAlarmMapper alarmMapper;
    @Autowired
    private GhReportMapper reportMapper;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Lazy
    @Autowired
    private MqttConfig mqttConfig;

    @org.springframework.beans.factory.annotation.Value("${greenhouse.sim.day-compress-sec:120}")
    private int dayCompressSec;

    @org.springframework.beans.factory.annotation.Value("${greenhouse.sim.interval-ms:1000}")
    private int intervalMs;

    /** 进程内仿真是否外发 PAR 遥测（默认关，避免 MQTT 回环写爆） */
    @org.springframework.beans.factory.annotation.Value("${greenhouse.sim.publish-mqtt-telemetry:false}")
    private boolean publishMqttTelemetry;

    /** 指令无 status 回执超时（秒）→ TIMEOUT + COMMAND_TIMEOUT 告警 */
    @org.springframework.beans.factory.annotation.Value("${greenhouse.control.command-timeout-sec:30}")
    private int commandTimeoutSec;

    /** zoneId → 上次 AUTO 补光动作（仿真分钟） */
    private final Map<String, Double> lastLampActionSimMinute = new ConcurrentHashMap<>();
    /** zoneId → 上次 AUTO 遮阳动作（仿真分钟） */
    private final Map<String, Double> lastShadeActionSimMinute = new ConcurrentHashMap<>();
    /**
     * zoneId → 目标带缩放 EMA，抑制 VPD/DLI 追赶造成的紫色带锯齿。
     */
    private final Map<String, Double> targetScaleEma = new ConcurrentHashMap<>();
    /**
     * zoneId → 温湿平滑状态 [humidity, tempC, shadeLag]。
     * 遮阳阶跃不直接打进湿度，经滞后后再低通，避免 VPD→目标带锯齿。
     */
    private final Map<String, double[]> climateSmooth = new ConcurrentHashMap<>();
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

        double[] climate = climateSnapshot(zoneId, minute, zone.getShadeOpenPercent(), outdoor);
        double humidity = climate[0];
        double tempC = climate[1];

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
            Map<String, Object> dynMap = dyn.toMap();
            double[] band = peekTargetBand(zoneId, dyn);
            dynMap.put("instantMin", round(band[0]));
            dynMap.put("instantMax", round(band[1]));
            out.put("dynamicTarget", dynMap);
            out.put("vpdKpa", dynMap.get("vpdKpa"));
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
        // R1: 批准 ≠ 下发；种植员 claim 后才执行
        wo.setStatus("APPROVED");
        wo.setDecidedAt(LocalDateTime.now());
        workOrderMapper.updateById(wo);
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
    public void claimWorkOrder(Long id) {
        GhWorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (!"APPROVED".equals(wo.getStatus())) {
            throw new IllegalStateException("仅已批准工单可接单执行: " + wo.getStatus());
        }
        wo.setStatus("IN_PROGRESS");
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

        wo.setStatus("COMPLETED");
        wo.setCompletedAt(LocalDateTime.now());
        workOrderMapper.updateById(wo);
    }

    @Override
    @Transactional
    public void completeWorkOrder(Long id) {
        GhWorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (!"IN_PROGRESS".equals(wo.getStatus()) && !"APPROVED".equals(wo.getStatus())) {
            throw new IllegalStateException("工单状态不可完成: " + wo.getStatus());
        }
        // 仅收尾；若仍为 APPROVED 且未 claim，不偷偷下发（应走 claim）
        if ("APPROVED".equals(wo.getStatus())) {
            throw new IllegalStateException("请先接单执行后再完成，或使用接单执行一键完成");
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
        boolean published = mqttConfig.publishGreenhouseCommand(deviceSn, cmd);
        // 已写库；MQTT 已发则 PENDING 等 status，未连 broker 则本地 SUCCESS
        recordControl(deviceSn, device.getZoneId(), "SET_DIMMING", source, cmd, published ? "PENDING" : "SUCCESS");
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
        boolean published = mqttConfig.publishGreenhouseCommand(deviceSn, cmd);
        recordControl(deviceSn, device.getZoneId(), "SET_OPEN_PERCENT", source, cmd, published ? "PENDING" : "SUCCESS");
        pushWs();
    }

    @Override
    @Transactional
    public void ingestTelemetry(Map<String, Object> payload) {
        String sn = str(payload.get("deviceSn"));
        if (sn == null) {
            return;
        }
        // 仿真自发布遥测：避免 tick→MQTT→ingest 回灌写爆 gh_telemetry
        if ("SIM".equalsIgnoreCase(str(payload.get("source")))) {
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
        ackPendingControl(sn, device);
        pushWs();
    }

    @Override
    @Transactional
    public void tickSimulation() {
        expireTimedOutCommands();
        resolveSimPendingAcks();
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
                lastLampActionSimMinute.remove(zone.getZoneId());
                lastShadeActionSimMinute.remove(zone.getZoneId());
                climateSmooth.remove(zone.getZoneId());
                targetScaleEma.remove(zone.getZoneId());
            }
            List<GhDevice> devices = devicesOf(zone.getZoneId());
            double outdoor = ClimateProfiles.outdoorParAt(zone.getClimateProfileId(), simMinuteOfDay);
            LightFieldModel.FieldResult field = LightFieldModel.compute(zone, devices, outdoor, simMinuteOfDay);
            LightFieldModel.FieldResult natural = LightFieldModel.compute(zone, devices, outdoor, simMinuteOfDay, 100, false);

            double[] climate = advanceClimate(zone.getZoneId(), simMinuteOfDay, zone.getShadeOpenPercent(), outdoor);
            double humidity = climate[0];
            double tempC = climate[1];

            GhRecipe recipeForBand = getRecipe(zone.getRecipeId());
            if (recipeForBand != null) {
                double dliForBand = zone.getLastDli() != null ? zone.getLastDli().doubleValue() : 0;
                DynamicLightTarget.Result dynBand = DynamicLightTarget.compute(
                        recipeForBand, simMinuteOfDay, tempC, humidity, dliForBand);
                advanceTargetBand(zone.getZoneId(), dynBand);
            }

            // 先控后采：AUTO 写入执行器后再算光场，曲线才是「调控后」
            if (Boolean.TRUE.equals(zone.getAutoControl())) {
                applyRules(zone, devices, field, tempC, humidity);
                zone = requireZone(zone.getZoneId());
                devices = devicesOf(zone.getZoneId());
                field = LightFieldModel.compute(zone, devices, outdoor, simMinuteOfDay);
                natural = LightFieldModel.compute(zone, devices, outdoor, simMinuteOfDay, 100, false);
            }

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

            // M6：欠/过光告警（按配方硬限）；同类型 ACTIVE 去重
            evaluateLightAlarms(zone, field, recipeForBand);

            // 仿真遥测回灌 MQTT，便于真机/模拟器路径联调
            publishSimTelemetry(devices, field, tempC, humidity);

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
                double[] band = peekTargetBand(zone.getZoneId(), dyn);
                sample.put("targetPpfdMin", round(band[0]));
                sample.put("targetPpfdMax", round(band[1]));
                sample.put("targetMid", round((band[0] + band[1]) / 2.0));
                sample.put("gapPpfd", round(field.effectivePpfd() - (band[0] + band[1]) / 2.0));
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
        }
        pushWs();
    }

    @Override
    public void resetSimDay() {
        // 从上午 9:00 起跑，避免长时间「夜里只见灯峰」
        simMinuteOfDay = 540;
        daySeries.clear();
        lastLampActionSimMinute.clear();
        lastShadeActionSimMinute.clear();
        climateSmooth.clear();
        targetScaleEma.clear();
        for (GhZone zone : listZones()) {
            GhZone patch = new GhZone();
            patch.setLastDli(BigDecimal.ZERO);
            // 演示重置：遮阳全开 + 自动控光开 + 东西同气候，消除固定东西差
            patch.setShadeOpenPercent(100);
            patch.setAutoControl(true);
            patch.setClimateProfileId("cq-winter-clear");
            patch.setRecipeId("dendrobium-officinale-cultivation-v1");
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
    public List<GhControlLog> recentControlLogs(int limit, String source) {
        LambdaQueryWrapper<GhControlLog> q = new LambdaQueryWrapper<GhControlLog>()
                .orderByDesc(GhControlLog::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (source != null && !source.isBlank()) {
            q.eq(GhControlLog::getSource, source);
        }
        return controlLogMapper.selectList(q);
    }

    @Override
    public List<GhAlarm> listAlarms(String status, int limit) {
        LambdaQueryWrapper<GhAlarm> q = new LambdaQueryWrapper<GhAlarm>()
                .orderByDesc(GhAlarm::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (status != null && !status.isBlank()) {
            q.eq(GhAlarm::getStatus, status);
        }
        return alarmMapper.selectList(q);
    }

    @Override
    @Transactional
    public void resolveAlarm(Long id) {
        GhAlarm a = alarmMapper.selectById(id);
        if (a == null) {
            throw new IllegalArgumentException("告警不存在");
        }
        a.setStatus("RESOLVED");
        a.setResolvedAt(LocalDateTime.now());
        alarmMapper.updateById(a);
        pushAlarmWs(a);
    }

    @Override
    @Transactional
    public void ingestAlarm(Map<String, Object> payload) {
        String sn = str(payload.get("deviceSn"));
        String type = str(payload.get("alarmType"));
        String message = str(payload.get("message"));
        if (type == null || type.isBlank()) {
            return;
        }
        String zoneId = null;
        if (sn != null) {
            GhDevice d = deviceMapper.selectOne(new LambdaQueryWrapper<GhDevice>().eq(GhDevice::getDeviceSn, sn));
            if (d != null) {
                zoneId = d.getZoneId();
                if ("DEVICE_OFFLINE".equalsIgnoreCase(type) || "OFFLINE".equalsIgnoreCase(type)) {
                    d.setOnlineStatus("OFFLINE");
                    deviceMapper.updateById(d);
                }
            }
        }
        if (message == null || message.isBlank()) {
            message = type;
        }
        raiseAlarm(zoneId, sn, type.toUpperCase(), message);
    }

    @Override
    public List<GhReport> listReports(String type, String status, int limit) {
        LambdaQueryWrapper<GhReport> q = new LambdaQueryWrapper<GhReport>()
                .orderByDesc(GhReport::getReportDate)
                .orderByDesc(GhReport::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        if (type != null && !type.isBlank()) {
            q.eq(GhReport::getReportType, type);
        }
        if (status != null && !status.isBlank()) {
            q.eq(GhReport::getStatus, status);
        }
        return reportMapper.selectList(q);
    }

    @Override
    public GhReport getReport(Long id) {
        return reportMapper.selectById(id);
    }

    @Override
    @Transactional
    public GhReport draftDailyLight(String zoneId) {
        if (RoleCodes.TRAINEE.equals(RoleCodes.normalize(UserHolder.getRole()))) {
            throw new ForbiddenException("学员请使用实训报告草稿，不可生成日光合运营草稿");
        }
        return upsertLightDraft(zoneId, "DAILY_LIGHT", "日光合摘要");
    }

    @Override
    @Transactional
    public GhReport draftTraining(String zoneId) {
        return upsertLightDraft(zoneId, "TRAINING", "实训观察报告");
    }

    private GhReport upsertLightDraft(String zoneId, String reportType, String titlePrefix) {
        String zid = (zoneId == null || zoneId.isBlank()) ? "ZONE-A" : zoneId;
        GhZone zone = requireZone(zid);
        LocalDate today = LocalDate.now();
        GhReport existing = reportMapper.selectOne(new LambdaQueryWrapper<GhReport>()
                .eq(GhReport::getReportType, reportType)
                .eq(GhReport::getReportDate, today)
                .eq(GhReport::getZoneId, zid)
                .eq(GhReport::getStatus, "DRAFT")
                .eq(GhReport::getAuthorId, UserHolder.getCurrent())
                .last("LIMIT 1"));

        List<GhWorkOrder> wos = workOrderMapper.selectList(new LambdaQueryWrapper<GhWorkOrder>()
                .eq(GhWorkOrder::getZoneId, zid)
                .ge(GhWorkOrder::getCreatedAt, today.atStartOfDay())
                .orderByDesc(GhWorkOrder::getId)
                .last("LIMIT 20"));
        long pending = wos.stream().filter(w -> "PENDING".equals(w.getStatus())).count();
        long completed = wos.stream().filter(w -> "COMPLETED".equals(w.getStatus())).count();

        double dli = zone.getLastDli() != null ? zone.getLastDli().doubleValue() : 0;
        double ppfd = zone.getLastEffectivePpfd() != null ? zone.getLastEffectivePpfd().doubleValue() : 0;
        GhRecipe recipe = getRecipe(zone.getRecipeId());
        Double dliMin = recipe != null && recipe.getDliTargetMin() != null
                ? recipe.getDliTargetMin().doubleValue() : null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("zoneId", zid);
        body.put("zoneName", zone.getName());
        body.put("recipeId", zone.getRecipeId());
        body.put("climateProfileId", zone.getClimateProfileId());
        body.put("autoControl", zone.getAutoControl());
        body.put("shadeOpenPercent", zone.getShadeOpenPercent());
        body.put("effectivePpfd", round(ppfd));
        body.put("dliSoFar", round(dli * 1000.0) / 1000.0);
        body.put("dliTargetMin", dliMin);
        body.put("workOrderPending", pending);
        body.put("workOrderCompleted", completed);
        body.put("workOrders", wos.stream().map(w -> Map.of(
                "id", w.getId(),
                "status", w.getStatus() != null ? w.getStatus() : "",
                "reason", w.getReason() != null ? w.getReason() : ""
        )).toList());

        // 简易产量/能耗估：沿用 economics 思路的轻量摘要
        try {
            Map<String, Object> el = getZoneEffectiveLight(zid);
            @SuppressWarnings("unchecked")
            Map<String, Object> econ = el.get("economics") instanceof Map
                    ? (Map<String, Object>) el.get("economics") : null;
            if (econ != null) {
                body.put("economics", econ);
            }
        } catch (Exception ignored) {
            // 草稿仍可保存核心光指标
        }

        if ("TRAINING".equals(reportType)) {
            body.put("observerNote", "请结合热力与日曲线描述光态观察结论，勿改动生产执行器。");
        }

        String summary = String.format(
                "%s · 有效光 %.0f · DLI %.2f%s · 工单待审 %d / 完成 %d",
                zone.getName() != null ? zone.getName() : zid,
                ppfd,
                dli,
                dliMin != null ? ("/" + dliMin) : "",
                pending,
                completed);
        if ("TRAINING".equals(reportType)) {
            summary = "实训 · " + summary;
        }

        String woIds = wos.stream().map(w -> String.valueOf(w.getId())).reduce((a, b) -> a + "," + b).orElse("");
        String bodyJson;
        try {
            bodyJson = OM.writeValueAsString(body);
        } catch (Exception e) {
            bodyJson = "{}";
        }

        LocalDateTime now = LocalDateTime.now();
        String title = titlePrefix + " · " + zid + " · " + today;
        if (existing != null) {
            existing.setTitle(title)
                    .setSummaryZh(summary)
                    .setBodyJson(bodyJson)
                    .setWorkOrderIds(woIds)
                    .setAuthorId(UserHolder.getCurrent())
                    .setAuthorRole(UserHolder.getRole())
                    .setUpdatedAt(now);
            reportMapper.updateById(existing);
            return existing;
        }
        GhReport row = new GhReport()
                .setReportType(reportType)
                .setTitle(title)
                .setStatus("DRAFT")
                .setAuthorId(UserHolder.getCurrent())
                .setAuthorRole(UserHolder.getRole())
                .setZoneId(zid)
                .setReportDate(today)
                .setSummaryZh(summary)
                .setBodyJson(bodyJson)
                .setWorkOrderIds(woIds)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        reportMapper.insert(row);
        return row;
    }

    @Override
    @Transactional
    public void submitReport(Long id) {
        GhReport r = reportMapper.selectById(id);
        if (r == null) {
            throw new IllegalArgumentException("报告不存在");
        }
        if (!"DRAFT".equals(r.getStatus())) {
            throw new IllegalStateException("仅草稿可提交");
        }
        r.setStatus("SUBMITTED");
        r.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(r);
    }

    @Override
    @Transactional
    public void reviewReport(Long id, String note, boolean approve) {
        GhReport r = reportMapper.selectById(id);
        if (r == null) {
            throw new IllegalArgumentException("报告不存在");
        }
        if (!"SUBMITTED".equals(r.getStatus()) && !"DRAFT".equals(r.getStatus())) {
            throw new IllegalStateException("当前状态不可批阅: " + r.getStatus());
        }
        r.setStatus(approve ? "REVIEWED" : "DRAFT");
        r.setReviewerId(UserHolder.getCurrent());
        r.setReviewNote(note);
        r.setReviewedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(r);
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

        double dliVal = zone.getLastDli() != null ? zone.getLastDli().doubleValue() : 0;
        DynamicLightTarget.Result dyn = DynamicLightTarget.compute(
                recipe, simMinuteOfDay, tempC, humidity, dliVal);
        // 用平滑后的瞬时目标带做控制，避免锯齿目标驱使执行器横跳
        double[] band = peekTargetBand(zone.getZoneId(), dyn);
        DynamicLightTarget.Result dynSmooth = new DynamicLightTarget.Result(
                dyn.recipeMin(), dyn.recipeMax(), dyn.recipeHardMin(), dyn.recipeHardMax(),
                band[0], band[1],
                dyn.hardMin(), dyn.hardMax(),
                dyn.photoperiodMask(), dyn.vpdKpa(), dyn.vpdFactor(), dyn.dliCatchUp(),
                dyn.dliSoFar(), dyn.dliTargetMin(), dyn.dliTargetMax(), dyn.dliExpectedByNow(),
                dyn.photoperiodHours(), dyn.noteZh());
        double outdoor = ClimateProfiles.outdoorParAt(zone.getClimateProfileId(), simMinuteOfDay);

        String zoneId = zone.getZoneId();
        AutoLightRegulator.Plan plan = AutoLightRegulator.plan(
                zone,
                devices,
                field,
                dynSmooth,
                recipe,
                outdoor,
                simMinuteOfDay,
                lastLampActionSimMinute.get(zoneId),
                lastShadeActionSimMinute.get(zoneId));

        GhDevice shade = devices.stream()
                .filter(d -> "SHADE_ACTUATOR".equals(d.getDeviceType()))
                .findFirst()
                .orElse(null);

        int dimThr = recipe.getApproveDimAbove() != null ? recipe.getApproveDimAbove() : 80;
        int shadeThr = recipe.getApproveShadeAbove() != null ? recipe.getApproveShadeAbove() : 80;

        if (plan.hasShadeChange() && shade != null) {
            int next = plan.shadeOpenNext();
            int cur = zone.getShadeOpenPercent() != null ? zone.getShadeOpenPercent() : 100;
            if (next != cur) {
                int closedPct = 100 - next;
                // P0.2：关遮阳且闭光比例 ≥ 审批阈值 → 工单，不直发
                setShadeOpen(shade.getDeviceSn(), next, "AUTO");
                lastShadeActionSimMinute.put(zoneId, simMinuteOfDay);
                if (next < cur && closedPct >= shadeThr) {
                    createWorkOrder(zoneId, shade.getDeviceSn(),
                            "AUTO 已关遮阳至开度 " + next + "%（闭光≥" + shadeThr + "%，待复核）",
                            null, next);
                }
            }
        }

        if (plan.hasLampChanges()) {
            AutoLightRegulator.LampAdjust needsWo = null;
            for (AutoLightRegulator.LampAdjust adj : plan.lamps()) {
                setDimming(adj.deviceSn(), adj.toPct(), "AUTO");
                if (adj.toPct() >= dimThr && (needsWo == null || adj.toPct() > needsWo.toPct())) {
                    needsWo = adj;
                }
            }
            if (needsWo != null) {
                createWorkOrder(zoneId, needsWo.deviceSn(),
                        "AUTO 已执行调光至 " + needsWo.toPct() + "%（≥" + dimThr + "%，待复核）",
                        needsWo.toPct(), null);
            }
            lastLampActionSimMinute.put(zoneId, simMinuteOfDay);
        }
    }

    /** @deprecated 规则已走 {@link AutoLightRegulator}；保留阈值语义供对照 */
    private void applyDimToAll(List<GhDevice> lamps, int next, String source,
                               String zoneId, String reason, int approveDimAbove) {
        if ("AUTO".equals(source) && next >= approveDimAbove) {
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

    /**
     * 控制与曲线共用瞬时目标：直接用动态带，不再用慢速 EMA 把理想带拖成斜坡。
     */
    private double[] peekTargetBand(String zoneId, DynamicLightTarget.Result dyn) {
        return new double[]{dyn.instantMin(), dyn.instantMax()};
    }

    /**
     * 记录当前缩放，供诊断；不再限速，避免理想带与调控脱节。
     */
    private double[] advanceTargetBand(String zoneId, DynamicLightTarget.Result dyn) {
        double rawScale = dyn.photoperiodMask() * dyn.vpdFactor() * dyn.dliCatchUp();
        targetScaleEma.put(zoneId, rawScale);
        return new double[]{dyn.instantMin(), dyn.instantMax()};
    }

    /**
     * 只读快照：不推进滤波器（供 REST 轮询，避免每次拉光场就误加速温湿）。
     */
    private double[] climateSnapshot(String zoneId, double minute, Integer shadeOpen, double outdoorPar) {
        double[] s = climateSmooth.get(zoneId);
        if (s != null) {
            return s;
        }
        double shade = shadeOpen != null ? shadeOpen : 100;
        return new double[]{
                synthHumidityIdeal(minute, shade),
                synthTempIdeal(minute, outdoorPar),
                shade
        };
    }

    /**
     * 仿真 tick：温湿低通 + 遮阳滞后。遮阳四档阶跃不直接写入湿度，避免 VPD→目标带锯齿牵动 AUTO。
     *
     * @return [humidityPct, tempC, shadeLag]
     */
    private double[] advanceClimate(String zoneId, double minute, Integer shadeOpen, double outdoorPar) {
        double targetShade = shadeOpen != null ? shadeOpen : 100;
        double step = minutesPerTick();
        // 遮阳对微气候约 20 仿真分钟跟上；温湿约 6 仿真分钟时间常数
        double shadeAlpha = Math.min(1.0, step / 20.0);
        double climateAlpha = Math.min(1.0, step / 6.0);

        double[] s = climateSmooth.get(zoneId);
        if (s == null) {
            double h0 = synthHumidityIdeal(minute, targetShade);
            double t0 = synthTempIdeal(minute, outdoorPar);
            s = new double[]{h0, t0, targetShade};
            climateSmooth.put(zoneId, s);
            return s;
        }

        s[2] += (targetShade - s[2]) * shadeAlpha;
        double idealH = synthHumidityIdeal(minute, s[2]);
        double idealT = synthTempIdeal(minute, outdoorPar);
        s[0] += (idealH - s[0]) * climateAlpha;
        s[1] += (idealT - s[1]) * climateAlpha;
        s[0] = Math.max(40, Math.min(95, s[0]));
        s[1] = Math.max(12, Math.min(38, s[1]));
        return s;
    }

    /** 湿度理想曲线：夜间高、正午低；遮阳闭合缓慢抬升（由 shadeLag 驱动） */
    private static double synthHumidityIdeal(double minute, double shadeOpenLag) {
        double day = Math.sin(Math.PI * ((minute - 360) / 720.0));
        day = Math.max(0, Math.min(1, day));
        // 二次平滑日形，减少正午附近一阶尖点
        double daySoft = day * day * (3 - 2 * day);
        double base = 84 - daySoft * 28 + (100 - shadeOpenLag) * 0.06;
        return Math.max(40, Math.min(95, base));
    }

    private static double synthTempIdeal(double minute, double outdoorPar) {
        double day = Math.sin(Math.PI * ((minute - 360) / 720.0));
        day = Math.max(0, Math.min(1, day));
        double daySoft = day * day * (3 - 2 * day);
        // 室外 PAR 用软饱和，避免气候样条拐点直接打进温度
        double parBump = 5.5 * (1.0 - Math.exp(-Math.max(0, outdoorPar) / 280.0));
        return 18 + daySoft * 11 + parBump;
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

    private void evaluateLightAlarms(GhZone zone, LightFieldModel.FieldResult field, GhRecipe recipe) {
        if (recipe == null || field == null) {
            return;
        }
        double ppfd = field.effectivePpfd();
        double hardMin = recipe.getPpfdHardMin() != null ? recipe.getPpfdHardMin().doubleValue() : 0;
        double hardMax = recipe.getPpfdHardMax() != null ? recipe.getPpfdHardMax().doubleValue() : 9999;
        // 光周期外不报欠光（夜）
        if (simMinuteOfDay >= 360 && simMinuteOfDay <= 1080) {
            if (ppfd < hardMin - 0.5) {
                raiseAlarm(zone.getZoneId(), null, "UNDER_PPFD",
                        zone.getZoneId() + " 有效光 " + round(ppfd) + " < 硬限 " + round(hardMin));
            } else {
                autoResolve(zone.getZoneId(), "UNDER_PPFD");
            }
            if (ppfd > hardMax + 0.5) {
                raiseAlarm(zone.getZoneId(), null, "OVER_PPFD",
                        zone.getZoneId() + " 有效光 " + round(ppfd) + " > 硬限 " + round(hardMax));
            } else {
                autoResolve(zone.getZoneId(), "OVER_PPFD");
            }
        }
        for (GhDevice d : devicesOf(zone.getZoneId())) {
            if ("OFFLINE".equalsIgnoreCase(d.getOnlineStatus())) {
                raiseAlarm(zone.getZoneId(), d.getDeviceSn(), "DEVICE_OFFLINE",
                        d.getDeviceSn() + " 离线");
            }
        }
    }

    private void raiseAlarm(String zoneId, String deviceSn, String type, String message) {
        Long active = alarmMapper.selectCount(new LambdaQueryWrapper<GhAlarm>()
                .eq(GhAlarm::getStatus, "ACTIVE")
                .eq(GhAlarm::getAlarmType, type)
                .eq(zoneId != null, GhAlarm::getZoneId, zoneId)
                .eq(deviceSn != null, GhAlarm::getDeviceSn, deviceSn));
        if (active != null && active > 0) {
            return;
        }
        GhAlarm a = new GhAlarm()
                .setZoneId(zoneId)
                .setDeviceSn(deviceSn)
                .setAlarmType(type)
                .setMessage(message)
                .setStatus("ACTIVE")
                .setCreatedAt(LocalDateTime.now());
        alarmMapper.insert(a);
        pushAlarmWs(a);
        log.info("光棚告警: type={} zone={} msg={}", type, zoneId, message);
    }

    private void autoResolve(String zoneId, String type) {
        List<GhAlarm> list = alarmMapper.selectList(new LambdaQueryWrapper<GhAlarm>()
                .eq(GhAlarm::getStatus, "ACTIVE")
                .eq(GhAlarm::getAlarmType, type)
                .eq(GhAlarm::getZoneId, zoneId));
        for (GhAlarm a : list) {
            a.setStatus("RESOLVED");
            a.setResolvedAt(LocalDateTime.now());
            alarmMapper.updateById(a);
            pushAlarmWs(a);
        }
    }

    private void ackPendingControl(String sn, GhDevice device) {
        List<GhControlLog> pending = controlLogMapper.selectList(new LambdaQueryWrapper<GhControlLog>()
                .eq(GhControlLog::getDeviceSn, sn)
                .eq(GhControlLog::getExecutionStatus, "PENDING")
                .orderByDesc(GhControlLog::getCreatedAt)
                .last("LIMIT 5"));
        for (GhControlLog row : pending) {
            if (!statusMatchesCommand(row, device)) {
                continue;
            }
            row.setExecutionStatus("SUCCESS");
            controlLogMapper.updateById(row);
        }
    }

    private boolean statusMatchesCommand(GhControlLog row, GhDevice device) {
        if (row.getCommand() == null) {
            return true;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = row.getPayloadJson() == null ? Map.of()
                    : OM.readValue(row.getPayloadJson(), Map.class);
            if ("SET_DIMMING".equals(row.getCommand()) && payload.get("dimmingPercent") != null) {
                int expect = Integer.parseInt(payload.get("dimmingPercent").toString());
                int actual = device.getDimmingPercent() != null ? device.getDimmingPercent() : -999;
                return Math.abs(expect - actual) <= 3;
            }
            if ("SET_OPEN_PERCENT".equals(row.getCommand()) && payload.get("shadeOpenPercent") != null) {
                int expect = Integer.parseInt(payload.get("shadeOpenPercent").toString());
                int actual = device.getShadeOpenPercent() != null ? device.getShadeOpenPercent() : -999;
                return Math.abs(expect - actual) <= 5;
            }
        } catch (Exception ignored) {
            return true;
        }
        return true;
    }

    /** sim.* 执行器：本地已落库，下一拍自动 SUCCESS（不等 MQTT 回执） */
    private void resolveSimPendingAcks() {
        List<GhControlLog> pending = controlLogMapper.selectList(new LambdaQueryWrapper<GhControlLog>()
                .eq(GhControlLog::getExecutionStatus, "PENDING")
                .last("LIMIT 100"));
        for (GhControlLog row : pending) {
            if (row.getDeviceSn() == null) {
                continue;
            }
            GhDevice d = deviceMapper.selectOne(new LambdaQueryWrapper<GhDevice>()
                    .eq(GhDevice::getDeviceSn, row.getDeviceSn()));
            if (d == null || d.getAdapterId() == null || !d.getAdapterId().startsWith("sim.")) {
                continue;
            }
            if (statusMatchesCommand(row, d)) {
                row.setExecutionStatus("SUCCESS");
                controlLogMapper.updateById(row);
            }
        }
    }

    private void expireTimedOutCommands() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(Math.max(5, commandTimeoutSec));
        List<GhControlLog> pending = controlLogMapper.selectList(new LambdaQueryWrapper<GhControlLog>()
                .eq(GhControlLog::getExecutionStatus, "PENDING")
                .lt(GhControlLog::getCreatedAt, cutoff)
                .last("LIMIT 50"));
        for (GhControlLog row : pending) {
            row.setExecutionStatus("TIMEOUT");
            controlLogMapper.updateById(row);
            raiseAlarm(row.getZoneId(), row.getDeviceSn(), "COMMAND_TIMEOUT",
                    (row.getDeviceSn() != null ? row.getDeviceSn() : "?") + " 指令超时: " + row.getCommand());
        }
    }

    private void publishSimTelemetry(List<GhDevice> devices, LightFieldModel.FieldResult field,
                                     double tempC, double humidity) {
        if (!publishMqttTelemetry || mqttConfig == null || field == null) {
            return;
        }
        for (GhDevice sensor : devices) {
            if (!"PAR_SENSOR".equals(sensor.getDeviceType())) {
                continue;
            }
            Double ppfd = field.sensorPpfd().get(sensor.getDeviceSn());
            if (ppfd == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deviceSn", sensor.getDeviceSn());
            payload.put("zoneId", sensor.getZoneId());
            payload.put("ppfd", round(ppfd));
            payload.put("lux", round(ppfd * 54));
            payload.put("temperatureC", round(tempC));
            payload.put("humidityPct", round(humidity));
            payload.put("source", "SIM");
            mqttConfig.publishGreenhouseTelemetry(sensor.getDeviceSn(), payload);
        }
    }

    private void pushAlarmWs(GhAlarm a) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", a.getId());
            data.put("zoneId", a.getZoneId());
            data.put("deviceSn", a.getDeviceSn());
            data.put("deviceName", a.getDeviceSn() != null ? a.getDeviceSn() : a.getZoneId());
            data.put("alarmType", a.getAlarmType());
            data.put("message", a.getMessage());
            data.put("status", a.getStatus());
            data.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            WebSocketMessage msg = WebSocketMessage.builder()
                    .type("GH_ALARM")
                    .timestamp(LocalDateTime.now())
                    .data(data)
                    .build();
            messagingTemplate.convertAndSend("/topic/greenhouse-alarms", msg);
            messagingTemplate.convertAndSend("/topic/alarms", msg);
        } catch (Exception e) {
            log.debug("推送光棚告警 WS 失败: {}", e.getMessage());
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
