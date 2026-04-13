package com.sre.agent.skill.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SkillMarkdownParser {

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---", Pattern.DOTALL);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)\\s*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern STEP_HEADER_PATTERN = Pattern.compile("^##\\s+Step\\s+(\\d+):\\s*(.*)", Pattern.MULTILINE);
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("^#\\s*(\\w[\\w_-]*):\\s*(.+)$", Pattern.MULTILINE);

    public ParsedSkill parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("Skill markdown content is empty");
        }

        ParsedSkill.SkillMetadata metadata = parseFrontmatter(markdown);
        List<SkillBlock> blocks = parseBlocks(markdown);

        log.debug("Parsed skill '{}': {} blocks", metadata.id(), blocks.size());
        return new ParsedSkill(metadata, blocks);
    }

    private ParsedSkill.SkillMetadata parseFrontmatter(String markdown) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(markdown);
        if (!matcher.find()) {
            return new ParsedSkill.SkillMetadata(
                    "unnamed", "Unnamed Skill", "", "1.0.0", "unknown", "tenant",
                    List.of(), List.of(), List.of(), Map.of());
        }

        String yaml = matcher.group(1);
        Map<String, String> fields = parseSimpleYaml(yaml);

        List<ParsedSkill.SkillInput> inputs = parseInputs(yaml);
        List<String> requiredPlugins = parseList(fields.getOrDefault("requires.plugins", ""));
        List<String> requiredPermissions = parseList(fields.getOrDefault("requires.permissions", ""));

        return new ParsedSkill.SkillMetadata(
                fields.getOrDefault("id", "unnamed"),
                fields.getOrDefault("name", "Unnamed Skill"),
                fields.getOrDefault("description", ""),
                fields.getOrDefault("version", "1.0.0"),
                fields.getOrDefault("author", "unknown"),
                fields.getOrDefault("source", "tenant"),
                requiredPlugins,
                requiredPermissions,
                inputs,
                Map.of()
        );
    }

    private List<SkillBlock> parseBlocks(String markdown) {
        // Remove frontmatter
        String content = FRONTMATTER_PATTERN.matcher(markdown).replaceFirst("").trim();

        List<SkillBlock> blocks = new ArrayList<>();
        int stepNumber = 0;

        // Split by step headers and find code blocks within each step
        String[] sections = content.split("(?=^## Step )", Pattern.UNICODE_CHARACTER_CLASS);

        for (String section : sections) {
            Matcher stepMatcher = STEP_HEADER_PATTERN.matcher(section);
            String stepTitle = "";
            if (stepMatcher.find()) {
                stepNumber = Integer.parseInt(stepMatcher.group(1));
                stepTitle = stepMatcher.group(2).trim();
            }

            Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(section);
            while (codeBlockMatcher.find()) {
                String language = codeBlockMatcher.group(1).toLowerCase();
                String code = codeBlockMatcher.group(2).trim();

                SkillBlock.BlockType blockType = mapLanguageToBlockType(language);
                Map<String, String> annotations = extractAnnotations(code);
                String cleanCode = stripAnnotations(code);

                if (stepNumber == 0) stepNumber = blocks.size() + 1;

                blocks.add(new SkillBlock(
                        stepNumber,
                        stepTitle.isEmpty() ? "Step " + stepNumber : stepTitle,
                        blockType,
                        cleanCode,
                        annotations
                ));
            }
        }

        // If no step headers, parse all code blocks sequentially
        if (blocks.isEmpty()) {
            Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(content);
            int seq = 1;
            while (codeBlockMatcher.find()) {
                String language = codeBlockMatcher.group(1).toLowerCase();
                String code = codeBlockMatcher.group(2).trim();

                SkillBlock.BlockType blockType = mapLanguageToBlockType(language);
                Map<String, String> annotations = extractAnnotations(code);
                String cleanCode = stripAnnotations(code);

                blocks.add(new SkillBlock(seq++, "Step " + (seq - 1), blockType, cleanCode, annotations));
            }
        }

        return blocks;
    }

    private SkillBlock.BlockType mapLanguageToBlockType(String language) {
        return switch (language) {
            case "sql" -> SkillBlock.BlockType.SQL;
            case "bash", "sh", "shell" -> SkillBlock.BlockType.BASH;
            case "python", "py" -> SkillBlock.BlockType.PYTHON;
            case "promql" -> SkillBlock.BlockType.PROMQL;
            case "prompt" -> SkillBlock.BlockType.PROMPT;
            case "http", "curl" -> SkillBlock.BlockType.HTTP;
            case "skill" -> SkillBlock.BlockType.SKILL;
            default -> SkillBlock.BlockType.BASH;
        };
    }

    private Map<String, String> extractAnnotations(String code) {
        Map<String, String> annotations = new LinkedHashMap<>();
        Matcher annotMatcher = ANNOTATION_PATTERN.matcher(code);
        while (annotMatcher.find()) {
            String key = annotMatcher.group(1).trim();
            String value = annotMatcher.group(2).trim();
            // Only treat lines at the START of the code block as annotations
            if (code.indexOf(annotMatcher.group()) < code.indexOf('\n', code.indexOf('\n') + 1) + 200) {
                annotations.put(key, value);
            }
        }
        return annotations;
    }

    private String stripAnnotations(String code) {
        StringBuilder cleaned = new StringBuilder();
        boolean inAnnotationHeader = true;
        for (String line : code.split("\n")) {
            if (inAnnotationHeader && line.matches("^#\\s+\\w[\\w_-]*:.*")) {
                continue; // Skip annotation lines
            }
            inAnnotationHeader = false;
            if (!cleaned.isEmpty()) cleaned.append("\n");
            cleaned.append(line);
        }
        return cleaned.toString().trim();
    }

    private Map<String, String> parseSimpleYaml(String yaml) {
        Map<String, String> fields = new LinkedHashMap<>();
        String currentPrefix = "";

        for (String line : yaml.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            if (trimmed.contains(":")) {
                int colonIdx = trimmed.indexOf(':');
                String key = trimmed.substring(0, colonIdx).trim();
                String value = trimmed.substring(colonIdx + 1).trim();

                if (line.startsWith("  ") && !currentPrefix.isEmpty()) {
                    fields.put(currentPrefix + "." + key, value);
                } else {
                    if (value.isEmpty()) {
                        currentPrefix = key;
                    } else {
                        fields.put(key, value);
                        currentPrefix = "";
                    }
                }
            }
        }
        return fields;
    }

    private List<ParsedSkill.SkillInput> parseInputs(String yaml) {
        // Simplified input parsing
        List<ParsedSkill.SkillInput> inputs = new ArrayList<>();
        boolean inInputs = false;
        String currentName = null;
        String currentType = "string";
        String currentDefault = "";

        for (String line : yaml.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals("inputs:")) { inInputs = true; continue; }
            if (!line.startsWith("  ") && !line.startsWith("\t") && inInputs && !trimmed.startsWith("-")) {
                inInputs = false;
            }
            if (!inInputs) continue;

            if (trimmed.startsWith("- name:")) {
                if (currentName != null) {
                    inputs.add(new ParsedSkill.SkillInput(currentName, currentType, currentDefault));
                }
                currentName = trimmed.replace("- name:", "").trim();
                currentType = "string";
                currentDefault = "";
            } else if (trimmed.startsWith("type:")) {
                currentType = trimmed.replace("type:", "").trim();
            } else if (trimmed.startsWith("default:")) {
                currentDefault = trimmed.replace("default:", "").trim().replaceAll("^\"|\"$", "");
            }
        }
        if (currentName != null) {
            inputs.add(new ParsedSkill.SkillInput(currentName, currentType, currentDefault));
        }
        return inputs;
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.replaceAll("[\\[\\]]", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
