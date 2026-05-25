package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.catalog.dto.AiPromptMetadata;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.choice.ChoicePromptResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChoicePromptResolverTest {

    private ChoicePromptResolver choicePromptResolver;

    @BeforeEach
    void setUp() {
        choicePromptResolver = new ChoicePromptResolver(new ObjectMapper());
        ReflectionTestUtils.setField(choicePromptResolver, "promptRulesPath", "docs/ai_prompt_rules.json");
        ReflectionTestUtils.invokeMethod(choicePromptResolver, "loadPromptRules");
    }

    @Test
    @DisplayName("AI 선택 정보로 실행 프롬프트 생성")
    void resolve_buildsPromptFromChoice() {
        NodeDefinition node = aiNode("SINGLE_FILE", "summarize",
                Map.of("follow_up", "report_style"));

        Map<String, Object> resolved = choicePromptResolver.resolve(node);

        assertThat(resolved)
                .containsEntry("action", "process")
                .containsEntry("prompt_source", "choice_rule");
        assertThat((String) resolved.get("prompt"))
                .contains("Flowify 워크플로우의 AI 처리 노드")
                .contains("입력은 단일 파일이다")
                .contains("파일 내용을 요약한다")
                .contains("보고서 문체로 작성한다");
    }

    @Test
    @DisplayName("custom 후속 입력은 프롬프트에 포함")
    void resolve_includesCustomFollowUpInput() {
        Map<String, Object> selections = new LinkedHashMap<>();
        selections.put("follow_up", "custom");
        selections.put("follow_up:custom", "교수님께 보낼 공손한 문체로 작성");
        NodeDefinition node = aiNode("SINGLE_EMAIL", "draft_reply", selections);

        Map<String, Object> resolved = choicePromptResolver.resolve(node);

        assertThat((String) resolved.get("prompt"))
                .contains("메일에 대한 답장 초안")
                .contains("사용자 추가 요청: 교수님께 보낼 공손한 문체로 작성");
    }

    @Test
    @DisplayName("UI 타입 llm은 의미 타입으로 프롬프트 생성")
    void resolve_buildsPromptFromSemanticNodeType() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .type("llm")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of("choiceActionId", "summarize"))
                .build();

        Map<String, Object> resolved = choicePromptResolver.resolve(node, "AI");

        assertThat(resolved)
                .containsEntry("action", "process")
                .containsEntry("prompt_source", "choice_rule");
        assertThat((String) resolved.get("prompt")).isNotBlank();
    }

    @Test
    @DisplayName("AI prompt rule existence can be checked")
    void hasActionPrompt_checksPromptRuleExistence() {
        assertThat(choicePromptResolver.hasActionPrompt("SINGLE_EMAIL", "summarize")).isTrue();
        assertThat(choicePromptResolver.hasActionPrompt("SINGLE_EMAIL", "unknown_action")).isFalse();
        assertThat(choicePromptResolver.hasActionPrompt("UNKNOWN_DATA", "summarize")).isFalse();
    }

    @Test
    @DisplayName("유효한 choice prompt가 있으면 사용자 prompt를 뒤에 추가한다")
    void resolve_appendsManualPromptWhenChoicePromptCanBeBuilt() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .type("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of(
                        "prompt", "보고용 문장으로 정리하고 마지막에 확인할 사항을 적어줘",
                        "choiceActionId", "summarize"))
                .build();

        Map<String, Object> resolved = choicePromptResolver.resolve(node);

        assertThat(resolved)
                .containsEntry("action", "process")
                .containsEntry("prompt_source", "choice_rule_augmented");
        assertThat((String) resolved.get("prompt"))
                .contains("파일 내용을 요약한다")
                .contains("사용자 추가 프롬프트:\n보고용 문장으로 정리하고 마지막에 확인할 사항을 적어줘");
    }

    @Test
    @DisplayName("choiceActionId가 없으면 기존처럼 manual prompt만 사용한다")
    void resolve_keepsManualPromptWithoutChoiceAction() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .type("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of("prompt", "직접 작성한 프롬프트"))
                .build();

        Map<String, Object> resolved = choicePromptResolver.resolve(node);

        assertThat(resolved)
                .containsEntry("action", "process")
                .containsEntry("prompt", "직접 작성한 프롬프트")
                .containsEntry("prompt_source", "manual");
    }

    @Test
    @DisplayName("choice rule을 만들 수 없으면 manual prompt로 fallback한다")
    void resolve_fallsBackToManualPromptWhenChoiceRuleCannotBeBuilt() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .type("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of(
                        "prompt", "직접 작성한 프롬프트",
                        "choiceActionId", "unknown_action"))
                .build();

        Map<String, Object> resolved = choicePromptResolver.resolve(node);

        assertThat(resolved)
                .containsEntry("action", "process")
                .containsEntry("prompt", "직접 작성한 프롬프트")
                .containsEntry("prompt_source", "manual");
    }

    @Test
    @DisplayName("프롬프트 대상이 아닌 노드는 빈 결과 반환")
    void resolve_ignoresNonPromptNode() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_filter")
                .type("DATA_FILTER")
                .dataType("SINGLE_FILE")
                .outputDataType("SINGLE_FILE")
                .config(Map.of("choiceActionId", "filter_metadata"))
                .build();

        assertThat(choicePromptResolver.resolve(node)).isEmpty();
    }

    @Test
    @DisplayName("알 수 없는 AI 선택지는 INVALID_REQUEST")
    void resolve_rejectsUnknownAction() {
        NodeDefinition node = aiNode("SINGLE_FILE", "unknown_action", null);

        assertThatThrownBy(() -> choicePromptResolver.resolve(node))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("choice 기반 AI 노드는 FE 표시용 prompt metadata를 제공한다")
    void describe_buildsChoicePromptMetadata() {
        NodeDefinition node = aiNode("SINGLE_EMAIL", "summarize",
                Map.of("follow_up", "action_items"));

        AiPromptMetadata metadata = choicePromptResolver.describe(node);

        assertThat(metadata).isNotNull();
        assertThat(metadata.isAvailable()).isTrue();
        assertThat(metadata.getMode()).isEqualTo("recommended");
        assertThat(metadata.getPromptSource()).isEqualTo("choice_rule");
        assertThat(metadata.getCustomPromptMode()).isNull();
        assertThat(metadata.getChoiceActionId()).isEqualTo("summarize");
        assertThat(metadata.getBasePromptSummary()).isNotBlank();
        assertThat(metadata.getIncludedInstructions())
                .contains("입력 유형: 단일 이메일")
                .contains("핵심 내용 요약");
    }

    @Test
    @DisplayName("사용자 프롬프트가 있으면 augmented metadata로 표시한다")
    void describe_marksAugmentedChoicePromptMetadata() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .type("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of(
                        "prompt", "보고용 문장으로 보강",
                        "choiceActionId", "summarize",
                        "choiceSelections", Map.of("follow_up", "report_style")))
                .build();

        AiPromptMetadata metadata = choicePromptResolver.describe(node);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getMode()).isEqualTo("custom");
        assertThat(metadata.getPromptSource()).isEqualTo("choice_rule_augmented");
        assertThat(metadata.getCustomPromptMode()).isEqualTo("append");
        assertThat(metadata.getIncludedInstructions())
                .contains("입력 유형: 단일 파일")
                .anySatisfy(instruction -> assertThat(instruction).contains("보고"));
    }

    @Test
    @DisplayName("choiceActionId가 없으면 manual metadata만 제공한다")
    void describe_returnsManualOnlyMetadataWithoutChoiceAction() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .type("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of("prompt", "직접 작성한 프롬프트"))
                .build();

        AiPromptMetadata metadata = choicePromptResolver.describe(node);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getMode()).isEqualTo("custom");
        assertThat(metadata.getPromptSource()).isEqualTo("manual");
        assertThat(metadata.getCustomPromptMode()).isEqualTo("manual_only");
        assertThat(metadata.getBasePromptSummary()).contains("직접 작성한 프롬프트");
        assertThat(metadata.getIncludedInstructions())
                .contains("기본 AI 지시는 포함되지 않습니다.");
    }

    private NodeDefinition aiNode(String dataType, String actionId, Map<String, Object> selections) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("choiceActionId", actionId);
        if (selections != null) {
            config.put("choiceSelections", selections);
        }

        return NodeDefinition.builder()
                .id("node_ai")
                .type("AI")
                .dataType(dataType)
                .outputDataType("TEXT")
                .config(config)
                .build();
    }

}
