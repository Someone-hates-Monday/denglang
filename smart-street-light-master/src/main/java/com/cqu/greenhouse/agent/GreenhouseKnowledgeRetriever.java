package com.cqu.greenhouse.agent;

import com.cqu.config.LlmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 薄 RAG：默认关键词打分；若配置了 embedding 仍先走关键词（Embedding 调用可选扩展）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreenhouseKnowledgeRetriever {

    private final GreenhouseKnowledgeCorpus corpus;
    private final LlmConfig llmConfig;

    public List<KnowledgeChunk> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int k = topK > 0 ? topK : Math.max(1, llmConfig.getTopK());
        String nq = normalize(query);
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeChunk c : corpus.all()) {
            int score = 0;
            int maxKeyLen = 0;
            for (String key : c.getKeywords()) {
                String nk = normalize(key);
                if (!nk.isEmpty() && nq.contains(nk)) {
                    score += 1 + nk.length() / 4;
                    maxKeyLen = Math.max(maxKeyLen, nk.length());
                }
            }
            String titleN = normalize(c.getTitle());
            if (!titleN.isEmpty() && nq.contains(titleN)) {
                score += 3;
            }
            if (score > 0) {
                scored.add(new Scored(c, score, maxKeyLen));
            }
        }
        scored.sort(Comparator
                .comparingInt((Scored s) -> s.score).reversed()
                .thenComparingInt(s -> s.maxKeyLen).reversed());
        List<KnowledgeChunk> out = new ArrayList<>();
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            out.add(scored.get(i).chunk);
        }
        if (out.isEmpty()) {
            log.debug("知识检索未命中 q={}", query);
        }
        return out;
    }

    private static String normalize(String q) {
        return q.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。！？、,.;:：；!?'\"“”‘’（）()【】\\[\\]<>《》·\\-]+", "");
    }

    private record Scored(KnowledgeChunk chunk, int score, int maxKeyLen) {}
}
