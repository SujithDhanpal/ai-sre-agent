package com.sre.agent.skill.executor;

import com.sre.agent.skill.parser.SkillBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class PromptBlockExecutor implements SkillBlockExecutor {

    private final ChatModel chatModel;

    public PromptBlockExecutor(@Autowired(required = false) ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public SkillBlock.BlockType supportedType() {
        return SkillBlock.BlockType.PROMPT;
    }

    @Override
    public BlockResult execute(SkillBlock block, Map<String, String> variables, ExecutionContext context) {
        long start = System.currentTimeMillis();

        if (chatModel == null) {
            return BlockResult.failure("No ChatModel available — Claude API key not configured", 0);
        }

        String prompt = resolveVariables(block.code(), variables);
        log.debug("Executing prompt skill block");

        try {
            String response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .system("You are an SRE skill executor. Analyze the data provided and respond concisely.")
                    .user(prompt)
                    .call().content();

            long elapsed = System.currentTimeMillis() - start;
            return BlockResult.success(response, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Prompt block execution failed: {}", e.getMessage());
            return BlockResult.failure(e.getMessage(), elapsed);
        }
    }

    private String resolveVariables(String code, Map<String, String> variables) {
        String resolved = code;
        for (var entry : variables.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }
}
