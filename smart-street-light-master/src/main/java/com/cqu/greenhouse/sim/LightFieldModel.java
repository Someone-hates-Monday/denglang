package com.cqu.greenhouse.sim;

import com.cqu.greenhouse.entity.GhDevice;
import com.cqu.greenhouse.entity.GhZone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 光场：自然光（室外 PAR × 透光 × 遮阳 × 太阳高度 × 南北梯度）+ 灯具余弦/距离衰减。
 */
public final class LightFieldModel {

    public record GridPoint(double x, double y, double ppfd, double sunPpfd, double ledPpfd) {
    }

    public record FieldResult(
            double effectivePpfd,
            double outdoorInPpfd,
            double ledEffectivePpfd,
            List<GridPoint> grid,
            Map<String, Double> sensorPpfd,
            int nx,
            int ny
    ) {
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

    /**
     * @param shadeOpenOverride null=用区当前开度；100=全开（少遮）用于「未控」基线
     * @param lampsEnabled      false=不计补光，用于自然光基线
     */
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
        double shadeTrans = 1.0 - GreenhouseGeometry.MAX_SHADE_BLOCK * closed;

        double[] sunAng = GreenhouseGeometry.solarElevationAzimuth(minuteOfDay, zone.getClimateProfileId());
        double elev = sunAng[0];
        double az = sunAng[1];
        double elevFactor = GreenhouseGeometry.solarElevationFactor(elev);

        // 室外 PAR 已是日变化；再乘高度角，保证夜间/低角度自然光归零或削弱
        double sunBase = Math.max(0, outdoorPar * cover * shadeTrans * elevFactor);

        boolean diffuse = GreenhouseGeometry.isDiffuseProfile(zone.getClimateProfileId());
        double measureZ = GreenhouseGeometry.measurePlaneZ(zone.getZoneId());

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
        int nx = GreenhouseGeometry.GRID_NX;
        int ny = GreenhouseGeometry.GRID_NY;
        double margin = GreenhouseGeometry.GRID_MARGIN_M;
        double usableL = Math.max(0.5, length - 2 * margin);
        double usableW = Math.max(0.5, width - 2 * margin);

        List<GridPoint> grid = new ArrayList<>(nx * ny);
        double sum = 0;
        double ledSum = 0;
        double sunSum = 0;
        for (int iy = 0; iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                double x = margin + (ix + 0.5) * usableL / nx;
                double y = margin + (iy + 0.5) * usableW / ny;
                double sunIn = sunBase * GreenhouseGeometry.bedSunFactor(y, diffuse, az, elev);
                double led = lampContribution(x, y, measureZ, lamps);
                double ppfd = sunIn + led;
                grid.add(new GridPoint(x, y, ppfd, sunIn, led));
                sum += ppfd;
                ledSum += led;
                sunSum += sunIn;
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
            double sunIn = sunBase * GreenhouseGeometry.bedSunFactor(y, diffuse, az, elev);
            double led = lampContribution(x, y, z, lamps);
            double ppfd = sunIn + led;
            sensorPpfd.put(s.getDeviceSn(), ppfd);
            sensorValues.add(ppfd);
            sensorLed.add(led);
            sensorSun.add(sunIn);
        }

        double effective;
        double ledEff;
        double sunEff;
        String agg = zone.getAggregation() != null ? zone.getAggregation() : "AVG";
        if (sensorValues.isEmpty()) {
            effective = sum / grid.size();
            ledEff = ledSum / grid.size();
            sunEff = sunSum / grid.size();
        } else if ("MIN".equalsIgnoreCase(agg)) {
            effective = sensorValues.stream().mapToDouble(d -> d).min().orElse(0);
            ledEff = sensorLed.stream().mapToDouble(d -> d).average().orElse(0);
            sunEff = sensorSun.stream().mapToDouble(d -> d).average().orElse(0);
        } else {
            effective = sensorValues.stream().mapToDouble(d -> d).average().orElse(0);
            ledEff = sensorLed.stream().mapToDouble(d -> d).average().orElse(0);
            sunEff = sensorSun.stream().mapToDouble(d -> d).average().orElse(0);
        }

        return new FieldResult(effective, sunEff, ledEff, grid, sensorPpfd, nx, ny);
    }

    private static double lampContribution(double x, double y, double z, List<GhDevice> lamps) {
        double total = 0;
        for (GhDevice lamp : lamps) {
            if (Boolean.FALSE.equals(lamp.getPowerOn())) {
                continue;
            }
            int dim = lamp.getDimmingPercent() != null ? lamp.getDimmingPercent() : 0;
            if (dim <= 0) {
                continue;
            }
            double lx = lamp.getPosX() != null ? lamp.getPosX().doubleValue() : x;
            double ly = lamp.getPosY() != null ? lamp.getPosY().doubleValue() : y;
            double lz = lamp.getPosZ() != null ? lamp.getPosZ().doubleValue() : 1.45;
            double dx = x - lx;
            double dy = y - ly;
            double dz = Math.max(0.2, lz - z);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double cos = dz / dist;
            double maxCanopy = GreenhouseGeometry.lampMaxPpfdAtCanopy(lamp.getDeviceSn());
            double peak = maxCanopy * dz * dz;
            total += peak * cos / (dist * dist) * (dim / 100.0);
        }
        return total;
    }
}
