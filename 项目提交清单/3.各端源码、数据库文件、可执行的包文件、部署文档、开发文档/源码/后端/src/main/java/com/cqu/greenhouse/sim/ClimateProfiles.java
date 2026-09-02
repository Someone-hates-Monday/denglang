package com.cqu.greenhouse.sim;

import java.util.List;
import java.util.Map;

/**
 * 重庆典型日室外 PAR（µmol·m⁻²·s⁻¹）模板。
 */
public final class ClimateProfiles {

    public record Sample(int minuteOfDay, double outdoorParPpfd) {
    }

    public record Profile(String id, String labelZh, List<Sample> samples) {
    }

    private static final Map<String, Profile> PROFILES = Map.of(
            "cq-winter-fog", new Profile("cq-winter-fog", "重庆冬雾寡照", List.of(
                    new Sample(0, 0), new Sample(480, 35), new Sample(720, 85),
                    new Sample(960, 30), new Sample(1080, 0), new Sample(1440, 0)
            )),
            "cq-winter-clear", new Profile("cq-winter-clear", "重庆冬晴", List.of(
                    new Sample(0, 0), new Sample(480, 80), new Sample(720, 280),
                    new Sample(960, 120), new Sample(1080, 0), new Sample(1440, 0)
            )),
            "cq-summer-noon", new Profile("cq-summer-noon", "重庆夏正午强光", List.of(
                    new Sample(0, 0), new Sample(420, 200), new Sample(720, 1200),
                    new Sample(900, 900), new Sample(1140, 50), new Sample(1440, 0)
            )),
            "cq-overcast", new Profile("cq-overcast", "重庆阴天", List.of(
                    new Sample(0, 0), new Sample(480, 60), new Sample(720, 160),
                    new Sample(960, 70), new Sample(1080, 0), new Sample(1440, 0)
            ))
    );

    private ClimateProfiles() {
    }

    public static Profile get(String id) {
        Profile p = PROFILES.get(id);
        return p != null ? p : PROFILES.get("cq-winter-fog");
    }

    public static Map<String, Profile> all() {
        return PROFILES;
    }

    public static double outdoorParAt(String profileId, int minuteOfDay) {
        return outdoorParAt(profileId, (double) minuteOfDay);
    }

    /** 连续仿真用：对 minuteOfDay 做线性插值（可含小数）。 */
    public static double outdoorParAt(String profileId, double minuteOfDay) {
        Profile p = get(profileId);
        List<Sample> s = p.samples();
        double m = minuteOfDay % 1440.0;
        if (m < 0) {
            m += 1440.0;
        }
        for (int i = 0; i < s.size() - 1; i++) {
            Sample a = s.get(i);
            Sample b = s.get(i + 1);
            if (m >= a.minuteOfDay() && m <= b.minuteOfDay()) {
                double t = (m - a.minuteOfDay()) / (double) Math.max(1, b.minuteOfDay() - a.minuteOfDay());
                return a.outdoorParPpfd() + t * (b.outdoorParPpfd() - a.outdoorParPpfd());
            }
        }
        return 0;
    }
}
