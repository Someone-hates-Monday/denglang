package com.cqu.greenhouse.agent;

import com.cqu.security.RoleCodes;
import com.cqu.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 规则路由优先的只读顾问编排：工具拉数 → 知识检索 → LLM 或模板答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GreenhouseAgentOrchestrator {

    private final GreenhouseAgentTools tools;
    private final GreenhouseAgentSessionStore sessions;
    private final GreenhouseLlmClient llmClient;

    public AgentChatResponse chat(AgentChatRequest req) {
        String message = req.getMessage() != null ? req.getMessage().trim() : "";
        if (message.isEmpty()) {
            return new AgentChatResponse()
                    .setSessionId(sessions.ensureSession(req.getSessionId()))
                    .setReply("请输入你想了解的棚况、工单或规程问题。")
                    .setMode("template");
        }

        String sessionId = sessions.ensureSession(req.getSessionId());
        String zoneId = (req.getZoneId() != null && !req.getZoneId().isBlank())
                ? req.getZoneId().trim()
                : "ZONE-A";
        String role = RoleCodes.normalize(UserHolder.getRole() != null ? UserHolder.getRole() : RoleCodes.GROWER);

        Set<String> toolsUsed = new LinkedHashSet<>();
        List<AgentChatResponse.Citation> citations = new ArrayList<>();
        Map<String, Object> toolBundle = new LinkedHashMap<>();

        Map<String, Object> snapshot = tools.buildSnapshot(zoneId);
        toolsUsed.add("snapshot");
        toolBundle.put("snapshot", snapshot);

        Intent intent = classify(message);
        runTools(intent, zoneId, message, toolsUsed, toolBundle, citations);

        String system = buildSystemPrompt(role);
        String userPayload = buildUserPayload(message, zoneId, role, toolBundle, citations);

        String reply;
        String mode;
        if (llmClient.isConfigured()) {
            try {
                List<Map<String, String>> hist = new ArrayList<>();
                for (GreenhouseAgentSessionStore.Turn t : sessions.history(sessionId)) {
                    hist.add(Map.of("role", t.role(), "content", t.content()));
                }
                reply = llmClient.chat(system, hist, userPayload);
                mode = "llm";
            } catch (Exception e) {
                log.warn("LLM 失败，回退模板: {}", e.getMessage());
                reply = templateAnswer(intent, message, toolBundle, citations, role);
                mode = "template";
            }
        } else {
            reply = templateAnswer(intent, message, toolBundle, citations, role);
            mode = citations.isEmpty() && intent == Intent.KNOWLEDGE ? "knowledge" : "template";
            if (!citations.isEmpty() && intent == Intent.KNOWLEDGE) {
                mode = "knowledge";
            }
        }

        sessions.append(sessionId, "user", message);
        sessions.append(sessionId, "assistant", reply);

        return new AgentChatResponse()
                .setSessionId(sessionId)
                .setReply(reply)
                .setToolsUsed(new ArrayList<>(toolsUsed))
                .setCitations(citations)
                .setSnapshot(snapshot)
                .setMode(mode);
    }

    private enum Intent {
        LIVE, ECONOMICS, WORK_ORDERS, ALARMS, DEVICES, RECIPE, KNOWLEDGE, DECISION, GREETING, GENERAL
    }

    private static final Pattern GREETING = Pattern.compile("你好|您好|hello|hi|在吗", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECONOMICS = Pattern.compile("电费|产量|economics|能耗|性价比|advice", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORK_ORDERS = Pattern.compile("工单|审批|pending|claim|approve|待批|接单", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALARMS = Pattern.compile("告警|报警|alarm|离线|欠光告警|过光告警", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICES = Pattern.compile("设备|灯具|测点|传感器|online|\\bsn\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECIPE = Pattern.compile("配方|recipe|硬限|目标带|绑定", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIVE_WORD = Pattern.compile("实时|当前|现在|最新|此刻|目前");
    private static final Pattern LIGHT_WORD = Pattern.compile("ppfd|dli|光|遮阳|湿度|温度|光照|环境", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECISION = Pattern.compile("要不要|建议|决策|怎么办|如何控|补不补|降不降");
    private static final Pattern KNOWLEDGE = Pattern.compile("是什么|什么叫|定义|解释|怎么|如何|多大|角色|mqtt|接入|规程|石斛|草莓|金线莲|工单状态", Pattern.CASE_INSENSITIVE);
    private static final Pattern KNOWLEDGE_HINT = Pattern.compile("石斛|草莓|金线莲|dli|配方|工单|mqtt|角色|棚|尺寸", Pattern.CASE_INSENSITIVE);

    private Intent classify(String q) {
        if (GREETING.matcher(q).find()) {
            return Intent.GREETING;
        }
        if (ECONOMICS.matcher(q).find()) {
            return Intent.ECONOMICS;
        }
        if (WORK_ORDERS.matcher(q).find()) {
            return Intent.WORK_ORDERS;
        }
        if (ALARMS.matcher(q).find()) {
            return Intent.ALARMS;
        }
        if (DEVICES.matcher(q).find()) {
            return Intent.DEVICES;
        }
        if (RECIPE.matcher(q).find()) {
            return Intent.RECIPE;
        }
        if (LIVE_WORD.matcher(q).find() && LIGHT_WORD.matcher(q).find()) {
            return Intent.LIVE;
        }
        if (DECISION.matcher(q).find()) {
            return Intent.DECISION;
        }
        if (KNOWLEDGE.matcher(q).find()) {
            return Intent.KNOWLEDGE;
        }
        return Intent.GENERAL;
    }

    private void runTools(Intent intent, String zoneId, String message,
                          Set<String> toolsUsed, Map<String, Object> toolBundle,
                          List<AgentChatResponse.Citation> citations) {
        switch (intent) {
            case LIVE -> {
                toolBundle.put("zoneLight", tools.getZoneLight(zoneId));
                toolsUsed.add("get_zone_light");
            }
            case ECONOMICS -> {
                toolBundle.put("economics", tools.getEconomics(zoneId));
                toolsUsed.add("get_economics");
            }
            case WORK_ORDERS -> {
                toolBundle.put("workOrdersPending", tools.listWorkOrders("PENDING"));
                toolBundle.put("workOrdersApproved", tools.listWorkOrders("APPROVED"));
                toolsUsed.add("list_work_orders");
            }
            case ALARMS -> {
                toolBundle.put("alarms", tools.listAlarms("ACTIVE", 20));
                toolsUsed.add("list_alarms");
            }
            case DEVICES -> {
                toolBundle.put("devices", tools.listDevices(null));
                toolsUsed.add("list_devices");
            }
            case RECIPE -> {
                toolBundle.put("recipe", tools.getRecipe(zoneId, null));
                toolsUsed.add("get_recipe");
            }
            case DECISION, GENERAL -> {
                toolBundle.put("zoneLight", tools.getZoneLight(zoneId));
                toolsUsed.add("get_zone_light");
                toolBundle.put("economics", tools.getEconomics(zoneId));
                toolsUsed.add("get_economics");
                toolBundle.put("workOrdersPending", tools.listWorkOrders("PENDING"));
                toolsUsed.add("list_work_orders");
            }
            case KNOWLEDGE -> { /* knowledge below */ }
            case GREETING -> { /* snapshot only */ }
        }

        boolean needKnowledge = intent == Intent.KNOWLEDGE
                || intent == Intent.GENERAL
                || intent == Intent.DECISION
                || KNOWLEDGE_HINT.matcher(message).find();
        if (needKnowledge) {
            List<KnowledgeChunk> chunks = tools.searchKnowledge(message, 4);
            toolsUsed.add("search_knowledge");
            toolBundle.put("knowledge", chunks.stream().map(c -> Map.of(
                    "title", c.getTitle(),
                    "source", c.getSource(),
                    "content", c.getContent()
            )).toList());
            for (KnowledgeChunk c : chunks) {
                citations.add(new AgentChatResponse.Citation()
                        .setTitle(c.getTitle())
                        .setSource(c.getSource()));
            }
        }
    }

    private String buildSystemPrompt(String role) {
        return """
                你是「智慧光棚」只读顾问智能体。回答简洁、用中文、分点。
                硬性规则：
                1) 只读：不得声称已调光/遮阳/批准/接单；需要执行时引导用户到工单页或冠层光场页。
                2) 数字优先采用「工具结果」；没有工具数据时明确说未知。
                3) economics.yieldIndex 是 DLI 达成率演示指标，不是千克产量；电费为估算。
                4) 过光：先降补光，硬限再用遮阳；欠光：先开遮阳拿免费日光，再升补光。
                5) 3D 演示为整跨铁皮石斛；ZONE-A/B 是半跨控光分区。
                """ + roleSlice(role);
    }

    private String roleSlice(String role) {
        return switch (role) {
            case RoleCodes.SITE_MANAGER -> "\n当前用户角色：场长。侧重全局摘要、电费/产量指数、PENDING 积压与策略建议。";
            case RoleCodes.AGRONOMIST -> "\n当前用户角色：农艺师。侧重配方硬限、偏离目标带、审批建议（不代替 approve）。";
            case RoleCodes.GROWER -> "\n当前用户角色：种植员。侧重待接单工单、现场确认要点（不代替 claim）。";
            case RoleCodes.DEVICE_OPS -> "\n当前用户角色：设备运维。侧重设备在线、告警、测点/灯/遮阳状态。";
            case RoleCodes.TRAINEE -> "\n当前用户角色：学员。侧重教学解释；不要完整罗列演示账号口令。";
            case RoleCodes.SYS_ADMIN -> "\n当前用户角色：系统管理员。可说明仿真/端口/接入；仍保持只读。";
            default -> "\n当前用户角色：" + role + "。";
        };
    }

    private String buildUserPayload(String message, String zoneId, String role,
                                    Map<String, Object> toolBundle,
                                    List<AgentChatResponse.Citation> citations) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户角色：").append(role).append('\n');
        sb.append("关注分区：").append(zoneId).append('\n');
        sb.append("用户问题：").append(message).append("\n\n");
        sb.append("【工具结果 JSON】\n").append(tools.toJson(toolBundle)).append('\n');
        if (!citations.isEmpty()) {
            sb.append("\n【知识来源】");
            for (AgentChatResponse.Citation c : citations) {
                sb.append(c.getTitle()).append("(").append(c.getSource()).append("); ");
            }
        }
        return sb.toString();
    }

    private String templateAnswer(Intent intent, String message, Map<String, Object> toolBundle,
                                  List<AgentChatResponse.Citation> citations, String role) {
        if (intent == Intent.GREETING) {
            return "你好，我是智慧光棚顾问（只读）。可以问当前 PPFD/电费预期、工单积压、告警设备，或石斛配方/工单规则等规程问题。需要调光或接单请到冠层页/工单页操作。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(summarizeTools(toolBundle));

        @SuppressWarnings("unchecked")
        Object knowledgeObj = toolBundle.get("knowledge");
        if (knowledgeObj instanceof List<?> knowledge && !knowledge.isEmpty()) {
            sb.append("\n\n相关知识：\n");
            for (Object item : knowledge) {
                if (item instanceof Map<?, ?> k) {
                    sb.append("· ").append(k.get("title")).append("：").append(k.get("content")).append('\n');
                }
            }
        }

        if (intent == Intent.DECISION) {
            sb.append("\n建议（只读）：结合上方有效光与 economics.adviceZh；大动作请走农艺审批→种植员 claim，我不会直接下发。");
        }

        if (citations.isEmpty()
                && (!(knowledgeObj instanceof List<?> kl) || kl.isEmpty())
                && intent == Intent.KNOWLEDGE) {
            sb.append("未命中规程条目。可问：石斛/草莓/金线莲配方、DLI、工单状态机、棚体尺寸、MQTT 接入、角色权限。");
        }

        if (RoleCodes.TRAINEE.equals(role) && message.contains("密码")) {
            sb.append("\n（学员视图：请向指导教师索取演示账号，此处不展开口令。）");
        }

        String out = sb.toString().trim();
        return out.isEmpty()
                ? "暂无足够数据。请确认后端仿真已运行，或换个问法（例如「当前 ZONE-A PPFD」「待批工单」「石斛光配方」）。"
                : out;
    }

    @SuppressWarnings("unchecked")
    private String summarizeTools(Map<String, Object> toolBundle) {
        StringBuilder sb = new StringBuilder();
        Object light = toolBundle.get("zoneLight");
        if (light instanceof Map<?, ?> m) {
            sb.append("棚况（工具 get_zone_light）：\n");
            sb.append("· 区 ").append(m.get("zoneId"))
                    .append(" 有效 PPFD ").append(fmt(m.get("effectivePpfd")))
                    .append(" · DLI ").append(fmt(m.get("dliSoFar")))
                    .append(" · 遮阳开度 ").append(fmt(m.get("shadeOpenPercent"))).append("%\n");
            if (m.get("temperatureC") != null || m.get("humidityPct") != null) {
                sb.append("· 温湿度 ").append(fmt(m.get("temperatureC"))).append("°C / ")
                        .append(fmt(m.get("humidityPct"))).append("%\n");
            }
        }
        Object econWrap = toolBundle.get("economics");
        if (econWrap instanceof Map<?, ?> ew) {
            Object econ = ew.get("economics");
            sb.append("经济性（get_economics）：\n");
            if (econ instanceof Map<?, ?> e) {
                sb.append("· 产量指数(DLI达成) ").append(fmt(e.get("yieldIndex")))
                        .append(" · 电费估 ¥").append(fmt(e.get("energyCostYuanEst"))).append('\n');
                if (e.get("adviceZh") != null) {
                    sb.append("· 建议 ").append(e.get("adviceZh")).append('\n');
                }
            } else {
                sb.append("· ").append(ew.get("note")).append('\n');
            }
        }
        Object pending = toolBundle.get("workOrdersPending");
        if (pending instanceof List<?> list) {
            sb.append("待批工单 ").append(list.size()).append(" 条");
            if (!list.isEmpty()) {
                sb.append("：");
                int n = 0;
                for (Object o : list) {
                    if (n++ >= 5) {
                        sb.append("…");
                        break;
                    }
                    if (o instanceof Map<?, ?> wo) {
                        sb.append("#").append(wo.get("id")).append("(").append(wo.get("reason")).append(") ");
                    }
                }
            }
            sb.append('\n');
        }
        Object approved = toolBundle.get("workOrdersApproved");
        if (approved instanceof List<?> list && !list.isEmpty()) {
            sb.append("已批待接单 ").append(list.size()).append(" 条（请种植员在工单页 claim）。\n");
        }
        Object alarms = toolBundle.get("alarms");
        if (alarms instanceof List<?> list) {
            sb.append("活跃告警 ").append(list.size()).append(" 条");
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?> a) {
                sb.append("，例：").append(a.get("alarmType")).append(" ").append(a.get("message"));
            }
            sb.append('\n');
        }
        Object devices = toolBundle.get("devices");
        if (devices instanceof List<?> list) {
            long offline = list.stream()
                    .filter(o -> o instanceof Map<?, ?> d
                            && !"ONLINE".equalsIgnoreCase(String.valueOf(((Map<?, ?>) d).get("onlineStatus"))))
                    .count();
            sb.append("设备 ").append(list.size()).append(" 台，非 ONLINE 约 ").append(offline).append(" 台。\n");
        }
        Object recipe = toolBundle.get("recipe");
        if (recipe instanceof Map<?, ?> rm) {
            Object r = rm.get("recipe");
            if (r instanceof Map<?, ?> rec) {
                sb.append("配方 ").append(rec.get("cropNameZh")).append(" ").append(rec.get("stage"))
                        .append(" 目标 ").append(fmt(rec.get("ppfdTargetMin"))).append("–")
                        .append(fmt(rec.get("ppfdTargetMax"))).append(" PPFD\n");
            }
        }
        Object snap = toolBundle.get("snapshot");
        if (sb.isEmpty() && snap instanceof Map<?, ?> sm) {
            sb.append("快照：PENDING 工单 ").append(sm.get("pendingWorkOrderCount")).append("；");
            Object zones = sm.get("zones");
            if (zones instanceof List<?> zl) {
                for (Object z : zl) {
                    if (z instanceof Map<?, ?> zm) {
                        sb.append(zm.get("zoneId")).append(" PPFD=").append(fmt(zm.get("effectivePpfd"))).append(" ");
                    }
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String fmt(Object o) {
        if (o == null) return "—";
        if (o instanceof Number n) {
            double d = n.doubleValue();
            if (Math.abs(d - Math.rint(d)) < 1e-6) return String.valueOf((long) Math.rint(d));
            return String.format(Locale.ROOT, "%.1f", d);
        }
        return o.toString();
    }
}
