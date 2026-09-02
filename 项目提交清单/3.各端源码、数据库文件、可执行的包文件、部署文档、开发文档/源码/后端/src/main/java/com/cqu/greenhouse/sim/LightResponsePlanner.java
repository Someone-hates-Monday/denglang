package com.cqu.greenhouse.sim;

import com.cqu.greenhouse.entity.GhDevice;
import com.cqu.greenhouse.entity.GhRecipe;
import com.cqu.greenhouse.entity.GhZone;

import java.util.ArrayList;
import java.util.List;

/**
 * 响应矩阵一次规划：PPFD ≈ A·(d/100) + E_sun(shade)。
 * <p>
 * 产量优先贴目标带；电费次之。免费日光能覆盖则开遮阳关灯；
 * 日间过亮关遮阳；光周期内日出前/日后欠光则补光。
 */
public final class LightResponsePlanner {

    private static final double RIDGE = 0.8;
    private static final double DEADBAND = 3.0;
    /** 电费项：相对产量缺口保持次级 */
    private static final double ENERGY_WEIGHT = 0.18;

    public record SolveResult(
            Integer shadeOpen,
            int[] dimmingPct,
            String[] lampSns,
            double predictedRmse,
            String noteZh
    ) {
    }

    private LightResponsePlanner() {
    }

    public static SolveResult solve(
            GhZone zone,
            List<GhDevice> devices,
            DynamicLightTarget.Result dyn,
            GhRecipe recipe,
            double outdoorPar,
            double simMinute) {
        if (dyn.photoperiodMask() < 0.05) {
            return nightRampDown(devices);
        }
        if (!Boolean.TRUE.equals(recipe.getAutoSupplement()) && !Boolean.TRUE.equals(recipe.getAutoShade())) {
            return null;
        }

        LightFieldModel.ResponseMatrix rm = LightFieldModel.buildResponseMatrix(zone, devices);
        if (rm.lampCount() == 0 || rm.sensorCount() == 0) {
            return null;
        }

        int currentShade = zone.getShadeOpenPercent() != null ? zone.getShadeOpenPercent() : 100;
        double mid = (dyn.instantMin() + dyn.instantMax()) / 2.0;
        double tMin = dyn.instantMin();
        double tMax = dyn.instantMax();
        double hardMax = dyn.hardMax();

        int shade = pickShade(zone, devices, outdoorPar, simMinute, tMin, tMax, currentShade,
                Boolean.TRUE.equals(recipe.getAutoShade()));

        LightFieldModel.FieldResult natural = LightFieldModel.compute(
                zone, devices, outdoorPar, simMinute, shade, false);
        double[] sun = new double[rm.sensorCount()];
        for (int i = 0; i < rm.sensorCount(); i++) {
            Double v = natural.sensorPpfd().get(rm.sensorSns()[i]);
            sun[i] = v != null ? v : natural.outdoorInPpfd();
        }

        double[] dFrac = Boolean.TRUE.equals(recipe.getAutoSupplement())
                ? solveDimming(rm.a(), sun, mid, tMin, rm.lamps())
                : currentDimmingFrac(rm.lamps());

        // 欠光时禁止把灯往下调：避免「进带奖励关灯 → 下一拍欠光」锯齿
        double[] hold = currentDimmingFrac(rm.lamps());
        for (int j = 0; j < dFrac.length; j++) {
            boolean anyUnder = false;
            for (int i = 0; i < sun.length; i++) {
                double pred = sun[i];
                for (int k = 0; k < dFrac.length; k++) {
                    pred += rm.a()[i][k] * dFrac[k];
                }
                if (pred < tMin - 1 && tMin > 1) {
                    anyUnder = true;
                    break;
                }
            }
            if (anyUnder && dFrac[j] < hold[j]) {
                dFrac[j] = hold[j];
            }
        }

        return toResult(rm, shade, dFrac, sun, mid, "贴带 shade=" + shade + "% rmse≈");
    }

    /** 能用日光盖住上限就尽量开遮阳；过亮才逐档关。不在四档间横跳打分。 */
    static int pickShade(GhZone zone, List<GhDevice> devices, double outdoorPar, double simMinute,
                         double tMin, double tMax, int current, boolean autoShade) {
        if (!autoShade) {
            return LightEconomics.snapShadeOpen(current);
        }
        int[] steps = LightEconomics.SHADE_STEPS;
        int chosen = steps[steps.length - 1];
        for (int s : steps) {
            LightFieldModel.FieldResult nat = LightFieldModel.compute(
                    zone, devices, outdoorPar, simMinute, s, false);
            double sunMax = 0;
            double sunMin = Double.POSITIVE_INFINITY;
            for (Double v : nat.sensorPpfd().values()) {
                if (v == null) {
                    continue;
                }
                sunMax = Math.max(sunMax, v);
                sunMin = Math.min(sunMin, v);
            }
            if (sunMax <= tMax + 4 || (tMin > 1 && sunMin < tMin - 2)) {
                chosen = s;
                break;
            }
            chosen = s;
        }
        int snapCur = LightEconomics.snapShadeOpen(current);
        if (Math.abs(chosen - snapCur) <= 30 && current >= 40) {
            return snapCur;
        }
        return chosen;
    }

    /** 始终评估 100/70/40/10，由代价函数决定；不再从候选里删档以免卡死在 10% */
    static int[] shadeCandidates(int current, boolean autoShade, double sunMaxAtOpen, double tMax) {
        if (!autoShade) {
            return new int[]{LightEconomics.snapShadeOpen(current)};
        }
        return LightEconomics.SHADE_STEPS.clone();
    }

    /**
     * 岭回归 + 欠光加权再填充：优先把偏低测点拉到 mid。
     */
    static double[] solveDimming(double[][] a, double[] sun, double mid, double tMin,
                                 List<GhDevice> lamps) {
        int m = a.length;
        int n = a[0].length;
        double[] r = new double[m];
        for (int i = 0; i < m; i++) {
            // 欠光残差放大，过光略放大，逼近中值
            double raw = mid - sun[i];
            if (raw > 0) {
                r[i] = raw * 1.25;
            } else {
                r[i] = raw * 1.1;
            }
        }

        double[][] ata = new double[n][n];
        double[] atr = new double[n];
        for (int j = 0; j < n; j++) {
            for (int k = 0; k < n; k++) {
                double s = 0;
                for (int i = 0; i < m; i++) {
                    s += a[i][j] * a[i][k];
                }
                ata[j][k] = s;
            }
            ata[j][j] += RIDGE;
            double t = 0;
            for (int i = 0; i < m; i++) {
                t += a[i][j] * r[i];
            }
            atr[j] = t;
        }

        double[] d = solveLinear(ata, atr);
        for (int j = 0; j < n; j++) {
            d[j] = clamp(d[j], 0, lampCapFrac(lamps.get(j)));
        }

        // Gauss-Seidel：按主测点消残差
        for (int pass = 0; pass < 6; pass++) {
            for (int j = 0; j < n; j++) {
                int primary = primarySensor(a, j);
                if (primary < 0 || a[primary][j] < 0.4) {
                    continue;
                }
                double pred = sun[primary];
                for (int k = 0; k < n; k++) {
                    pred += a[primary][k] * d[k];
                }
                double err = mid - pred;
                d[j] = clamp(d[j] + 0.85 * err / a[primary][j], 0, lampCapFrac(lamps.get(j)));
            }
        }

        // 欠光兜底：仍低于 mid 的测点补灯；已过亮测点关联灯不抬升
        for (int fill = 0; fill < 4; fill++) {
            boolean any = false;
            for (int i = 0; i < m; i++) {
                double pred = sun[i];
                for (int k = 0; k < n; k++) {
                    pred += a[i][k] * d[k];
                }
                if (pred >= mid - 2) {
                    continue;
                }
                if (sun[i] > mid + 5) {
                    continue; // 自然光已偏高，不靠补光堆
                }
                int jBest = primaryLamp(a, i);
                if (jBest < 0 || a[i][jBest] < 0.3) {
                    continue;
                }
                double need = (mid - pred) / a[i][jBest];
                double before = d[jBest];
                d[jBest] = clamp(d[jBest] + need, 0, lampCapFrac(lamps.get(jBest)));
                if (Math.abs(d[jBest] - before) > 1e-4) {
                    any = true;
                }
            }
            if (!any) {
                break;
            }
        }
        return d;
    }

    private static int primarySensor(double[][] a, int lampIdx) {
        int best = -1;
        double bestA = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i][lampIdx] > bestA) {
                bestA = a[i][lampIdx];
                best = i;
            }
        }
        return best;
    }

    private static int primaryLamp(double[][] a, int sensorIdx) {
        int best = -1;
        double bestA = 0;
        for (int j = 0; j < a[sensorIdx].length; j++) {
            if (a[sensorIdx][j] > bestA) {
                bestA = a[sensorIdx][j];
                best = j;
            }
        }
        return best;
    }

    private static double lampCapFrac(GhDevice lamp) {
        return 1.0;
    }

    private static double[] currentDimmingFrac(List<GhDevice> lamps) {
        double[] d = new double[lamps.size()];
        for (int j = 0; j < lamps.size(); j++) {
            int pct = lamps.get(j).getDimmingPercent() != null ? lamps.get(j).getDimmingPercent() : 0;
            d[j] = pct / 100.0;
        }
        return d;
    }

    private static double score(double[][] a, double[] sun, double[] d,
                                double mid, double tMin, double tMax, double hardMax,
                                int shade, int currentShade) {
        int m = a.length;
        int n = d.length;
        double sse = 0;
        double under = 0;
        double overHard = 0;
        double energy = 0;
        double predMin = Double.POSITIVE_INFINITY;
        double predMax = 0;
        double sunMin = Double.POSITIVE_INFINITY;
        double sunMax = 0;

        for (int i = 0; i < m; i++) {
            sunMin = Math.min(sunMin, sun[i]);
            sunMax = Math.max(sunMax, sun[i]);
            double pred = sun[i];
            for (int j = 0; j < n; j++) {
                pred += a[i][j] * d[j];
            }
            predMin = Math.min(predMin, pred);
            predMax = Math.max(predMax, pred);
            double e = pred - mid;
            sse += e * e;
            if (pred < tMin - 1 && tMin > 1) {
                under += (tMin - pred) * (tMin - pred);
            }
            if (pred > hardMax && hardMax > 1) {
                overHard += (pred - hardMax) * (pred - hardMax);
            }
        }
        for (double v : d) {
            energy += v * v;
        }

        double sunAvg = 0;
        for (int i = 0; i < m; i++) {
            sunAvg += sun[i];
        }
        sunAvg /= Math.max(1, m);

        double avgDim = 0;
        for (double v : d) {
            avgDim += v;
        }
        avgDim /= Math.max(1, n);

        double spreadPen = 0;
        if (Double.isFinite(predMin)) {
            spreadPen = (predMax - predMin) * (predMax - predMin) * 0.12;
        }

        double switchPen = shade != currentShade ? 8.0 : 0;

        // 产量：贴目标中值；欠光权重大于过光（产量损失不可逆）
        double yieldPen = sse * 2.2 + under * 14 + overHard * 11;

        // 日间过亮：关遮阳便宜；欠光：必须开遮阳拿免费日光，再谈补光
        double shadePen;
        if (tMax > 1 && sunAvg > tMax + 2) {
            shadePen = -(100 - shade) * 1.35;
            if (shade >= 100) {
                shadePen += Math.max(0, sunMax - tMax) * Math.max(0, sunMax - tMax) * 6.0 + 180;
            }
        } else if (tMin > 1 && sunMin < tMin - 1) {
            shadePen = (100 - shade) * 3.2;
            if (shade < 100) {
                shadePen += (tMin - sunMin) * (tMin - sunMin) * 4.0;
            }
        } else {
            shadePen = (100 - shade) * 0.18;
        }

        double wastePen = 0;
        if (shade <= 40 && avgDim > 0.45 && sunMin < tMin) {
            wastePen = (avgDim - 0.4) * (40 - shade) * 10;
        }

        double inBandBonus = 0;
        if (predMin >= tMin - 2 && predMax <= tMax + 3) {
            inBandBonus = -avgDim * 8; // 已进带则奖励少用电
            if (sunAvg <= tMax && shade >= 70) {
                inBandBonus -= 20;
            }
        }

        return yieldPen + ENERGY_WEIGHT * energy * m
                + shadePen + spreadPen + switchPen + wastePen + inBandBonus;
    }

    private static SolveResult toResult(LightFieldModel.ResponseMatrix rm, int shade,
                                        double[] dFrac, double[] sun, double mid, String note) {
        int n = dFrac.length;
        int[] pct = new int[n];
        for (int j = 0; j < n; j++) {
            pct[j] = (int) Math.round(clamp(dFrac[j], 0, 1) * 100.0 / 5.0) * 5;
            pct[j] = (int) clamp(pct[j], 0, lampCapFrac(rm.lamps().get(j)) * 100);
        }
        double sse = 0;
        for (int i = 0; i < rm.sensorCount(); i++) {
            double pred = sun[i];
            for (int j = 0; j < n; j++) {
                pred += rm.a()[i][j] * (pct[j] / 100.0);
            }
            sse += (pred - mid) * (pred - mid);
        }
        double rmse = Math.sqrt(sse / Math.max(1, rm.sensorCount()));
        return new SolveResult(shade, pct, rm.lampSns().clone(), rmse, note + Math.round(rmse));
    }

    private static SolveResult nightRampDown(List<GhDevice> devices) {
        List<GhDevice> lamps = devices.stream()
                .filter(d -> "GROW_LAMP".equals(d.getDeviceType()))
                .toList();
        int[] pct = new int[lamps.size()];
        String[] sns = new String[lamps.size()];
        boolean any = false;
        for (int i = 0; i < lamps.size(); i++) {
            sns[i] = lamps.get(i).getDeviceSn();
            int cur = lamps.get(i).getDimmingPercent() != null ? lamps.get(i).getDimmingPercent() : 0;
            pct[i] = 0;
            if (pct[i] != cur) {
                any = true;
            }
        }
        if (!any) {
            return new SolveResult(null, new int[0], new String[0], 0, "光周期外已灭灯");
        }
        return new SolveResult(null, pct, sns, 0, "光周期外降灯");
    }

    static double[] solveLinear(double[][] mat, double[] b) {
        int n = b.length;
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(mat[i], 0, a[i], 0, n);
            a[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) {
                    pivot = r;
                }
            }
            double[] tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            double div = a[col][col];
            if (Math.abs(div) < 1e-9) {
                continue;
            }
            for (int c = col; c <= n; c++) {
                a[col][c] /= div;
            }
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                double f = a[r][col];
                for (int c = col; c <= n; c++) {
                    a[r][c] -= f * a[col][c];
                }
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = a[i][n];
        }
        return x;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static boolean fieldNearBand(LightFieldModel.FieldResult field, double tMin, double tMax) {
        if (field.sensorPpfd() == null || field.sensorPpfd().isEmpty()) {
            double e = field.effectivePpfd();
            return e >= tMin - DEADBAND && e <= tMax + DEADBAND;
        }
        int ok = 0;
        int n = 0;
        for (double v : field.sensorPpfd().values()) {
            n++;
            if (v >= tMin - DEADBAND && v <= tMax + DEADBAND) {
                ok++;
            }
        }
        return n > 0 && ok * 1.0 / n >= 0.7;
    }
}
