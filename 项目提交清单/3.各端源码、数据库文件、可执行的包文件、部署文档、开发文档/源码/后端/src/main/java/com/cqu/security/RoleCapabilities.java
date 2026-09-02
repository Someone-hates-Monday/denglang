package com.cqu.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 角色 → 能力矩阵（与 web/src/auth/rbac.ts ROLE_CAPS 保持同步）
 */
public final class RoleCapabilities {

    public static final String DASH_VIEW = "dash.view";
    public static final String GH_VIEW = "gh.view";
    public static final String GH_HEAT = "gh.heat";
    public static final String AUTO_TOGGLE = "auto.toggle";
    public static final String RECIPE_BIND = "recipe.bind";
    public static final String RECIPE_EDIT = "recipe.edit";
    public static final String CLIMATE_SET = "climate.set";
    public static final String CTRL_DIM_LOW = "ctrl.dim.low";
    public static final String CTRL_DIM_HIGH = "ctrl.dim.high";
    public static final String CTRL_SHADE = "ctrl.shade";
    public static final String SIM_RESET = "sim.reset";
    public static final String WO_LIST = "wo.list";
    public static final String WO_APPROVE = "wo.approve";
    public static final String WO_REJECT = "wo.reject";
    public static final String WO_CLAIM = "wo.claim";
    public static final String WO_COMPLETE = "wo.complete";
    public static final String DEV_VIEW = "dev.view";
    public static final String DEV_CRUD = "dev.crud";
    public static final String DEV_DEBUG = "dev.debug";
    public static final String LOG_VIEW = "log.view";
    public static final String REPORT_VIEW = "report.view";
    public static final String REPORT_WRITE = "report.write";
    public static final String USER_MANAGE = "user.manage";
    public static final String CONTACT_SEND = "contact.send";
    public static final String PERM_REQUEST = "perm.request";
    public static final String PERM_DECIDE = "perm.decide";

    private static final Set<String> ALL = Set.of(
            DASH_VIEW, GH_VIEW, GH_HEAT, AUTO_TOGGLE, RECIPE_BIND, RECIPE_EDIT, CLIMATE_SET,
            CTRL_DIM_LOW, CTRL_DIM_HIGH, CTRL_SHADE, SIM_RESET,
            WO_LIST, WO_APPROVE, WO_REJECT, WO_CLAIM, WO_COMPLETE,
            DEV_VIEW, DEV_CRUD, DEV_DEBUG, LOG_VIEW, REPORT_VIEW, REPORT_WRITE,
            USER_MANAGE, CONTACT_SEND, PERM_REQUEST, PERM_DECIDE
    );

    private static final Map<String, Set<String>> ROLE_CAPS = build();

    private RoleCapabilities() {
    }

    private static Map<String, Set<String>> build() {
        Map<String, Set<String>> m = new HashMap<>();
        // 场长：策略/审批；设备只读；无强制调试
        m.put(RoleCodes.SITE_MANAGER, Set.of(
                DASH_VIEW, GH_VIEW, GH_HEAT, AUTO_TOGGLE, RECIPE_BIND, CLIMATE_SET,
                CTRL_DIM_LOW, CTRL_SHADE, SIM_RESET,
                WO_LIST, WO_APPROVE, WO_REJECT, DEV_VIEW, LOG_VIEW,
                REPORT_VIEW, REPORT_WRITE, CONTACT_SEND, PERM_REQUEST, PERM_DECIDE
        ));
        m.put(RoleCodes.AGRONOMIST, Set.of(
                DASH_VIEW, GH_VIEW, GH_HEAT, AUTO_TOGGLE, RECIPE_BIND, RECIPE_EDIT, CLIMATE_SET,
                CTRL_DIM_LOW, CTRL_DIM_HIGH, CTRL_SHADE, SIM_RESET,
                WO_LIST, WO_APPROVE, WO_REJECT, DEV_VIEW, LOG_VIEW,
                REPORT_VIEW, REPORT_WRITE, CONTACT_SEND, PERM_REQUEST, PERM_DECIDE
        ));
        m.put(RoleCodes.GROWER, Set.of(
                DASH_VIEW, GH_VIEW, GH_HEAT, CTRL_DIM_LOW, CTRL_SHADE,
                WO_LIST, WO_CLAIM, WO_COMPLETE, LOG_VIEW,
                REPORT_VIEW, REPORT_WRITE, CONTACT_SEND, PERM_REQUEST
        ));
        m.put(RoleCodes.DEVICE_OPS, Set.of(
                DASH_VIEW, GH_VIEW, GH_HEAT, CTRL_DIM_LOW, CTRL_DIM_HIGH, CTRL_SHADE,
                WO_LIST, WO_CLAIM, WO_COMPLETE, DEV_VIEW, DEV_CRUD, DEV_DEBUG, LOG_VIEW,
                REPORT_VIEW, REPORT_WRITE, CONTACT_SEND, PERM_REQUEST
        ));
        // 学员：观察 + 实训报告（无生产控灯、无日志能力）
        m.put(RoleCodes.TRAINEE, Set.of(
                DASH_VIEW, GH_VIEW, GH_HEAT, WO_LIST, REPORT_VIEW, REPORT_WRITE
        ));
        m.put(RoleCodes.SYS_ADMIN, ALL);
        return Collections.unmodifiableMap(m);
    }

    public static boolean can(String role, String capability) {
        if (capability == null || capability.isBlank()) {
            return false;
        }
        String key = RoleCodes.normalize(role);
        Set<String> caps = ROLE_CAPS.getOrDefault(key, Set.of());
        return caps.contains(capability);
    }

    public static void require(String role, String capability) {
        if (!can(role, capability)) {
            throw new ForbiddenException("无权限: " + capability);
        }
    }

    /** 调光：&lt;80 需 low；≥80 需 high */
    public static void requireDimming(String role, int percent) {
        if (percent >= 80) {
            require(role, CTRL_DIM_HIGH);
        } else {
            require(role, CTRL_DIM_LOW);
        }
    }
}
