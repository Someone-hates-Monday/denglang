package com.cqu.greenhouse.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * 棚体几何常量，对齐 {@code docs/greenhouse/layouts/cq-demo-bay-v1.json}。
 */
public final class GreenhouseGeometry {

    public static final String GEOMETRY_ID = "cq-demo-bay-v1";
    public static final double LENGTH_M = 16.0;
    public static final double WIDTH_M = 7.0;
    public static final double GUTTER_HEIGHT_M = 2.8;
    public static final double RIDGE_HEIGHT_M = 3.8;
    public static final double COVER_TRANSMITTANCE = 0.65;
    public static final double MAX_SHADE_BLOCK = 0.92;
    public static final double EXTERNAL_SHADE_Z = 3.5;
    public static final int GRID_NX = 32;
    public static final int GRID_NY = 14;
    public static final double GRID_MARGIN_M = 0.25;

    /** 南 / 中 / 北床 Y 名义中心（与布局床位一致） */
    public static final double BED_SOUTH_Y = 1.40;
    public static final double BED_MID_Y = 3.50;
    public static final double BED_NORTH_Y = 5.60;

    private GreenhouseGeometry() {
    }

    public static double measurePlaneZ(String zoneId) {
        return 0.90;
    }

    /**
     * 自北顺时针方位角下的太阳方向单位向量（布局 ENU：东、北、上）。
     * 与前端 {@code layoutCoords.sunDirectionEnu}、Three 场景日光箭头一致。
     */
    public static double[] sunDirectionEnu(double azFromNorthDeg, double elevationDeg) {
        double az = Math.toRadians(azFromNorthDeg);
        double el = Math.toRadians(elevationDeg);
        double horiz = Math.cos(el);
        return new double[]{
                Math.sin(az) * horiz,
                Math.cos(az) * horiz,
                Math.sin(el)
        };
    }

    /**
     * 东西向直射微调：上午偏东时东端（+X）略强，下午偏西时西端略强。
     * 对齐 GREENHOUSE-LAYOUT §1.1「上午 +X 侧略强」。
     */
    public static double bedSunFactorEastWest(double x, double lengthM, double azFromNorth,
                                              double elevDeg, boolean diffuse) {
        if (diffuse || elevDeg < 3 || lengthM < 1) {
            return 1.0;
        }
        double sunEast = Math.sin(Math.toRadians(azFromNorth));
        double along = (x / lengthM - 0.5) * 2.0;
        double w = solarElevationFactor(elevDeg);
        return 1.0 + 0.05 * sunEast * along * w;
    }

    public static double[] solarElevationAzimuth(double minuteOfDay, String climateProfileId) {
        double lat = Math.toRadians(29.5);
        double declDeg = -15;
        if (climateProfileId != null) {
            if (climateProfileId.contains("summer")) {
                declDeg = 20;
            } else if (climateProfileId.contains("clear") || climateProfileId.contains("overcast")) {
                declDeg = -5;
            }
        }
        double decl = Math.toRadians(declDeg);
        double hourAngle = Math.toRadians((minuteOfDay / 60.0 - 12.0) * 15.0);
        double sinEl = Math.sin(lat) * Math.sin(decl) + Math.cos(lat) * Math.cos(decl) * Math.cos(hourAngle);
        sinEl = Math.max(-1, Math.min(1, sinEl));
        double elev = Math.toDegrees(Math.asin(sinEl));
        // 方位：自北顺时针。atan2(sin H, cos H·sin φ − tan δ·cos φ) 正午为 0，再 +180 → 南。
        double azRad = Math.atan2(
                Math.sin(hourAngle),
                Math.cos(hourAngle) * Math.sin(lat) - Math.tan(decl) * Math.cos(lat));
        double azFromNorth = Math.toDegrees(azRad) + 180.0;
        if (azFromNorth < 0) {
            azFromNorth += 360.0;
        } else if (azFromNorth >= 360.0) {
            azFromNorth -= 360.0;
        }
        if (elev < 0) {
            elev = 0;
        }
        return new double[]{elev, azFromNorth};
    }

    /** 直射日型用较大南北梯度；雾/阴用漫射梯度。 */
    public static boolean isDiffuseProfile(String climateProfileId) {
        if (climateProfileId == null) {
            return true;
        }
        return climateProfileId.contains("fog")
                || climateProfileId.contains("overcast")
                || climateProfileId.contains("diffuse");
    }

    /**
     * 太阳高度对自然光的调制：夜间 0；低角度削弱；正午接近 1。
     * 与 ClimateProfiles 室外 PAR 相乘，避免「有 PAR 曲线但未跟高度角」。
     */
    public static double solarElevationFactor(double elevationDeg) {
        if (elevationDeg <= 0.5) {
            return 0;
        }
        double sinEl = Math.sin(Math.toRadians(elevationDeg));
        return Math.max(0, Math.min(1.0, sinEl));
    }

    /**
     * 南北自然光梯度：随太阳方位加强「南向采光」——正午偏南时南床更高。
     *
     * @param y           北向坐标（m）
     * @param diffuse     漫射日型
     * @param azFromNorth 太阳方位（北顺时针，°）
     * @param elevDeg     高度角（°）
     */
    public static double bedSunFactor(double y, boolean diffuse, double azFromNorth, double elevDeg) {
        double south = diffuse ? 1.02 : 1.06;
        double mid = 1.0;
        double north = diffuse ? 0.98 : 0.94;
        // 太阳越偏南（az≈180）且越高，南北差越大；清晨偏东时差缩小
        double southBias = 1.0;
        if (!diffuse && elevDeg > 5) {
            double faceSouth = Math.max(0, Math.cos(Math.toRadians(azFromNorth - 180)));
            southBias = 1.0 + 0.08 * faceSouth * solarElevationFactor(elevDeg);
            south *= southBias;
            north = mid - (south - mid) * 0.85;
        }
        if (y <= BED_SOUTH_Y) {
            return south;
        }
        if (y >= BED_NORTH_Y) {
            return north;
        }
        if (y <= BED_MID_Y) {
            double t = (y - BED_SOUTH_Y) / (BED_MID_Y - BED_SOUTH_Y);
            return south + t * (mid - south);
        }
        double t = (y - BED_MID_Y) / (BED_NORTH_Y - BED_MID_Y);
        return mid + t * (north - mid);
    }

    /** 兼容旧调用：无方位时用漫射中等梯度 */
    public static double bedSunFactor(double y, boolean diffuse) {
        return bedSunFactor(y, diffuse, 180, diffuse ? 30 : 45);
    }

    /** 园艺红蓝灯带 HORTI_BAR_RB_150：冠层峰值 150，覆盖栽培带 90–120 */
    public static double lampMaxPpfdAtCanopy(String deviceSn) {
        return 150.0;
    }

    public static double beamHalfAngleDeg(String deviceSn) {
        if (deviceSn != null && (deviceSn.contains("L1") || isUnderShelfLamp(deviceSn))) {
            return 65.0;
        }
        return 55.0;
    }

    /** 设计净空（灯心到名义冠层），用于逆平方归一；须与安装高度一致 */
    public static double designClearanceM(String deviceSn) {
        if (deviceSn != null && deviceSn.contains("L1")) {
            return 0.35;
        }
        if (isUnderShelfLamp(deviceSn)) {
            return 0.30;
        }
        return 0.85;
    }

    /** 中/北床 L0：灯装在 L1 搁架下方 */
    public static boolean isUnderShelfLamp(String deviceSn) {
        if (deviceSn == null || deviceSn.contains("L1") || !deviceSn.contains("LAMP-")) {
            return false;
        }
        String n = deviceSn.replaceAll(".*?(\\d+)$", "$1");
        try {
            return Integer.parseInt(n) >= 6;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static double diffuseFraction(String climateProfileId) {
        if (climateProfileId == null) {
            return 0.55;
        }
        if (climateProfileId.contains("fog")) {
            return 0.88;
        }
        if (climateProfileId.contains("overcast")) {
            return 0.80;
        }
        if (climateProfileId.contains("summer")) {
            return 0.35;
        }
        if (climateProfileId.contains("clear")) {
            return 0.40;
        }
        return 0.55;
    }

    public record BedBox(String bedId, double x0, double x1, double y0, double y1,
                         double zTop, double sunTransmit, double ledTransmit) {
    }

    public static List<BedBox> bedOccluders(String zoneId) {
        List<BedBox> list = new ArrayList<>();
        // L1 搁架对下层直射/补光有遮挡；L0 床宽 1.2 m（v1.4）
        if ("ZONE-A".equals(zoneId) || zoneId == null) {
            list.add(new BedBox("BED-A-M-L1", 0.5, 7.5, 2.90, 4.10, 1.35, 0.32, 0.35));
            list.add(new BedBox("BED-A-N-L1", 0.5, 7.5, 5.00, 6.20, 1.35, 0.32, 0.35));
            list.add(new BedBox("BED-A-S", 0.5, 7.5, 0.80, 2.00, 0.90, 0.55, 0.40));
            list.add(new BedBox("BED-A-M", 0.5, 7.5, 2.90, 4.10, 0.90, 0.55, 0.40));
            list.add(new BedBox("BED-A-N", 0.5, 7.5, 5.00, 6.20, 0.90, 0.55, 0.40));
        }
        if ("ZONE-B".equals(zoneId) || zoneId == null) {
            list.add(new BedBox("BED-B-M-L1", 8.5, 15.5, 2.90, 4.10, 1.35, 0.32, 0.35));
            list.add(new BedBox("BED-B-N-L1", 8.5, 15.5, 5.00, 6.20, 1.35, 0.32, 0.35));
            list.add(new BedBox("BED-B-S", 8.5, 15.5, 0.80, 2.00, 0.90, 0.55, 0.40));
            list.add(new BedBox("BED-B-M", 8.5, 15.5, 2.90, 4.10, 0.90, 0.55, 0.40));
            list.add(new BedBox("BED-B-N", 8.5, 15.5, 5.00, 6.20, 0.90, 0.55, 0.40));
        }
        return list;
    }

    public static List<String> l0BedIds(String zoneId) {
        if ("ZONE-B".equals(zoneId)) {
            return List.of("BED-B-S", "BED-B-M", "BED-B-N");
        }
        return List.of("BED-A-S", "BED-A-M", "BED-A-N");
    }

    public static String bedIdAt(String zoneId, double x, double y) {
        for (String id : l0BedIds(zoneId)) {
            BedBox b = l0Box(id);
            if (b != null && x >= b.x0() && x <= b.x1() && y >= b.y0() && y <= b.y1()) {
                return id;
            }
        }
        return null;
    }

    public static BedBox l0Box(String bedId) {
        return switch (bedId) {
            case "BED-A-S" -> new BedBox(bedId, 0.5, 7.5, 0.80, 2.00, 0.90, 0.55, 0.40);
            case "BED-A-M" -> new BedBox(bedId, 0.5, 7.5, 2.90, 4.10, 0.90, 0.55, 0.40);
            case "BED-A-N" -> new BedBox(bedId, 0.5, 7.5, 5.00, 6.20, 0.90, 0.55, 0.40);
            case "BED-B-S" -> new BedBox(bedId, 8.5, 15.5, 0.80, 2.00, 0.90, 0.55, 0.40);
            case "BED-B-M" -> new BedBox(bedId, 8.5, 15.5, 2.90, 4.10, 0.90, 0.55, 0.40);
            case "BED-B-N" -> new BedBox(bedId, 8.5, 15.5, 5.00, 6.20, 0.90, 0.55, 0.40);
            default -> null;
        };
    }

    /** 与灯同位编号的 PAR 测点（layout v1.4：LAMP-ZONE-A-01 ↔ PAR-ZONE-A-01） */
    public static String lampParSensorSn(String lampSn) {
        if (lampSn == null || !lampSn.startsWith("LAMP-")) {
            return null;
        }
        return "PAR-" + lampSn.substring(5);
    }

    /** 灯所属 L0 床；L1 返回 null（单独弱控） */
    public static String lampBedId(String deviceSn) {
        if (deviceSn == null || deviceSn.contains("L1")) {
            return null;
        }
        // v1.4：01–05 S，06–10 M，11–15 N（每床 5 段灯带）
        String n = deviceSn.replaceAll(".*?(\\d+)$", "$1");
        try {
            int i = Integer.parseInt(n);
            String prefix = deviceSn.contains("ZONE-B") ? "BED-B-" : "BED-A-";
            if (i <= 5) {
                return prefix + "S";
            }
            if (i <= 10) {
                return prefix + "M";
            }
            return prefix + "N";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * PAR 测点所属 L0 床（与 {@link #lampBedId} 同位编号）。
     * L1 测点返回 null，避免把上层光强并入 L0 分床均值。
     */
    public static String parL0BedId(String deviceSn) {
        if (deviceSn == null || !deviceSn.startsWith("PAR-") || deviceSn.contains("L1")) {
            return null;
        }
        return lampBedId(deviceSn.replace("PAR-", "LAMP-"));
    }
}
