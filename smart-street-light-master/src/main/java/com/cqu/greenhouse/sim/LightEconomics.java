package com.cqu.greenhouse.sim;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 遮光粗档 + 补光能耗 / 产量指数权衡。
 * <p>
 * 外遮阳机械上难微调 → 仅 100/70/40/10 四档。
 * 原则：免费日光优先；有灯在亮时不关遮阳（除非硬限光抑制）；
 * 关遮阳若会使自然光掉到目标下并被迫开灯 → 默认不关。
 */
public final class LightEconomics {

    /** 外遮阳可用开度档（%） */
    public static final int[] SHADE_STEPS = {100, 70, 40, 10};

    /** 单灯满功率近似（W），用于日能耗估算 */
    public static final double LAMP_WATT_FULL = 200.0;
    /** 电价 元/kWh（演示） */
    public static final double YUAN_PER_KWH = 0.65;

    private LightEconomics() {
    }

    public static int snapShadeOpen(int openPercent) {
        int best = SHADE_STEPS[0];
        int bestDist = Math.abs(openPercent - best);
        for (int s : SHADE_STEPS) {
            int d = Math.abs(openPercent - s);
            if (d < bestDist) {
                best = s;
                bestDist = d;
            }
        }
        return best;
    }

    /** 下一档：更关（开度更小） */
    public static int stepShadeClosed(int currentOpen) {
        int cur = snapShadeOpen(currentOpen);
        int candidate = -1;
        for (int s : SHADE_STEPS) {
            if (s < cur && (candidate < 0 || s > candidate)) {
                candidate = s;
            }
        }
        return candidate < 0 ? cur : candidate;
    }

    /** 下一档：更开 */
    public static int stepShadeOpened(int currentOpen) {
        int cur = snapShadeOpen(currentOpen);
        int candidate = -1;
        for (int s : SHADE_STEPS) {
            if (s > cur && (candidate < 0 || s < candidate)) {
                candidate = s;
            }
        }
        return candidate < 0 ? cur : candidate;
    }

    /**
     * 是否值得关遮阳挡过光。
     *
     * @param effectiveNow 当前有效光
     * @param naturalIfOpen 遮阳全开时自然光（近似用 outdoorIn / shadeTrans 反推或传入）
     * @param naturalIfClosed 关一档后自然光
     * @param targetMid 动态目标中值
     * @param hardMax 硬限
     * @param avgDim 当前平均调光
     */
    public static boolean shouldCloseShade(double effectiveNow, double naturalIfOpen,
                                           double naturalIfClosed, double targetMid,
                                           double hardMax, int avgDim) {
        // 光抑制硬限：必须挡
        if (effectiveNow >= hardMax && hardMax > 1) {
            return true;
        }
        // 灯还在亮：关遮阳会「挡免费光又用电」→ 禁止（先降灯）
        if (avgDim > 8) {
            return false;
        }
        // 关遮阳后自然光跌破目标，日落后还得补灯 → 性价比差
        if (naturalIfClosed < targetMid * 0.92 && targetMid > 5) {
            return false;
        }
        // 仅当明显过目标且关档后自然光仍够用
        return effectiveNow > targetMid * 1.2 && naturalIfClosed >= targetMid * 0.9;
    }

    public static boolean shouldOpenShade(double effectiveNow, double targetMin, int currentOpen) {
        if (currentOpen >= 100) {
            return false;
        }
        // 欠光时优先开遮阳拿免费日光
        return effectiveNow < targetMin || currentOpen < 100;
    }

    public static Map<String, Object> summarize(
            String recipeId,
            double dliSoFar,
            double dliTargetMin,
            int avgDim,
            int lampCount,
            int shadeOpen,
            double effectivePpfd,
            double naturalPpfd,
            double ledPpfd,
            double targetMin,
            double targetMax,
            double dayProgress,
            String adviceHint
    ) {
        double hoursOn = Math.max(0, dayProgress) * 12.0 * (avgDim / 100.0);
        double kwh = lampCount * (LAMP_WATT_FULL / 1000.0) * hoursOn;
        double costYuan = kwh * YUAN_PER_KWH;
        double yieldIndex = dliTargetMin > 0.01
                ? Math.min(1.5, dliSoFar / dliTargetMin)
                : 0;
        // 简单平衡分：产量指数 − 归一化电费惩罚
        double balance = yieldIndex - Math.min(0.6, costYuan / 8.0);

        String advice = adviceHint;
        if (advice == null || advice.isBlank()) {
            if (shadeOpen < 100 && avgDim > 15) {
                advice = "遮阳未全开且补光在耗电：优先开遮阳，避免挡免费日光又开灯";
            } else if (effectivePpfd < targetMin && shadeOpen < 100) {
                advice = "欠光：先把遮阳开到 100% 再利用三色补光";
            } else if (effectivePpfd > targetMax && avgDim > 5) {
                advice = "过光：先降补光，勿急着关遮阳（粗档遮阳易过头）";
            } else if (effectivePpfd > targetMax && naturalPpfd > targetMax) {
                advice = "自然光偏强：仅当逼近光抑制硬限再用遮阳粗档";
            } else if (yieldIndex >= 0.95 && costYuan < 2) {
                advice = "产量进度与能耗较均衡";
            } else if (yieldIndex < 0.7) {
                advice = "日积分偏低：光周期内适度提高三色补光，优先保产量";
            } else {
                advice = "维持目标带；遮阳用粗档、补光用光谱配方";
            }
        }

        SpectrumShares.Rgb ledShare = SpectrumShares.ledForRecipe(recipeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shadeSteps", SHADE_STEPS);
        m.put("shadeOpenSnapped", snapShadeOpen(shadeOpen));
        m.put("ledShareR", round3(ledShare.r()));
        m.put("ledShareG", round3(ledShare.g()));
        m.put("ledShareB", round3(ledShare.b()));
        m.put("avgDimmingPercent", avgDim);
        m.put("lampCount", lampCount);
        m.put("ledKwhTodayEst", round3(kwh));
        m.put("energyCostYuanEst", round2(costYuan));
        m.put("yieldIndex", round3(yieldIndex));
        m.put("balanceScore", round3(balance));
        m.put("naturalPpfd", round1(naturalPpfd));
        m.put("ledPpfd", round1(ledPpfd));
        m.put("effectivePpfd", round1(effectivePpfd));
        m.put("targetMin", round1(targetMin));
        m.put("targetMax", round1(targetMax));
        m.put("yuanPerKwh", YUAN_PER_KWH);
        m.put("adviceZh", advice);
        return m;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
