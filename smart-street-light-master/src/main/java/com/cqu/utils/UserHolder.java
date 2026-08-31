package com.cqu.utils;

/**
 * 当前请求用户上下文（线程本地）
 */
public class UserHolder {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    public static void setCurrent(Long userId) {
        USER_ID.set(userId);
    }

    public static void setRole(String role) {
        ROLE.set(role);
    }

    public static void set(Long userId, String role) {
        USER_ID.set(userId);
        ROLE.set(role);
    }

    public static Long getCurrent() {
        return USER_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static void remove() {
        USER_ID.remove();
        ROLE.remove();
    }
}
