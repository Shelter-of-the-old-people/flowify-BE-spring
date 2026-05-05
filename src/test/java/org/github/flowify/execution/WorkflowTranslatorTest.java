package org.github.flowify.execution;

import org.github.flowify.execution.service.WorkflowTranslator;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.choice.ChoiceNodeTypeResolver;
import org.github.flowify.workflow.service.choice.ChoicePromptResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTranslatorTest {

    @Mock
    private ChoicePromptResolver choicePromptResolver;

    @Mock
    private ChoiceNodeTypeResolver choiceNodeTypeResolver;

    private WorkflowTranslator workflowTranslator;

    @BeforeEach
    void setUp() {
        workflowTranslator = new WorkflowTranslator(choicePromptResolver, choiceNodeTypeResolver);
    }

    @Test
    @DisplayName("AI 노드 런타임 설정에 선택 기반 프롬프트 반영")
    void toRuntimeModel_appliesResolvedPromptToAiNode() {
        NodeDefinition aiNode = NodeDefinition.builder()
                .id("node_ai")
                .category("ai")
                .type("AI")
                .label("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of(
                        "choiceActionId", "summarize",
                        "node_type", "WRONG",
                        "output_data_type", "WRONG"))
                .build();
        when(choiceNodeTypeResolver.resolve(aiNode)).thenReturn("AI");
        when(choicePromptResolver.resolve(aiNode, "AI")).thenReturn(Map.of(
                "action", "process",
                "prompt", "resolved prompt",
                "prompt_source", "choice_rule",
                "node_type", "WRONG"));

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(aiNode));
        Map<String, Object> runtimeConfig = firstNodeRuntimeConfig(runtime);

        assertThat(runtimeConfig)
                .containsEntry("choiceActionId", "summarize")
                .containsEntry("action", "process")
                .containsEntry("prompt", "resolved prompt")
                .containsEntry("prompt_source", "choice_rule")
                .containsEntry("node_type", "AI")
                .containsEntry("output_data_type", "TEXT");
    }

    @Test
    @DisplayName("PASSTHROUGH 노드에는 프롬프트를 추가하지 않음")
    void toRuntimeModel_doesNotAddPromptToPassthroughNode() {
        NodeDefinition passthroughNode = NodeDefinition.builder()
                .id("node_pass")
                .category("processing")
                .type("PASSTHROUGH")
                .label("그대로 전달")
                .dataType("SINGLE_FILE")
                .outputDataType("SINGLE_FILE")
                .config(Map.of("choiceActionId", "passthrough"))
                .build();
        when(choiceNodeTypeResolver.resolve(passthroughNode)).thenReturn("PASSTHROUGH");
        when(choicePromptResolver.resolve(passthroughNode, "PASSTHROUGH")).thenReturn(Map.of());

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(passthroughNode));
        Map<String, Object> runtimeConfig = firstNodeRuntimeConfig(runtime);

        assertThat(runtimeConfig)
                .containsEntry("choiceActionId", "passthrough")
                .containsEntry("node_type", "PASSTHROUGH")
                .containsEntry("output_data_type", "SINGLE_FILE")
                .doesNotContainKeys("prompt", "prompt_source");
    }

    @Test
    @DisplayName("UI ???condition? choice node type 湲곕컲?쇰줈 if_else濡?蹂?섑븳??")
    void toRuntimeModel_translatesVisualConditionByChoiceNodeType() {
        NodeDefinition conditionNode = NodeDefinition.builder()
                .id("node_condition")
                .category("control")
                .type("condition")
                .label("遺꾨쪟")
                .dataType("SINGLE_FILE")
                .outputDataType("SINGLE_FILE")
                .config(Map.of(
                        "choiceActionId", "classify_by_type",
                        "choiceNodeType", "CONDITION_BRANCH"))
                .build();
        when(choiceNodeTypeResolver.resolve(conditionNode)).thenReturn("CONDITION_BRANCH");
        when(choicePromptResolver.resolve(conditionNode, "CONDITION_BRANCH")).thenReturn(Map.of());

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(conditionNode));
        Map<String, Object> node = firstRuntimeNode(runtime);
        Map<String, Object> runtimeConfig = firstNodeRuntimeConfig(runtime);

        assertThat(node).containsEntry("runtime_type", "if_else");
        assertThat(runtimeConfig)
                .containsEntry("choiceActionId", "classify_by_type")
                .containsEntry("choiceNodeType", "CONDITION_BRANCH")
                .containsEntry("node_type", "CONDITION_BRANCH")
                .containsEntry("output_data_type", "SINGLE_FILE")
                .doesNotContainKeys("prompt", "prompt_source");
    }

    private Workflow workflowWith(NodeDefinition node) {
        return Workflow.builder()
                .id("workflow-1")
                .name("테스트 워크플로우")
                .userId("user-1")
                .nodes(List.of(node))
                .edges(List.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstNodeRuntimeConfig(Map<String, Object> runtime) {
        return (Map<String, Object>) firstRuntimeNode(runtime).get("runtime_config");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstRuntimeNode(Map<String, Object> runtime) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) runtime.get("nodes");
        return nodes.get(0);
    }
}
