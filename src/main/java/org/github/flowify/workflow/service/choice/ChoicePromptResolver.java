package org.github.flowify.workflow.service.choice;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.choice.dto.prompt.AiPromptRules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChoicePromptResolver {

    private static final Set<String> PROMPT_NODE_TYPES = Set.of("AI", "AI_FILTER");
    private static final String DEFAULT_RUNTIME_ACTION = "process";

    private final ObjectMapper objectMapper;

    @Value("${app.ai-prompt-rules.path:docs/ai_prompt_rules.json}")
    private String promptRulesPath;

    private AiPromptRules promptRules;

    @PostConstruct
    private void loadPromptRules() {
        try {
            ClassPathResource resource = new ClassPathResource(promptRulesPath);
            try (InputStream is = resource.getInputStream()) {
                promptRules = objectMapper.readValue(is, AiPromptRules.class);
            }
            log.info("Loaded AI prompt rules v{}", promptRules.getVersion());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ai_prompt_rules.json from " + promptRulesPath, e);
        }
    }

    public Map<String, Object> resolve(NodeDefinition node) {
        if (node == null || !isPromptNode(node.getType())) {
            return Map.of();
        }

        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String manualPrompt = asText(config.get("prompt"));
        if (hasText(manualPrompt)) {
            return resolveManualPrompt(config, manualPrompt);
        }

        String choiceActionId = asText(config.get("choiceActionId"));
        if (!hasText(choiceActionId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "AI 노드 '" + node.getId() + "'의 실행 프롬프트를 만들 수 없습니다.");
        }

        String dataType = resolveDataType(node, config);
        String actionPrompt = resolveActionPrompt(dataType, choiceActionId);

        List<String> promptParts = new ArrayList<>();
        addIfHasText(promptParts, promptRules.getBasePrompt());
        addIfHasText(promptParts, getDataTypePrompt(dataType));
        addIfHasText(promptParts, actionPrompt);
        promptParts.addAll(resolveSelectionModifiers(config.get("choiceSelections")));

        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("action", DEFAULT_RUNTIME_ACTION);
        resolved.put("prompt", String.join("\n\n", promptParts));
        resolved.put("prompt_source", "choice_rule");
        return resolved;
    }

    private Map<String, Object> resolveManualPrompt(Map<String, Object> config, String manualPrompt) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        String action = asText(config.get("action"));
        resolved.put("action", hasText(action) ? action : DEFAULT_RUNTIME_ACTION);
        resolved.put("prompt", manualPrompt);
        resolved.put("prompt_source", "manual");
        return resolved;
    }

    private boolean isPromptNode(String nodeType) {
        return nodeType != null && PROMPT_NODE_TYPES.contains(nodeType.toUpperCase());
    }

    private String resolveDataType(NodeDefinition node, Map<String, Object> config) {
        String dataType = firstText(
                node.getDataType(),
                config.get("dataType"),
                config.get("data_type"));
        if (!hasText(dataType)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "AI 노드 '" + node.getId() + "'의 입력 데이터 타입이 필요합니다.");
        }
        return dataType;
    }

    private String resolveActionPrompt(String dataType, String choiceActionId) {
        Map<String, Map<String, String>> actionPrompts = promptRules.getActionPrompts();
        Map<String, String> dataTypeActions = actionPrompts != null ? actionPrompts.get(dataType) : null;
        String actionPrompt = dataTypeActions != null ? dataTypeActions.get(choiceActionId) : null;
        if (!hasText(actionPrompt)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "AI 선택지 '" + dataType + "." + choiceActionId + "'에 대한 프롬프트가 없습니다.");
        }
        return actionPrompt;
    }

    private String getDataTypePrompt(String dataType) {
        Map<String, String> dataTypePrompts = promptRules.getDataTypePrompts();
        return dataTypePrompts != null ? dataTypePrompts.get(dataType) : null;
    }

    private List<String> resolveSelectionModifiers(Object selectionsValue) {
        if (!(selectionsValue instanceof Map<?, ?> selections)) {
            return List.of();
        }

        List<String> modifiers = new ArrayList<>();
        for (Map.Entry<?, ?> entry : selections.entrySet()) {
            String key = asText(entry.getKey());
            Object value = entry.getValue();

            if (key.contains(":")) {
                appendCustomInstruction(modifiers, value);
                continue;
            }

            appendModifierInstructions(modifiers, value);
        }
        return modifiers;
    }

    private void appendModifierInstructions(List<String> modifiers, Object value) {
        if (value instanceof List<?> values) {
            for (Object item : values) {
                appendModifierInstruction(modifiers, item);
            }
            return;
        }
        appendModifierInstruction(modifiers, value);
    }

    private void appendModifierInstruction(List<String> modifiers, Object value) {
        String selectionId = asText(value);
        if (!hasText(selectionId) || "custom".equals(selectionId)) {
            return;
        }

        String modifier = promptRules.getModifiers() != null
                ? promptRules.getModifiers().get(selectionId)
                : null;
        modifiers.add(hasText(modifier) ? modifier : "사용자 선택 조건: " + selectionId);
    }

    private void appendCustomInstruction(List<String> modifiers, Object value) {
        String customInstruction = asText(value);
        if (hasText(customInstruction)) {
            modifiers.add("사용자 추가 요청: " + customInstruction);
        }
    }

    private void addIfHasText(List<String> values, String value) {
        if (hasText(value)) {
            values.add(value.trim());
        }
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = asText(value);
            if (hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String asText(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
