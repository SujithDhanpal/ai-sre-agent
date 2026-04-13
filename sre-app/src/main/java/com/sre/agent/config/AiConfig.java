package com.sre.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures AI models:
 * - Anthropic Claude as PRIMARY ChatModel (for all agent conversations)
 * - OpenAI only for embeddings (text-embedding-3-small)
 */
@Configuration
public class AiConfig {

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel) {
        return anthropicChatModel;
    }
}
