package com.cqu.greenhouse.agent;

import com.cqu.greenhouse.entity.GhAlarm;
import com.cqu.greenhouse.entity.GhDevice;
import com.cqu.greenhouse.entity.GhRecipe;
import com.cqu.greenhouse.entity.GhWorkOrder;
import com.cqu.greenhouse.service.IGreenhouseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：全部走现有 Greenhouse 服务，不控灯。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreenhouseAgentTools {

    private final IGreenhouseService greenhouseService;
    private final GreenhouseKnowledgeRetriever retriever;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getZoneLight(String zoneId) {
        return slimLight(greenhouseService.getZoneEffectiveLight(zoneId));
    }

    public Map<String, Object> getEconomics(String zoneId) {
        Map<String, Object> light = greenhouseService.getZoneEffectiveLight(zoneId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("zoneId", zoneId);
        out.put("effectivePpfd", light.get("effectivePpfd"));
        out.put("dliSoFar", light.get("dliSoFar"));
        out.put("economics", light.get("economics"));
        out.put("note", "yieldIndex 为 DLI 达成率演示指标，不是千克产量；电费为估算。");
        return out;
    }

    public List<Map<String, Object>> listWorkOrders(String status) {
        List<GhWorkOrder> list = greenhouseService.listWorkOrders(status);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GhWorkOrder wo : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", wo.getId());
            m.put("zoneId", wo.getZoneId());
            m.put("status", wo.getStatus());
            m.put("reason", wo.getReason());
            m.put("suggestedDimmingPct", wo.getSuggestedDimmingPct());
            m.put("suggestedShadePct", wo.getSuggestedShadePct());
            m.put("targetDeviceSn", wo.getTargetDeviceSn());
            m.put("createdAt", wo.getCreatedAt() != null ? wo.getCreatedAt().toString() : null);
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listAlarms(String status, int limit) {
        List<GhAlarm> list = greenhouseService.listAlarms(status, limit);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GhAlarm a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("zoneId", a.getZoneId());
            m.put("deviceSn", a.getDeviceSn());
            m.put("alarmType", a.getAlarmType());
            m.put("message", a.getMessage());
            m.put("status", a.getStatus());
            m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listDevices(String zoneId) {
        List<GhDevice> list = greenhouseService.listDevices(zoneId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GhDevice d : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceSn", d.getDeviceSn());
            m.put("deviceName", d.getDeviceName());
            m.put("zoneId", d.getZoneId());
            m.put("deviceType", d.getDeviceType());
            m.put("onlineStatus", d.getOnlineStatus());
            m.put("dimmingPercent", d.getDimmingPercent());
            m.put("shadeOpenPercent", d.getShadeOpenPercent());
            m.put("lastPpfd", d.getLastPpfd());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> getRecipe(String zoneId, String recipeId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (recipeId != null && !recipeId.isBlank()) {
            GhRecipe r = greenhouseService.getRecipe(recipeId);
            out.put("recipe", slimRecipe(r));
            return out;
        }
        Map<String, Object> light = greenhouseService.getZoneEffectiveLight(zoneId);
        out.put("zoneId", zoneId);
        out.put("recipeId", light.get("recipeId"));
        Object recipeObj = light.get("recipe");
        if (recipeObj instanceof GhRecipe r) {
            out.put("recipe", slimRecipe(r));
        } else if (recipeObj instanceof Map<?, ?> m) {
            out.put("recipe", m);
        } else {
            out.put("recipe", recipeObj);
        }
        return out;
    }

    public List<KnowledgeChunk> searchKnowledge(String query, int topK) {
        return retriever.search(query, topK);
    }

    /** 默认棚况快照：两区 light 摘要 + PENDING 数 */
    public Map<String, Object> buildSnapshot(String preferredZone) {
        Map<String, Object> snap = new LinkedHashMap<>();
        List<Map<String, Object>> zones = new ArrayList<>();
        for (String zid : List.of("ZONE-A", "ZONE-B")) {
            try {
                zones.add(slimLight(greenhouseService.getZoneEffectiveLight(zid)));
            } catch (Exception e) {
                log.warn("snapshot zone {} failed: {}", zid, e.getMessage());
            }
        }
        snap.put("zones", zones);
        snap.put("focusZone", preferredZone != null ? preferredZone : "ZONE-A");
        try {
            List<GhWorkOrder> pending = greenhouseService.listWorkOrders("PENDING");
            snap.put("pendingWorkOrderCount", pending != null ? pending.size() : 0);
        } catch (Exception e) {
            snap.put("pendingWorkOrderCount", null);
        }
        return snap;
    }

    public String toJson(Object o) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> slimLight(Map<String, Object> light) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (light == null) return m;
        for (String k : List.of(
                "zoneId", "name", "recipeId", "climateProfileId", "minuteOfDay",
                "effectivePpfd", "naturalPpfd", "ledPpfd", "dliSoFar",
                "shadeOpenPercent", "humidityPct", "temperatureC", "autoControl", "economics")) {
            if (light.containsKey(k)) {
                m.put(k, light.get(k));
            }
        }
        Object recipe = light.get("recipe");
        if (recipe instanceof GhRecipe r) {
            m.put("recipe", slimRecipe(r));
        } else if (recipe != null) {
            m.put("recipe", recipe);
        }
        return m;
    }

    private Map<String, Object> slimRecipe(GhRecipe r) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (r == null) return m;
        m.put("recipeId", r.getRecipeId());
        m.put("crop", r.getCrop());
        m.put("cropNameZh", r.getCropNameZh());
        m.put("stage", r.getStage());
        m.put("ppfdTargetMin", r.getPpfdTargetMin());
        m.put("ppfdTargetMax", r.getPpfdTargetMax());
        m.put("ppfdHardMin", r.getPpfdHardMin());
        m.put("ppfdHardMax", r.getPpfdHardMax());
        m.put("dliTargetMin", r.getDliTargetMin());
        m.put("dliTargetMax", r.getDliTargetMax());
        m.put("approveDimAbove", r.getApproveDimAbove());
        m.put("approveShadeAbove", r.getApproveShadeAbove());
        return m;
    }
}
