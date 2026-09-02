package com.cqu.greenhouse.sim;

/**
 * 日光 / 补光三色光子份额（与前端 spectrumModel 对齐，演示级）。
 */
public final class SpectrumShares {

    public record Rgb(double r, double g, double b) {
        public Rgb normalized() {
            double s = r + g + b;
            if (s <= 1e-9) {
                return new Rgb(0.33, 0.34, 0.33);
            }
            return new Rgb(r / s, g / s, b / s);
        }
    }

    /** 室外 PAR 宽带近似 */
    public static final Rgb SUN = new Rgb(0.33, 0.37, 0.30);

    private SpectrumShares() {
    }

    public static Rgb ledForRecipe(String recipeId) {
        String id = recipeId == null ? "" : recipeId.toLowerCase();
        if (id.contains("fragaria") || id.contains("strawberry")) {
            return new Rgb(0.70, 0.08, 0.22);
        }
        if (id.contains("anoectochilus")) {
            return new Rgb(0.52, 0.14, 0.34);
        }
        if (id.contains("dendrobium")) {
            return new Rgb(0.62, 0.12, 0.26);
        }
        return new Rgb(0.65, 0.10, 0.25);
    }

    public static Rgb split(double sunPpfd, double ledPpfd, String recipeId) {
        Rgb sun = SUN;
        Rgb led = ledForRecipe(recipeId);
        return new Rgb(
                sunPpfd * sun.r() + ledPpfd * led.r(),
                sunPpfd * sun.g() + ledPpfd * led.g(),
                sunPpfd * sun.b() + ledPpfd * led.b()
        );
    }
}
