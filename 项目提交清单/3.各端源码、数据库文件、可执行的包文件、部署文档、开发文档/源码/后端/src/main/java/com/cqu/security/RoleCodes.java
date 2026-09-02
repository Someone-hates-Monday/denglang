package com.cqu.security;

import java.util.Set;

/**
 * 智慧光棚角色码 — 对齐 docs/greenhouse/RBAC-ROLES.md 与 web/src/auth/rbac.ts
 */
public final class RoleCodes {

    public static final String SITE_MANAGER = "SITE_MANAGER";
    public static final String AGRONOMIST = "AGRONOMIST";
    public static final String GROWER = "GROWER";
    public static final String DEVICE_OPS = "DEVICE_OPS";
    public static final String TRAINEE = "TRAINEE";
    public static final String SYS_ADMIN = "SYS_ADMIN";

    private static final Set<String> REGISTERABLE = Set.of(
            SITE_MANAGER, AGRONOMIST, GROWER, DEVICE_OPS, TRAINEE
    );

    private RoleCodes() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return GROWER;
        }
        String r = raw.trim().toUpperCase();
        if ("ADMIN".equals(r) || SYS_ADMIN.equals(r)) {
            return SYS_ADMIN;
        }
        if ("MUNICIPAL_STAFF".equals(r) || GROWER.equals(r)) {
            return GROWER;
        }
        if (SITE_MANAGER.equals(r) || AGRONOMIST.equals(r)
                || DEVICE_OPS.equals(r) || TRAINEE.equals(r)) {
            return r;
        }
        return GROWER;
    }

    public static boolean isRegisterable(String role) {
        return REGISTERABLE.contains(normalize(role)) && !SYS_ADMIN.equals(normalize(role));
    }
}
