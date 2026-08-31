package com.cqu.config;

import com.cqu.mapper.DevicesMapper;
import com.cqu.entity.Devices;
import com.cqu.service.IAlarmLogsService;
import com.cqu.service.IDevicesService;
import com.cqu.service.ILightReadingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT 客户端配置（连接 Docker EMQX）
 * <p>
 * 订阅硬件上报 topic，路由到对应的 Service 处理；
 * 提供服务端下发指令的 publish 方法。
 */
@Slf4j
@Component
public class MqttConfig {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    /** 上报 topic 前缀 */
    private static final String TOPIC_PREFIX = "smart-light/";

    /** 下发指令 topic 模板：smart-light/{deviceSn}/command */
    private static final String COMMAND_TOPIC_TPL = TOPIC_PREFIX + "%s/command";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MqttClient mqttClient;

    @Autowired
    private DevicesMapper devicesMapper;

    @Lazy  // 打破循环依赖：DevicesServiceImpl → MqttConfig → IDevicesService
    @Autowired
    private IDevicesService devicesService;

    @Lazy  // 打破循环依赖：LightReadingsServiceImpl → MqttConfig → ILightReadingsService
    @Autowired
    private ILightReadingsService lightReadingsService;

    @Autowired
    private IAlarmLogsService alarmLogsService;

    @Lazy
    @Autowired(required = false)
    private com.cqu.greenhouse.service.IGreenhouseService greenhouseService;

    private static final String GH_TOPIC_PREFIX = "smart-greenhouse/";

    // ==================== 生命周期 ====================

    @PostConstruct
    public void init() {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
            }
            if (password != null && !password.isBlank()) {
                options.setPassword(password.toCharArray());
            }

            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("MQTT 连接成功: broker={}, reconnect={}", serverURI, reconnect);
                    subscribeTopics();
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT 连接断开: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // QoS 1/2 publish 完成回调，无需处理
                }
            });

            mqttClient.connect(options);
            log.info("MQTT 客户端初始化完成: brokerUrl={}, clientId={}", brokerUrl, clientId);
        } catch (MqttException e) {
            log.error("MQTT 客户端初始化失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT 客户端已关闭");
            } catch (MqttException e) {
                log.error("MQTT 客户端关闭异常", e);
            }
        }
    }

    // ==================== 订阅 ====================

    private void subscribeTopics() {
        try {
            // 使用单层通配符 + 匹配 deviceSn
            mqttClient.subscribe(new String[]{
                    TOPIC_PREFIX + "+/status",
                    TOPIC_PREFIX + "+/light",
                    TOPIC_PREFIX + "+/alarm",
                    GH_TOPIC_PREFIX + "+/telemetry",
                    GH_TOPIC_PREFIX + "+/status",
                    GH_TOPIC_PREFIX + "+/alarm",
            }, new int[]{0, 0, 1, 0, 0, 1});
            log.info("MQTT 已订阅: smart-light status/light/alarm + smart-greenhouse telemetry/status/alarm");
        } catch (MqttException e) {
            log.error("MQTT 订阅 topic 失败", e);
        }
    }

    /** 光棚下行：smart-greenhouse/{sn}/command */
    public boolean publishGreenhouseCommand(String deviceSn, Object payload) {
        if (deviceSn == null) {
            return false;
        }
        return publish(GH_TOPIC_PREFIX + deviceSn + "/command", payload, 1);
    }

    /** 光棚仿真遥测外发（可选，便于抓包演示） */
    public boolean publishGreenhouseTelemetry(String deviceSn, Object payload) {
        if (deviceSn == null) {
            return false;
        }
        return publish(GH_TOPIC_PREFIX + deviceSn + "/telemetry", payload, 0);
    }

    // ==================== 下发指令 ====================

    /**
     * 向指定设备下发开关指令
     *
     * @return 是否已成功 publish（MQTT 已连接且未抛异常）
     */
    public boolean publishCommand(String deviceSn, String command) {
        if (deviceSn == null) {
            log.warn("下发指令失败: deviceSn 为 null");
            return false;
        }
        String topic = String.format(COMMAND_TOPIC_TPL, deviceSn);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("command", command);
        payload.put("timestamp", LocalDateTime.now().format(FORMATTER));
        return publish(topic, payload, 1);
    }

    public boolean isBrokerConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    /**
     * 底层 publish
     */
    private boolean publish(String topic, Object payload, int qos) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warn("MQTT 未连接，丢弃消息: topic={}", topic);
            return false;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            mqttClient.publish(topic, msg);
            log.info("MQTT 下发: topic={}, payload={}", topic, json);
            return true;
        } catch (Exception e) {
            log.error("MQTT publish 失败: topic={}", topic, e);
            return false;
        }
    }

    // ==================== 消息路由 ====================

    @SuppressWarnings("unchecked")
    private void handleMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.info("MQTT 收到消息: topic={}, payload={}", topic, payload);

            String[] parts = topic.split("/");
            if (parts.length != 3) {
                log.warn("MQTT 消息 topic 格式错误: {}", topic);
                return;
            }
            String prefix = parts[0];
            String deviceSn = parts[1];
            String type = parts[2];

            if (payload == null || payload.isBlank() || !payload.trim().startsWith("{")) {
                log.warn("MQTT 消息不是 JSON，已丢弃: topic={}, payload={}", topic, payload);
                return;
            }
            Map<String, Object> data = OBJECT_MAPPER.readValue(payload, Map.class);

            if ("smart-greenhouse".equals(prefix)) {
                // topic 中的 SN 优先，避免模拟器漏带 deviceSn
                data.putIfAbsent("deviceSn", deviceSn);
                handleGreenhouseMessage(type, data);
                return;
            }

            switch (type) {
                case "status" -> handleStatusCallback(deviceSn, data);
                case "light" -> handleLightReport(deviceSn, data);
                case "alarm" -> handleAlarmReport(deviceSn, data);
                default -> log.warn("未知的 MQTT 消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("MQTT 消息处理异常: topic={}", topic, e);
        }
    }

    // ==================== 业务处理 ====================

    /**
     * 2.4 状态回传：smart-light/{deviceSn}/status
     * payload: {"deviceSn": "SN001", "status": "ON"}
     */
    private void handleStatusCallback(String deviceSn, Map<String, Object> data) {
        Long deviceId = resolveDeviceId(deviceSn);
        if (deviceId == null) return;

        String status = (String) data.get("status");
        if (status == null || status.isBlank()) {
            log.warn("状态回传缺少 status 字段: deviceSn={}", deviceSn);
            return;
        }
        devicesService.updateDeviceStatus(deviceId, status);
    }

    /**
     * 3.4 光照上报：smart-light/{deviceSn}/light
     * payload: {"deviceSn": "SN001", "lightIntensity": 850.5}
     * 阈值自动开关指令由 reportReading() 内部通过 MQTT 下发
     */
    private void handleLightReport(String deviceSn, Map<String, Object> data) {
        Long deviceId = resolveDeviceId(deviceSn);
        if (deviceId == null) return;

        BigDecimal lightIntensity = data.get("lightIntensity") != null
                ? new BigDecimal(data.get("lightIntensity").toString()) : null;

        lightReadingsService.reportReading(deviceId, lightIntensity);
    }

    /**
     * 4.5 告警上报：smart-light/{deviceSn}/alarm
     * payload: {"deviceSn": "SN001", "alarmType": "LIGHT_ABNORMAL", "message": "..."}
     */
    private void handleAlarmReport(String deviceSn, Map<String, Object> data) {
        Long deviceId = resolveDeviceId(deviceSn);
        if (deviceId == null) return;

        String alarmType = (String) data.get("alarmType");
        String message = (String) data.get("message");

        if (alarmType == null || alarmType.isBlank()) {
            log.warn("告警上报缺少 alarmType 字段: deviceSn={}", deviceSn);
            return;
        }
        alarmLogsService.createAlarm(deviceId, alarmType, message);
    }

    private void handleGreenhouseMessage(String type, Map<String, Object> data) {
        if (greenhouseService == null) {
            log.warn("光棚服务未就绪，丢弃 MQTT: type={}", type);
            return;
        }
        switch (type) {
            case "telemetry" -> greenhouseService.ingestTelemetry(data);
            case "status" -> greenhouseService.ingestStatus(data);
            case "alarm" -> greenhouseService.ingestAlarm(data);
            default -> log.warn("未知光棚 MQTT 类型: {}", type);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 通过 deviceSn 查询 deviceId
     */
    private Long resolveDeviceId(String deviceSn) {
        Devices device = devicesMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Devices>()
                        .eq(Devices::getDeviceSn, deviceSn));
        if (device == null) {
            log.warn("未找到设备: deviceSn={}，消息已丢弃", deviceSn);
            return null;
        }
        return device.getId();
    }
}
