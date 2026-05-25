package org.github.flowify.workflow.service.choice;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.catalog.dto.AiPromptMetadata;
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
        return resolve(node, node != null ? node.getType() : null);
    }

    public Map<String, Object> resolve(NodeDefinition node, String semanticNodeType) {
        if (node == null || !isPromptNode(semanticNodeType)) {
            return Map.of();
        }

        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String manualPrompt = asText(config.get("prompt"));
        String choiceActionId = asText(config.get("choiceActionId"));
        if (!hasText(choiceActionId)) {
            if (hasText(manualPrompt)) {
                return resolveManualPrompt(config, manualPrompt);
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "AI 노드 '" + node.getId() + "'의 실행 프롬프트를 만들 수 없습니다.");
        }

        String dataType = resolveDataTypeCandidate(node, config);
        if (hasText(manualPrompt) && canBuildChoicePrompt(dataType, choiceActionId)) {
            return resolveChoicePrompt(config, dataType, choiceActionId, manualPrompt);
        }
        if (hasText(manualPrompt)) {
            return resolveManualPrompt(config, manualPrompt);
        }

        return resolveChoicePrompt(config, resolveDataType(node, config), choiceActionId, null);
    }

    public boolean hasActionPrompt(String dataType, String choiceActionId) {
        if (!hasText(dataType) || !hasText(choiceActionId) || promptRules == null) {
            return false;
        }

        return hasText(findActionPrompt(dataType, choiceActionId));
    }

    public AiPromptMetadata describe(NodeDefinition node) {
        return describe(node, node != null ? node.getType() : null);
    }

    public AiPromptMetadata describe(NodeDefinition node, String semanticNodeType) {
        if (node == null || !isPromptNode(semanticNodeType)) {
            return null;
        }

        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String manualPrompt = asText(config.get("prompt"));
        String choiceActionId = asText(config.get("choiceActionId"));
        if (!hasText(choiceActionId)) {
            return hasText(manualPrompt) ? buildManualOnlyMetadata() : null;
        }

        String dataType = resolveDataTypeCandidate(node, config);
        if (canBuildChoicePrompt(dataType, choiceActionId)) {
            return buildChoiceMetadata(config, dataType, choiceActionId, hasText(manualPrompt));
        }

        return hasText(manualPrompt) ? buildManualOnlyMetadata() : null;
    }

    private Map<String, Object> resolveManualPrompt(Map<String, Object> config, String manualPrompt) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        String action = asText(config.get("action"));
        resolved.put("action", hasText(action) ? action : DEFAULT_RUNTIME_ACTION);
        resolved.put("prompt", manualPrompt);
        resolved.put("prompt_source", "manual");
        return resolved;
    }

    private AiPromptMetadata buildManualOnlyMetadata() {
        return AiPromptMetadata.builder()
                .available(true)
                .mode("custom")
                .promptSource("manual")
                .customPromptMode("manual_only")
                .basePromptSummary("이 노드는 직접 작성한 프롬프트만 사용합니다.")
                .includedInstructions(List.of(
                        "기본 AI 지시는 포함되지 않습니다.",
                        "직접 작성한 프롬프트가 그대로 실행에 사용됩니다."))
                .build();
    }

    private AiPromptMetadata buildChoiceMetadata(
            Map<String, Object> config,
            String dataType,
            String choiceActionId,
            boolean hasManualPrompt
    ) {
        List<String> includedInstructions = new ArrayList<>();
        addIfHasText(includedInstructions, "입력 유형: " + labelForDataType(dataType));
        includedInstructions.addAll(resolveActionInstructionSummaries(dataType, choiceActionId));
        includedInstructions.addAll(resolveSelectionInstructionSummaries(config.get("choiceSelections")));
        if (includedInstructions.isEmpty()) {
            includedInstructions.add("선택한 AI 작업 기준으로 기본 지시가 적용됩니다.");
        }

        return AiPromptMetadata.builder()
                .available(true)
                .mode(hasManualPrompt ? "custom" : "recommended")
                .promptSource(hasManualPrompt ? "choice_rule_augmented" : "choice_rule")
                .customPromptMode(hasManualPrompt ? "append" : null)
                .choiceActionId(choiceActionId)
                .basePromptSummary(resolveBasePromptSummary(dataType, choiceActionId))
                .includedInstructions(List.copyOf(includedInstructions))
                .build();
    }

    private Map<String, Object> resolveChoicePrompt(
            Map<String, Object> config,
            String dataType,
            String choiceActionId,
            String manualPrompt
    ) {
        String actionPrompt = resolveActionPrompt(dataType, choiceActionId);

        List<String> promptParts = new ArrayList<>();
        addIfHasText(promptParts, promptRules.getBasePrompt());
        addIfHasText(promptParts, getDataTypePrompt(dataType));
        addIfHasText(promptParts, actionPrompt);
        promptParts.addAll(resolveSelectionModifiers(config.get("choiceSelections")));
        appendManualPromptSuffix(promptParts, manualPrompt);

        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("action", DEFAULT_RUNTIME_ACTION);
        resolved.put("prompt", String.join("\n\n", promptParts));
        resolved.put("prompt_source", hasText(manualPrompt) ? "choice_rule_augmented" : "choice_rule");
        return resolved;
    }

    private boolean isPromptNode(String nodeType) {
        return nodeType != null && PROMPT_NODE_TYPES.contains(nodeType.toUpperCase());
    }

    private String resolveDataType(NodeDefinition node, Map<String, Object> config) {
        String dataType = resolveDataTypeCandidate(node, config);
        if (!hasText(dataType)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "AI 노드 '" + node.getId() + "'의 입력 데이터 타입이 필요합니다.");
        }
        return dataType;
    }

    private String resolveDataTypeCandidate(NodeDefinition node, Map<String, Object> config) {
        return firstText(
                node.getDataType(),
                config.get("dataType"),
                config.get("data_type"));
    }

    private String resolveActionPrompt(String dataType, String choiceActionId) {
        String actionPrompt = findActionPrompt(dataType, choiceActionId);
        if (!hasText(actionPrompt)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "AI 선택지 '" + dataType + "." + choiceActionId + "'에 대한 프롬프트가 없습니다.");
        }
        return actionPrompt;
    }

    private String findActionPrompt(String dataType, String choiceActionId) {
        Map<String, Map<String, String>> actionPrompts = promptRules.getActionPrompts();
        Map<String, String> dataTypeActions = actionPrompts != null ? actionPrompts.get(dataType) : null;
        return dataTypeActions != null ? dataTypeActions.get(choiceActionId) : null;
    }

    private String getDataTypePrompt(String dataType) {
        Map<String, String> dataTypePrompts = promptRules.getDataTypePrompts();
        return dataTypePrompts != null ? dataTypePrompts.get(dataType) : null;
    }

    private boolean canBuildChoicePrompt(String dataType, String choiceActionId) {
        return hasText(dataType) && hasActionPrompt(dataType, choiceActionId);
    }

    private String resolveBasePromptSummary(String dataType, String choiceActionId) {
        return switch (choiceActionId) {
            case "summarize" -> switch (dataType) {
                case "SINGLE_EMAIL" -> "이메일 내용을 요약하고 중요 일정과 후속 액션을 정리합니다.";
                case "SINGLE_FILE" -> "파일 내용을 요약하고 핵심 포인트를 구조화해 정리합니다.";
                default -> "입력 내용을 요약하고 핵심 포인트를 정리합니다.";
            };
            case "ai_summarize" -> switch (dataType) {
                case "ARTICLE_LIST" -> "기사나 게시글 목록을 요약하고 공통 흐름을 정리합니다.";
                default -> "입력 텍스트를 요약해 핵심 내용을 정리합니다.";
            };
            case "ai_refine" -> "기존 내용을 유지하면서 문장과 구조를 더 읽기 좋게 다듬습니다.";
            case "translate" -> "입력 내용을 자연스럽게 번역합니다.";
            case "extract_info" -> "입력에서 필요한 정보만 추출해 항목별로 정리합니다.";
            case "describe_image" -> "이미지 내용을 설명하고 시각 정보를 정리합니다.";
            case "ocr" -> "이미지나 파일에서 텍스트를 추출해 정리합니다.";
            case "classify_intent" -> "입력의 의도나 주제를 분류합니다.";
            case "sentiment" -> "입력의 감정 성향을 분류합니다.";
            case "urgency" -> "입력의 긴급도를 판단합니다.";
            case "extract_todos" -> "입력에서 해야 할 일과 확인 사항을 추출합니다.";
            case "draft_reply" -> "입력 내용을 바탕으로 답장 초안을 작성합니다.";
            case "ai_generate" -> "입력 데이터를 바탕으로 맞춤 초안이나 문서를 생성합니다.";
            case "ai_analyze" -> "입력 데이터를 분석하고 주요 인사이트를 정리합니다.";
            case "ai_filter" -> "조건에 맞는 항목만 선별합니다.";
            case "merge" -> "여러 입력 항목을 하나의 결과로 통합합니다.";
            default -> "선택한 AI 작업을 입력 데이터에 맞게 수행합니다.";
        };
    }

    private List<String> resolveActionInstructionSummaries(String dataType, String choiceActionId) {
        return switch (choiceActionId) {
            case "summarize" -> switch (dataType) {
                case "SINGLE_EMAIL" -> List.of("핵심 내용 요약", "중요 일정 추출", "후속 액션 정리");
                case "SINGLE_FILE" -> List.of("핵심 내용 요약", "주요 포인트 정리", "확인할 내용 구분");
                default -> List.of("핵심 내용 요약");
            };
            case "ai_summarize" -> switch (dataType) {
                case "ARTICLE_LIST" -> List.of("글별 핵심 내용 정리", "공통 흐름 요약");
                default -> List.of("핵심 내용 요약");
            };
            case "ai_refine" -> List.of("기존 의미 유지", "문장과 구조 다듬기");
            case "translate" -> List.of("원문 의미 유지", "자연스러운 번역");
            case "extract_info" -> List.of("필요 정보만 추출", "항목별로 구분해 정리");
            case "describe_image" -> List.of("보이는 요소 설명", "시각 정보 정리");
            case "ocr" -> List.of("텍스트 추출", "원문 순서 유지");
            case "classify_intent" -> List.of("의도 분류", "간단한 근거 제시");
            case "sentiment" -> List.of("감정 성향 판단", "간단한 근거 제시");
            case "urgency" -> List.of("긴급도 판단", "마감과 요청 강도 확인");
            case "extract_todos" -> List.of("해야 할 일 추출", "마감이나 확인 사항 정리");
            case "draft_reply" -> List.of("답장 초안 작성", "바로 보낼 수 있는 문장 구성");
            case "ai_generate" -> switch (dataType) {
                case "SPREADSHEET_DATA" -> List.of("행과 열 데이터를 반영", "맞춤 초안 작성");
                default -> List.of("입력 데이터를 반영한 초안 생성");
            };
            case "ai_analyze" -> switch (dataType) {
                case "SPREADSHEET_DATA" -> List.of("행과 열 구조 해석", "추세와 주요 인사이트 정리");
                case "API_RESPONSE" -> List.of("주요 항목 분석", "패턴과 변화 정리");
                case "SINGLE_FILE" -> List.of("자료 구조 분석", "핵심 포인트 정리");
                default -> List.of("주요 항목 분석", "핵심 인사이트 정리");
            };
            case "ai_filter" -> List.of("조건에 맞는 항목 선별");
            case "merge" -> List.of("여러 입력 통합", "중복 제거와 흐름 정리");
            default -> List.of("선택한 AI 작업 수행");
        };
    }

    private List<String> resolveSelectionInstructionSummaries(Object selectionsValue) {
        if (!(selectionsValue instanceof Map<?, ?> selections)) {
            return List.of();
        }

        List<String> instructions = new ArrayList<>();
        for (Map.Entry<?, ?> entry : selections.entrySet()) {
            String key = asText(entry.getKey());
            Object value = entry.getValue();

            if (key.contains(":")) {
                appendCustomInstruction(instructions, value);
                continue;
            }

            appendSelectionInstructionSummaries(instructions, value);
        }
        return instructions;
    }

    private void appendSelectionInstructionSummaries(List<String> instructions, Object value) {
        if (value instanceof List<?> values) {
            for (Object item : values) {
                appendSelectionInstructionSummary(instructions, item);
            }
            return;
        }
        appendSelectionInstructionSummary(instructions, value);
    }

    private void appendSelectionInstructionSummary(List<String> instructions, Object value) {
        String selectionId = asText(value);
        if (!hasText(selectionId) || "custom".equals(selectionId)) {
            return;
        }

        String modifier = promptRules.getModifiers() != null
                ? promptRules.getModifiers().get(selectionId)
                : null;
        instructions.add(hasText(modifier) ? modifier : "추가 선택: " + selectionId);
    }

    private String labelForDataType(String dataType) {
        return switch (dataType) {
            case "ARTICLE_LIST" -> "기사/게시글 목록";
            case "SINGLE_FILE" -> "단일 파일";
            case "SINGLE_EMAIL" -> "단일 이메일";
            case "SPREADSHEET_DATA" -> "스프레드시트 데이터";
            case "API_RESPONSE" -> "API 응답";
            case "TEXT" -> "텍스트";
            default -> dataType;
        };
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

    private void appendManualPromptSuffix(List<String> promptParts, String manualPrompt) {
        if (hasText(manualPrompt)) {
            promptParts.add("사용자 추가 프롬프트:\n" + manualPrompt);
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
