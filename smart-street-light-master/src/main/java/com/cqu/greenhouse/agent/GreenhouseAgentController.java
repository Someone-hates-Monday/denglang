package com.cqu.greenhouse.agent;

import com.cqu.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智慧光棚顾问智能体 — 只读问答
 */
@Slf4j
@RestController
@RequestMapping("/greenhouse/agent")
@RequiredArgsConstructor
public class GreenhouseAgentController {

    private final GreenhouseAgentOrchestrator orchestrator;

    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return Result.fail("消息不能为空");
        }
        try {
            AgentChatResponse resp = orchestrator.chat(request);
            log.info("agent chat session={} mode={} tools={}",
                    resp.getSessionId(), resp.getMode(), resp.getToolsUsed());
            return Result.success(resp);
        } catch (Exception e) {
            log.error("agent chat failed: {}", e.getMessage(), e);
            return Result.fail("顾问问答异常：" + e.getMessage());
        }
    }
}
