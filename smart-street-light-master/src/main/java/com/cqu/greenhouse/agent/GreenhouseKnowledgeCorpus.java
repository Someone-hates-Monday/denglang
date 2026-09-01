package com.cqu.greenhouse.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 光棚规程/作物/角色静态语料（内存种子；不依赖 Embedding）
 */
@Component
public class GreenhouseKnowledgeCorpus {

    private final List<KnowledgeChunk> chunks = new ArrayList<>();

    public GreenhouseKnowledgeCorpus() {
        add("dendrobium-recipe", "铁皮石斛光配方", "contracts/light-recipe.md",
                "铁皮石斛分阶段：组培 TISSUE 目标 60–70、硬限 50–90 PPFD，DLI≈2.2–3.0；"
                        + "栽培 CULTIVATION 目标 90–120、硬限 70–140，DLI≈3.9–5.2。"
                        + "欠光先开遮阳再补光；过光先降补光，硬限再用遮阳粗档。",
                "石斛", "组培", "栽培", "光配方", "ppfd", "硬限");
        add("strawberry-recipe", "设施草莓光配方", "CROP-ECONOMICS-STANDARDS-REF.md",
                "设施草莓目标 PPFD 约 250–400，DLI 约 17–25。"
                        + "LED 补光相对对照可增产约 33–56%。演示主叙事为整跨石斛，草莓作配方切换知识。",
                "草莓", "dli", "补光", "产量");
        add("anoectochilus-recipe", "台湾金线莲光配方", "contracts/light-recipe.md",
                "金线莲耐阴：目标 PPFD 约 25–35，硬限 15–55，DLI 约 1.3–1.8。适合配方切换对照。",
                "金线莲", "耐阴", "ppfd");
        add("dli-concept", "DLI 光日积分", "contracts/light-recipe.md",
                "DLI（Daily Light Integral）= 一天冠层光量子总量，单位 mol·m⁻²·d⁻¹。"
                        + "估算：DLI ≈ PPFD × 光周期(h) × 0.0036。economics 的 yieldIndex 是 DLI 达成率，不是千克产量。",
                "dli", "日积分", "光积分", "产量指数", "yield");
        add("economics", "产量指数与电费口径", "CROP-ECONOMICS-STANDARDS-REF.md",
                "LightEconomics：产量指数≈当日 DLI 相对目标达成率（演示算法）；"
                        + "电费估用电价示意常数与灯功率估算，非真实账单。"
                        + "建议：欠光先开遮阳拿免费光；过光先降灯勿急关遮阳。",
                "电费", "economics", "产量", "能耗", "性价比");
        add("layout", "棚体尺寸与分区", "GREENHOUSE-LAYOUT.md",
                "cq-demo-bay-v1：净长 16m×净宽 7m，檐高 2.8、脊高 3.8。"
                        + "ZONE-A/B 为半跨控光分区；3D 演示整跨铁皮石斛不混种。"
                        + "西南角原点，+X 东 +Y 北。",
                "大棚", "尺寸", "分区", "zone", "布局", "棚体");
        add("lighting-v13", "补光灯排布 v1.3", "LIGHTING-UPGRADE-v1.3.md",
                "每床 3 灯，灯心 Z≈1.85m，光束半角约 55°，分床调光；外遮阳半跨 SHADE-ZONE-A/B。",
                "布灯", "灯", "分床", "遮阳", "shade");
        add("workorder", "工单状态机", "RBAC-ROLES.md",
                "PENDING→APPROVED→IN_PROGRESS→COMPLETED，可 REJECTED。"
                        + "approve 只批准不下发；种植员 claim 才下发并完成。大开度变更进工单审计。",
                "工单", "审批", "approve", "claim", "pending", "状态机");
        add("rbac", "六角色与演示账号", "RBAC-ROLES.md",
                "场长 SITE_MANAGER、农艺 AGRONOMIST、种植员 GROWER、运维 DEVICE_OPS、学员 TRAINEE、系统 SYS_ADMIN。"
                        + "演示：admin/admin123；changzhang、nongyi、zhongzhi、yunwei、xueyuan / demo123。",
                "角色", "权限", "账号", "rbac", "登录");
        add("mqtt", "MQTT 接入", "contracts/mqtt.md",
                "前缀 smart-greenhouse/：telemetry / status / alarm 上行，command 下行。"
                        + "Broker EMQX :1883。调光 SET_DIMMING，遮阳 SET_OPEN_PERCENT。",
                "mqtt", "接入", "topic", "遥测", "指令");
        add("control-rules", "控光规则", "CONFORMANCE.md",
                "规则读区有效 PPFD：过硬限先降补光再关遮阳粗档；欠硬限先开遮阳再升补光。"
                        + "目标带内微调；cooldown 防抖。助手只读建议，执行请走工单/冠层页。",
                "规则", "闭环", "自动", "补光", "控光", "过光", "欠光");
        add("ports", "本地端口", "IMPLEMENT.md",
                "Web :5173，API :8080，PG :5433，EMQX :1883，控制台 :18083。",
                "端口", "启动", "本地", "8080", "5173");
    }

    private void add(String id, String title, String source, String content, String... keys) {
        KnowledgeChunk c = new KnowledgeChunk()
                .setId(id)
                .setTitle(title)
                .setSource(source)
                .setContent(content)
                .setKeywords(List.of(keys));
        chunks.add(c);
    }

    public List<KnowledgeChunk> all() {
        return List.copyOf(chunks);
    }
}
