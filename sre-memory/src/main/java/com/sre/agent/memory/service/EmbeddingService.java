package com.sre.agent.memory.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(@Autowired(required = false) EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] generateEmbedding(String tenantId, String text) {
        if (text == null || text.isBlank() || embeddingModel == null) {
            return new float[0];
        }
        try {
            return embeddingModel.embed(text.substring(0, Math.min(text.length(), 8000)));
        } catch (Exception e) {
            log.warn("Embedding generation failed: {}", e.getMessage());
            return new float[0];
        }
    }

    public String toVectorString(float[] embedding) {
        if (embedding == null || embedding.length == 0) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String truncate(String text, int maxChars) {
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }
}
