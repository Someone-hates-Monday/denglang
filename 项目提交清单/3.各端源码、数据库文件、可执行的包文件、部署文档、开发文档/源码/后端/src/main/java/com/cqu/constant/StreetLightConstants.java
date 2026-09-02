package com.cqu.constant;

/**
 * 业务常量（告警类型、指令执行状态）
 */
public final class StreetLightConstants {

    private StreetLightConstants() {
    }

    /** 指令执行状态 */
    public static final String EXEC_PENDING = "PENDING";
    public static final String EXEC_SUCCESS = "SUCCESS";
    public static final String EXEC_TIMEOUT = "TIMEOUT";

    /** 告警类型 */
    public static final String ALARM_OFFLINE = "OFFLINE";
    public static final String ALARM_COMMAND_TIMEOUT = "COMMAND_TIMEOUT";
    public static final String ALARM_HEARTBEAT_TIMEOUT = "HEARTBEAT_TIMEOUT";
    public static final String ALARM_LIGHT_ABNORMAL = "LIGHT_ABNORMAL";

    /** 指令无回执判定超时（秒） */
    public static final int COMMAND_ACK_TIMEOUT_SECONDS = 30;
}
