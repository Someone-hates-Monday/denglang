package com.cqu.greenhouse.sim;

import com.cqu.greenhouse.entity.GhRecipe;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 瞬时光目标：配方基带 × 光周期掩码 × VPD 门控 × DLI 追赶。
 * <p>
 * 不是全植物生理模型；答辩口径对齐 RESEARCH-SOLUTION「温湿门控修正目标带」。
 */
public final class DynamicLightTarget {

    /** 高 VPD 阈值（kPa）：限制猛补光 */
    public static final double VPD_HIGH_KPA = 1.4;
    /** 偏低 VPD：可略抬目标 */
    public static final double VPD_LOW_KPA = 0.55;

    public record Result(
            double recipeMin,
            double recipeMax,
            double recipeHardMin,
            double recipeHardMax,
            double instantMin,
            double instantMax,
            double hardMin,
            double hardMax,
            double photoperiodMask,
            double vpdKpa,
            double vpdFactor,
            double dliCatchUp,
            double dliSoFar,
            double dliTargetMin,
            double dliTargetMax,
            double dliExpectedByNow,
            double photoperiodHours,
            String noteZh
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recipeMin", round1(recipeMin));
            m.put("recipeMax", round1(recipeMax));
            m.put("instantMin", round1(instantMin));
            m.put("instantMax", round1(instantMax));
            m.put("hardMin", round1(hardMin));
            m.put("hardMax", round1(hardMax));
            m.put("photoperiodMask", round3(photoperiodMask));
            m.put("vpdKpa", round3(vpdKpa));
            m.put("vpdFactor", round3(vpdFactor));
            m.put("dliCatchUp", round3(dliCatchUp));
            m.put("dliSoFar", round3(dliSoFar));
            m.put("dliTargetMin", round3(dliTargetMin));
            m.put("dliTargetMax", round3(dliTargetMax));
            m.put("dliExpectedByNow", round3(dliExpectedByNow));
            m.put("dliRemainingMin", round3(Math.max(0, dliTargetMin - dliSoFar)));
            m.put("photoperiodHours", round1(photoperiodHours));
            m.put("noteZh", noteZh);
            return m;
        }
    }

    private DynamicLightTarget() {
    }

    /**
     * Tetens 近似：饱和水汽压 (kPa) → VPD = es × (1 − RH/100)
     */
    public static double vpdKpa(double tempC, double humidityPct) {
        double rh = Math.max(1, Math.min(100, humidityPct));
        double es = 0.6108 * Math.exp((17.27 * tempC) / (tempC + 237.3));
        return Math.max(0, es * (1.0 - rh / 100.0));
    }

    /**
     * 光周期掩码：正午为中心的配方窗。
     * 窗外为 0；窗内平台为 1（贴配方带）。
     * 晨昏各约 75 分钟 smoothstep，避免矩形窗在日曲线上变成断崖。
     */
    public static double photoperiodMask(double minuteOfDay, double photoperiodHours) {
        double hours = Math.max(6, Math.min(18, photoperiodHours));
        double halfMin = hours * 30.0;
        double m = minuteOfDay % 1440.0;
        if (m < 0) {
            m += 1440.0;
        }
        double dist = Math.abs(m - 720.0);
        if (dist > halfMin) {
            return 0;
        }
        double ramp = Math.min(90.0, Math.max(45.0, halfMin * 0.22));
        if (dist <= halfMin - ramp) {
            return 1.0;
        }
        double u = (halfMin - dist) / ramp;
        u = Math.max(0, Math.min(1, u));
        return u * u * (3.0 - 2.0 * u);
    }

    public static double vpdFactor(double vpd) {
        // 连续分段线性，避免跨阈值时目标带阶跃 → AUTO 横跳
        double[] xs = {0.40, 0.55, 0.80, 1.00, 1.20, 1.40, 1.60, 1.90};
        double[] ys = {1.02, 1.02, 1.00, 1.00, 0.98, 0.96, 0.95, 0.94};
        if (vpd <= xs[0]) {
            return ys[0];
        }
        for (int i = 1; i < xs.length; i++) {
            if (vpd <= xs[i]) {
                double t = (vpd - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + t * (ys[i] - ys[i - 1]);
            }
        }
        return ys[ys.length - 1];
    }

    public static Result compute(GhRecipe recipe, double minuteOfDay,
                                 double tempC, double humidityPct, double dliSoFar) {
        double rMin = recipe.getPpfdTargetMin() != null ? recipe.getPpfdTargetMin().doubleValue() : 50;
        double rMax = recipe.getPpfdTargetMax() != null ? recipe.getPpfdTargetMax().doubleValue() : 80;
        double hMin = recipe.getPpfdHardMin() != null ? recipe.getPpfdHardMin().doubleValue() : rMin * 0.8;
        double hMax = recipe.getPpfdHardMax() != null ? recipe.getPpfdHardMax().doubleValue() : rMax * 1.2;
        double photoH = recipe.getPhotoperiodHours() != null
                ? recipe.getPhotoperiodHours().doubleValue() : 12;
        double dliMin = recipe.getDliTargetMin() != null ? recipe.getDliTargetMin().doubleValue() : 0;
        double dliMax = recipe.getDliTargetMax() != null ? recipe.getDliTargetMax().doubleValue() : 0;
        if (dliMin <= 0 && dliMax <= 0) {
            // 无 DLI 种子时用目标中值 × 光周期粗算
            double mid = (rMin + rMax) / 2.0;
            dliMin = mid * photoH * 0.0036 * 0.9;
            dliMax = mid * photoH * 0.0036 * 1.1;
        }

        double mask = photoperiodMask(minuteOfDay, photoH);
        double vpd = vpdKpa(tempC, humidityPct);
        double vf = vpdFactor(vpd);

        double halfMin = Math.max(6, Math.min(18, photoH)) * 30.0;
        double photoStart = 720.0 - halfMin;
        double photoLen = halfMin * 2.0;
        double m = minuteOfDay % 1440.0;
        if (m < 0) {
            m += 1440.0;
        }
        double elapsed = Math.max(0, Math.min(photoLen, m - photoStart));
        double dliMid = (dliMin + dliMax) / 2.0;
        double expected = photoLen > 0 ? dliMid * (elapsed / photoLen) : 0;

        double catchUp = 1.0;
        if (mask > 0.05 && expected > 0.05) {
            double ratio = expected / Math.max(dliSoFar, 0.05);
            catchUp = 1.0 + 0.04 * Math.tanh((ratio - 1.0) * 1.0);
            catchUp = clamp(catchUp, 0.97, 1.06);
        }

        double scale = mask * vf * catchUp;
        double instantMin = rMin * scale;
        double instantMax = Math.max(instantMin + 1, rMax * scale);
        // 硬限随光周期收缩，避免夜里被 hardMin 强行补光
        double hardMinDyn = hMin * mask * Math.min(1.0, vf);
        double hardMaxDyn = Math.max(hardMinDyn + 5, hMax * Math.max(mask, 0.15) * Math.max(vf, 0.85));

        String note = buildNote(mask, vf, catchUp, vpd, dliSoFar, dliMin, expected);

        return new Result(
                rMin, rMax, hMin, hMax,
                instantMin, instantMax, hardMinDyn, hardMaxDyn,
                mask, vpd, vf, catchUp,
                dliSoFar, dliMin, dliMax, expected, photoH,
                note
        );
    }

    private static String buildNote(double mask, double vf, double catchUp, double vpd,
                                    double dliSoFar, double dliMin, double expected) {
        if (mask < 0.05) {
            return "光周期外 · 瞬时目标≈0（夜间不追配方带；有光周期则日出前/日后补光）";
        }
        StringBuilder sb = new StringBuilder("动态目标 = 配方带 × 光周期 × VPD × DLI追赶");
        if (vf < 0.95) {
            sb.append(" · 高VPD(").append(String.format("%.2f", vpd)).append("kPa)压目标");
        } else if (vf > 1.03) {
            sb.append(" · 低VPD略抬目标");
        }
        if (catchUp > 1.08) {
            sb.append(" · DLI落后追赶(+").append(Math.round((catchUp - 1) * 100)).append("%)");
        } else if (catchUp < 0.92) {
            sb.append(" · DLI超前略降目标");
        }
        if (dliMin > 0) {
            sb.append(" · 日积分 ").append(String.format("%.2f", dliSoFar))
                    .append("/").append(String.format("%.2f", dliMin))
                    .append("（期望≈").append(String.format("%.2f", expected)).append("）");
        }
        return sb.toString();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
