package com.cqu.greenhouse.sim;

import com.cqu.greenhouse.entity.GhDevice;
import com.cqu.greenhouse.entity.GhZone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 光场 v1.3：日光（直射/漫射 + 高度角 + 床架软影）+ 补光（逆平方 × 光束角 × 床体遮挡）。
 * 布局真源：layouts/cq-demo-bay-v1.json · LIGHTING-UPGRADE-v1.3.md
 */
public final class LightFieldModel {

    public record GridPoint(
            double x, double y,
            double ppfd, double sunPpfd, double ledPpfd,
            double rPpfd, double gPpfd, double bPpfd
    ) {
    }

    public record BedLightStat(String bedId, double avgPpfd, double minPpfd, double avgLed, int cellCount) {
    }

    public record FieldResult(
            double effectivePpfd,
            double outdoorInPpfd,
            double ledEffectivePpfd,
            List<GridPoint> grid,
            Map<String, Double> sensorPpfd,
            int nx,
            int ny,
            double shadeTransmittance,
            double coverTransmittance,
            Map<String, BedLightStat> bedStats,
            Map<String, Object> sunModel
    ) {
        public FieldResult(double effectivePpfd, double outdoorInPpfd, double ledEffectivePpfd,
                           List<GridPoint> grid, Map<String, Double> sensorPpfd, int nx, int ny,
                           double shadeTransmittance, double coverTransmittance) {
            this(effectivePpfd, outdoorInPpfd, ledEffectivePpfd, grid, sensorPpfd, nx, ny,
                    shadeTransmittance, coverTransmittance, Map.of(), Map.of());
        }
    }

    private LightFieldModel() {
    }

    public static FieldResult compute(GhZone zone, List<GhDevice> devices, double outdoorPar) {
        return compute(zone, devices, outdoorPar, 720.0, null, true);
    }

    public static FieldResult compute(GhZone zone, List<GhDevice> devices, double outdoorPar,
                                      double minuteOfDay) {
        return compute(zone, devices, outdoorPar, minuteOfDay, null, true);
    }

    public static FieldResult compute(GhZone zone, List<GhDevice> devices, double outdoorPar,
                                      double minuteOfDay,
                                      Integer shadeOpenOverride, boolean lampsEnabled) {
        double cover = zone.getCoverTransmittance() != null
                ? zone.getCoverTransmittance().doubleValue()
                : GreenhouseGeometry.COVER_TRANSMITTANCE;
        int shadeOpen = shadeOpenOverride != null
                ? shadeOpenOverride
                : (zone.getShadeOpenPercent() != null ? zone.getShadeOpenPercent() : 100);
        double closed = 1.0 - shadeOpen / 100.0;

        double[] sunAng = GreenhouseGeometry.solarElevationAzimuth(minuteOfDay, zone.getClimateProfileId());
        double elev = sunAng[0];
        double az = sunAng[1];
        double elevFactor = GreenhouseGeometry.solarElevationFactor(elev);
        boolean diffuseProfile = GreenhouseGeometry.isDiffuseProfile(zone.getClimateProfileId());
        double diffuseF = GreenhouseGeometry.diffuseFraction(zone.getClimateProfileId());

        double shadeTrans = physicalShadeTransmittance(closed, elevFactor, diffuseProfile);
        double eBase = Math.max(0, outdoorPar * cover * shadeTrans * elevFactor);

        double measureZ = GreenhouseGeometry.measurePlaneZ(zone.getZoneId());
        String recipeId = zone.getRecipeId();
        String zoneId = zone.getZoneId();

        List<GhDevice> lamps = lampsEnabled
                ? devices.stream().filter(d -> "GROW_LAMP".equals(d.getDeviceType())).toList()
                : List.of();
        List<GhDevice> sensors = devices.stream()
                .filter(d -> "PAR_SENSOR".equals(d.getDeviceType()))
                .toList();

        double length = zone.getLengthM() != null
                ? zone.getLengthM().doubleValue()
                : GreenhouseGeometry.LENGTH_M;
        double width = zone.getWidthM() != null
                ? zone.getWidthM().doubleValue()
                : GreenhouseGeometry.WIDTH_M;
        // 半跨区：光场网格仍用整跨坐标，但区 bounds 在设备侧已分区
        int nx = GreenhouseGeometry.GRID_NX;
        int ny = GreenhouseGeometry.GRID_NY;
        double margin = GreenhouseGeometry.GRID_MARGIN_M;
        double usableL = Math.max(0.5, length - 2 * margin);
        double usableW = Math.max(0.5, width - 2 * margin);

        List<GreenhouseGeometry.BedBox> occluders = GreenhouseGeometry.bedOccluders(zoneId);

        List<GridPoint> grid = new ArrayList<>(nx * ny);
        Map<String, double[]> bedAcc = new LinkedHashMap<>(); // sum, min, ledSum, count
        for (String bedId : GreenhouseGeometry.l0BedIds(zoneId)) {
            bedAcc.put(bedId, new double[]{0, Double.POSITIVE_INFINITY, 0, 0});
        }

        double sum = 0;
        double ledSum = 0;
        double sunSum = 0;
        for (int iy = 0; iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                double x = margin + (ix + 0.5) * usableL / nx;
                double y = margin + (iy + 0.5) * usableW / ny;
                double sunOcc = sunOcclusion(x, y, measureZ, elev, az, diffuseF, occluders);
                double ewF = GreenhouseGeometry.bedSunFactorEastWest(x, length, az, elev, diffuseProfile);
                double dirF = (1.0 - diffuseF) * GreenhouseGeometry.bedSunFactor(y, false, az, elev) * sunOcc * ewF;
                double difF = diffuseF * GreenhouseGeometry.bedSunFactor(y, true, az, elev);
                double sunIn = eBase * (dirF + difF);
                double led = lampContribution(x, y, measureZ, lamps, occluders);
                SpectrumShares.Rgb rgb = SpectrumShares.split(sunIn, led, recipeId);
                double ppfd = sunIn + led;
                grid.add(new GridPoint(x, y, ppfd, sunIn, led, rgb.r(), rgb.g(), rgb.b()));
                sum += ppfd;
                ledSum += led;
                sunSum += sunIn;

                String bedId = GreenhouseGeometry.bedIdAt(zoneId, x, y);
                if (bedId != null && bedAcc.containsKey(bedId)) {
                    double[] a = bedAcc.get(bedId);
                    a[0] += ppfd;
                    a[1] = Math.min(a[1], ppfd);
                    a[2] += led;
                    a[3] += 1;
                }
            }
        }

        Map<String, BedLightStat> bedStats = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : bedAcc.entrySet()) {
            double[] a = e.getValue();
            int n = (int) a[3];
            if (n <= 0) {
                bedStats.put(e.getKey(), new BedLightStat(e.getKey(), 0, 0, 0, 0));
            } else {
                bedStats.put(e.getKey(), new BedLightStat(
                        e.getKey(), a[0] / n, a[1] == Double.POSITIVE_INFINITY ? 0 : a[1], a[2] / n, n));
            }
        }

        Map<String, Double> sensorPpfd = new HashMap<>();
        List<Double> sensorValues = new ArrayList<>();
        List<Double> sensorLed = new ArrayList<>();
        List<Double> sensorSun = new ArrayList<>();
        for (GhDevice s : sensors) {
            double x = s.getPosX() != null ? s.getPosX().doubleValue() : length / 2;
            double y = s.getPosY() != null ? s.getPosY().doubleValue() : width / 2;
            double z = s.getPosZ() != null ? s.getPosZ().doubleValue() : measureZ;
            double sunOcc = sunOcclusion(x, y, z, elev, az, diffuseF, occluders);
            double ewF = GreenhouseGeometry.bedSunFactorEastWest(x, length, az, elev, diffuseProfile);
            double dirF = (1.0 - diffuseF) * GreenhouseGeometry.bedSunFactor(y, false, az, elev) * sunOcc * ewF;
            double difF = diffuseF * GreenhouseGeometry.bedSunFactor(y, true, az, elev);
            double sunIn = eBase * (dirF + difF);
            double led = lampContribution(x, y, z, lamps, occluders);
            double ppfd = sunIn + led;
            sensorPpfd.put(s.getDeviceSn(), ppfd);
            sensorValues.add(ppfd);
            sensorLed.add(led);
            sensorSun.add(sunIn);
        }

        double effective;
        double ledEff;
        double sunEff;
        if (sensorValues.isEmpty()) {
            effective = sum / Math.max(1, grid.size());
            ledEff = ledSum / Math.max(1, grid.size());
            sunEff = sunSum / Math.max(1, grid.size());
        } else if (zone.getAggregation() != null && "MIN".equalsIgnoreCase(zone.getAggregation())) {
            effective = sensorValues.stream().mapToDouble(d -> d).min().orElse(0);
            ledEff = sensorLed.stream().mapToDouble(d -> d).average().orElse(0);
            sunEff = sensorSun.stream().mapToDouble(d -> d).average().orElse(0);
        } else {
            // 区有效：各床传感器均值的平均，避免单点代表全区
            effective = sensorValues.stream().mapToDouble(d -> d).average().orElse(0);
            ledEff = sensorLed.stream().mapToDouble(d -> d).average().orElse(0);
            sunEff = sensorSun.stream().mapToDouble(d -> d).average().orElse(0);
        }

        Map<String, Object> sunModel = new LinkedHashMap<>();
        sunModel.put("elevationDeg", Math.round(elev * 10.0) / 10.0);
        sunModel.put("azimuthDeg", Math.round(az * 10.0) / 10.0);
        sunModel.put("azimuthConvention", "from_north_clockwise_deg");
        double[] sunDir = GreenhouseGeometry.sunDirectionEnu(az, elev);
        sunModel.put("dirEast", Math.round(sunDir[0] * 1000.0) / 1000.0);
        sunModel.put("dirNorth", Math.round(sunDir[1] * 1000.0) / 1000.0);
        sunModel.put("dirUp", Math.round(sunDir[2] * 1000.0) / 1000.0);
        sunModel.put("elevFactor", Math.round(elevFactor * 1000.0) / 1000.0);
        sunModel.put("diffuseFraction", Math.round(diffuseF * 1000.0) / 1000.0);
        sunModel.put("outdoorPar", Math.round(outdoorPar * 10.0) / 10.0);
        sunModel.put("eBaseAfterCoverShade", Math.round(eBase * 10.0) / 10.0);
        sunModel.put("noteZh", "E_sun = E_out×τ_cover×τ_shade×sin(el)×(直射×床因子×软影 + 漫射×床因子)");

        return new FieldResult(effective, sunEff, ledEff, grid, sensorPpfd, nx, ny, shadeTrans, cover,
                bedStats, sunModel);
    }

    public static double physicalShadeTransmittance(double closed, double elevFactor, boolean diffuse) {
        closed = Math.max(0, Math.min(1, closed));
        double directFrac = diffuse
                ? 0.22
                : Math.max(0.15, Math.min(0.85, 0.25 + 0.55 * elevFactor));
        double diffuseFrac = 1.0 - directFrac;
        double directTrans = 1.0 - 0.97 * closed;
        double diffuseTrans = 1.0 - 0.78 * closed;
        return Math.max(0.04, directFrac * directTrans + diffuseFrac * diffuseTrans);
    }

    public static double naturalScaleForShadeOpen(GhZone zone, double outdoorPar, double minuteOfDay,
                                                  int shadeOpenPercent) {
        FieldResult f = compute(zone, List.of(), outdoorPar, minuteOfDay, shadeOpenPercent, false);
        return f.outdoorInPpfd();
    }

    /** 直射软影：上层床架挡住从太阳来的直射 */
    static double sunOcclusion(double x, double y, double z,
                               double elevDeg, double azFromNorth, double diffuseF,
                               List<GreenhouseGeometry.BedBox> occluders) {
        if (elevDeg < 2 || diffuseF > 0.8) {
            return 1.0;
        }
        double[] towardSun = GreenhouseGeometry.sunDirectionEnu(azFromNorth, elevDeg);
        double dx = towardSun[0];
        double dy = towardSun[1];
        double dz = towardSun[2];
        double atten = 1.0;
        for (GreenhouseGeometry.BedBox b : occluders) {
            if (b.zTop() <= z + 0.05) {
                continue;
            }
            double t = (b.zTop() - z) / Math.max(1e-3, dz);
            if (t <= 0 || t > 25) {
                continue;
            }
            double hx = x + dx * t;
            double hy = y + dy * t;
            if (hx >= b.x0() && hx <= b.x1() && hy >= b.y0() && hy <= b.y1()) {
                atten *= b.sunTransmit();
            }
        }
        return Math.max(0.15, atten);
    }

    private static double lampContribution(double x, double y, double z, List<GhDevice> lamps,
                                           List<GreenhouseGeometry.BedBox> occluders) {
        double total = 0;
        for (GhDevice lamp : lamps) {
            if (Boolean.FALSE.equals(lamp.getPowerOn())) {
                continue;
            }
            int dim = lamp.getDimmingPercent() != null ? lamp.getDimmingPercent() : 0;
            if (dim <= 0) {
                continue;
            }
            total += unitLedAt(x, y, z, lamp, occluders) * (dim / 100.0);
        }
        return total;
    }

    /**
     * 单灯 dimming=100% 时对点 (x,y,z) 的 PPFD 贡献（含床体遮挡）。
     * 用于响应矩阵 A：ppfd_led = Σ A[i,j] · (d_j/100)。
     */
    public static double unitLedAt(double x, double y, double z, GhDevice lamp,
                                   List<GreenhouseGeometry.BedBox> occluders) {
        if (lamp == null) {
            return 0;
        }
        double lx = lamp.getPosX() != null ? lamp.getPosX().doubleValue() : x;
        double ly = lamp.getPosY() != null ? lamp.getPosY().doubleValue() : y;
        double lz = lamp.getPosZ() != null ? lamp.getPosZ().doubleValue() : 1.85;
        double dx = x - lx;
        double dy = y - ly;
        double dz = z - lz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.05) {
            return 0;
        }
        double cosAim = (-dz) / dist;
        if (cosAim <= 0.02) {
            return 0;
        }
        double halfAng = Math.toRadians(GreenhouseGeometry.beamHalfAngleDeg(lamp.getDeviceSn()));
        double ang = Math.acos(Math.min(1, Math.max(-1, cosAim)));
        double beam = 1.0;
        if (ang > halfAng) {
            double soft = Math.max(0, 1.0 - (ang - halfAng) / (Math.PI / 2 - halfAng + 1e-6));
            beam = soft * soft;
            if (beam < 0.02) {
                return 0;
            }
        }
        double designH = Math.max(0.25, GreenhouseGeometry.designClearanceM(lamp.getDeviceSn()));
        double maxCanopy = GreenhouseGeometry.lampMaxPpfdAtCanopy(lamp.getDeviceSn());
        double peak = maxCanopy * designH * designH;
        double occ = ledOcclusion(lx, ly, lz, x, y, z, occluders);
        return peak * cosAim / (dist * dist) * beam * occ;
    }

    /** 测点 × 灯 响应矩阵（行=传感器，列=灯，值=100% 时贡献） */
    public static ResponseMatrix buildResponseMatrix(GhZone zone, List<GhDevice> devices) {
        List<GhDevice> lamps = devices.stream()
                .filter(d -> "GROW_LAMP".equals(d.getDeviceType()))
                .toList();
        List<GhDevice> sensors = devices.stream()
                .filter(d -> "PAR_SENSOR".equals(d.getDeviceType()))
                .toList();
        List<GreenhouseGeometry.BedBox> occluders = GreenhouseGeometry.bedOccluders(zone.getZoneId());
        int m = sensors.size();
        int n = lamps.size();
        double[][] a = new double[m][n];
        String[] sensorSns = new String[m];
        String[] lampSns = new String[n];
        for (int j = 0; j < n; j++) {
            lampSns[j] = lamps.get(j).getDeviceSn();
        }
        for (int i = 0; i < m; i++) {
            GhDevice s = sensors.get(i);
            sensorSns[i] = s.getDeviceSn();
            double x = s.getPosX() != null ? s.getPosX().doubleValue() : 0;
            double y = s.getPosY() != null ? s.getPosY().doubleValue() : 0;
            double z = s.getPosZ() != null ? s.getPosZ().doubleValue()
                    : GreenhouseGeometry.measurePlaneZ(zone.getZoneId());
            for (int j = 0; j < n; j++) {
                a[i][j] = unitLedAt(x, y, z, lamps.get(j), occluders);
            }
        }
        return new ResponseMatrix(sensorSns, lampSns, a, lamps);
    }

    public record ResponseMatrix(
            String[] sensorSns,
            String[] lampSns,
            double[][] a,
            List<GhDevice> lamps
    ) {
        public int sensorCount() {
            return sensorSns.length;
        }

        public int lampCount() {
            return lampSns.length;
        }
    }

    /** 补光线穿过其它床冠层时衰减 */
    static double ledOcclusion(double lx, double ly, double lz,
                               double x, double y, double z,
                               List<GreenhouseGeometry.BedBox> occluders) {
        double dx = x - lx;
        double dy = y - ly;
        double dz = z - lz;
        if (Math.abs(dz) < 1e-4) {
            return 1.0;
        }
        double atten = 1.0;
        for (GreenhouseGeometry.BedBox b : occluders) {
            // 只挡在灯与点之间的冠层
            double zLo = Math.min(lz, z);
            double zHi = Math.max(lz, z);
            if (b.zTop() <= zLo + 0.02 || b.zTop() >= zHi - 0.02) {
                continue;
            }
            double t = (b.zTop() - lz) / dz;
            if (t <= 0.02 || t >= 0.98) {
                continue;
            }
            double hx = lx + dx * t;
            double hy = ly + dy * t;
            if (hx >= b.x0() && hx <= b.x1() && hy >= b.y0() && hy <= b.y1()) {
                // 点若就在该床面上，不算「穿过」
                if (Math.abs(z - b.zTop()) < 0.08
                        && x >= b.x0() && x <= b.x1() && y >= b.y0() && y <= b.y1()) {
                    continue;
                }
                atten *= b.ledTransmit();
            }
        }
        return Math.max(0.08, atten);
    }
}
