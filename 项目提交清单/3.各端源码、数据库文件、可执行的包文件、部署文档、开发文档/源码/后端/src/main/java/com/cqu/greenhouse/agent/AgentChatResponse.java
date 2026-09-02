package com.cqu.greenhouse.agent;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class AgentChatResponse {
    private String sessionId;
    private String reply;
    private List<String> toolsUsed = new ArrayList<>();
    private List<Citation> citations = new ArrayList<>();
    private Map<String, Object> snapshot;
    /** llm | template | knowledge */
    private String mode;

    @Data
    @Accessors(chain = true)
    public static class Citation {
        private String title;
        private String source;
    }
}
