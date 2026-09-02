package com.cqu.greenhouse.sim;

import com.cqu.greenhouse.entity.GhDevice;
import com.cqu.greenhouse.entity.GhRecipe;
import com.cqu.greenhouse.entity.GhZone;

import java.util.ArrayList;
import java.util.List;

/**
 * AUTO 编排：调用 {@link LightResponsePlanner} 一次求出遮阳档 + 各灯调光，
 * 考虑邻灯耦合与遮挡（响应矩阵 A），而不是各灯独立小步横跳。
 */
public final class AutoLightRegulator {

    /** 补光重规划最短间隔（仿真分钟）— 日光缓变时不必每 tick 重解 */
    public static final double LAMP_COOLDOWN_MIN = 8.0;
    /** 遮阳换档冷却：避免每 tick 100↔10 造成日曲线锯齿 */
    public static final double SHADE_COOLDOWN_MIN = 36.0;

    public record LampAdjust(String deviceSn, int fromPct, int toPct, double localPpfd) {
    }

    public record Plan(List<LampAdjust> lamps, Integer shadeOpenNext) {
        public boolean hasLampChanges() {
            return lamps != null && !lamps.isEmpty();
        }

        public boolean hasShadeChange() {
            return shadeOpenNext != null;
        }
    }

    private AutoLightRegulator() {
    }

    public static boolean lampCooldownOk(Double lastActionMin, double simMinute) {
        return cooldownOk(lastActionMin, simMinute, LAMP_COOLDOWN_MIN);
    }

    public static boolean shadeCooldownOk(Double lastActionMin, double simMinute) {
        return cooldownOk(lastActionMin, simMinute, SHADE_COOLDOWN_MIN);
    }

    static boolean cooldownOk(Double lastActionMin, double simMinute, double cooldownMin) {
        if (lastActionMin == null) {
            return true;
        }
        double delta = simMinute - lastActionMin;
        if (delta < 0) {
            delta += 1440.0;
        }
        return delta <= 0 || delta >= cooldownMin;
    }

    public static Plan plan(
            GhZone zone,
            List<GhDevice> devices,
            LightFieldModel.FieldResult field,
            DynamicLightTarget.Result dyn,
            GhRecipe recipe,
            double outdoorPar,
            double simMinute,
            Double lastLampActionMin,
            Double lastShadeActionMin) {

        double tMin = dyn.instantMin();
        double tMax = dyn.instantMax();
        int currentShade = zone.getShadeOpenPercent() != null ? zone.getShadeOpenPercent() : 100;

        boolean lampCd = lampCooldownOk(lastLampActionMin, simMinute);
        boolean shadeCd = shadeCooldownOk(lastShadeActionMin, simMinute);

        // 任一侧冷却到期即可重规划；不再因「近似进带」整段跳过（下午欠光会被卡住）
        if (!lampCd && !shadeCd) {
            return new Plan(List.of(), null);
        }

        LightResponsePlanner.SolveResult solved = LightResponsePlanner.solve(
                zone, devices, dyn, recipe, outdoorPar, simMinute);
        if (solved == null) {
            return new Plan(List.of(), null);
        }

        Integer shadeNext = null;
        if (solved.shadeOpen() != null
                && solved.shadeOpen() != currentShade
                && shadeCd
                && Boolean.TRUE.equals(recipe.getAutoShade())) {
            shadeNext = solved.shadeOpen();
        }

        List<LampAdjust> lampPlan = new ArrayList<>();
        if (lampCd && Boolean.TRUE.equals(recipe.getAutoSupplement())
                && solved.dimmingPct() != null && solved.lampSns() != null) {
            // 已在死区内且遮阳不变：跳过小幅灯位抖动
            boolean near = dyn.photoperiodMask() >= 0.05
                    && LightResponsePlanner.fieldNearBand(field, tMin, tMax);
            if (near && shadeNext == null) {
                return new Plan(List.of(), null);
            }
            for (int i = 0; i < solved.lampSns().length; i++) {
                String sn = solved.lampSns()[i];
                int to = solved.dimmingPct()[i];
                GhDevice lamp = devices.stream()
                        .filter(d -> sn.equals(d.getDeviceSn()))
                        .findFirst()
                        .orElse(null);
                if (lamp == null) {
                    continue;
                }
                int from = lamp.getDimmingPercent() != null ? lamp.getDimmingPercent() : 0;
                if (from != to) {
                    Double local = field.sensorPpfd() != null
                            ? field.sensorPpfd().get(GreenhouseGeometry.lampParSensorSn(sn))
                            : null;
                    lampPlan.add(new LampAdjust(sn, from, to, local != null ? local : 0));
                }
            }
        }

        // 若只换遮阳、灯无变化，仍返回遮阳
        if (shadeNext != null && shadeNext > currentShade && !lampPlan.isEmpty()) {
            // 开遮阳时灯解已按「开后日光」算出，允许同 tick 落地，实现一步到位
            return new Plan(lampPlan, shadeNext);
        }

        return new Plan(lampPlan, shadeNext);
    }
}
