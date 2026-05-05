package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                .contains("보고서 문체로 정리한다");
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
    @DisplayName("수동 prompt가 있으면 선택 규칙으로 덮어쓰지 않음")
    void resolve_keepsManualPrompt() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .type("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of(
                        "prompt", "직접 작성한 프롬프트",
                        "choiceActionId", "summarize"))
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
                .id("node_pass")
                .type("PASSTHROUGH")
                .dataType("SINGLE_FILE")
                .outputDataType("SINGLE_FILE")
                .config(Map.of("choiceActionId", "passthrough"))
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
