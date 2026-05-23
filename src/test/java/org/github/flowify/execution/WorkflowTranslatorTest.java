package org.github.flowify.execution;

import org.github.flowify.execution.service.WorkflowTranslator;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.choice.BranchRuntimeConfigResolver;
import org.github.flowify.workflow.service.choice.ChoiceNodeTypeResolver;
import org.github.flowify.workflow.service.choice.ChoicePromptResolver;
import org.github.flowify.workflow.state.service.WorkflowNodeStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTranslatorTest {

    @Mock
    private ChoicePromptResolver choicePromptResolver;

    @Mock
    private ChoiceNodeTypeResolver choiceNodeTypeResolver;
    @Mock
    private WorkflowNodeStateService workflowNodeStateService;

    private WorkflowTranslator workflowTranslator;

    @BeforeEach
    void setUp() {
        workflowTranslator = new WorkflowTranslator(
                choicePromptResolver,
                choiceNodeTypeResolver,
                new BranchRuntimeConfigResolver(),
                workflowNodeStateService);
        when(workflowNodeStateService.getStateMap(anyString())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("Gmail sender_email start node는 runtime_source target을 그대로 전달한다")
    void toRuntimeModel_translatesGmailSenderEmailSource() {
        NodeDefinition gmailNode = NodeDefinition.builder()
                .id("gmail_sender")
                .category("service")
                .type("gmail")
                .role("start")
                .label("Gmail")
                .outputDataType("SINGLE_EMAIL")
                .config(Map.of(
                        "service", "gmail",
                        "source_mode", "sender_email",
                        "target", "sender@example.com",
                        "canonical_input_type", "SINGLE_EMAIL",
                        "trigger_kind", "event",
                        "isConfigured", true))
                .build();

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(gmailNode));
        Map<String, Object> node = firstRuntimeNode(runtime);

        assertThat(node).containsEntry("runtime_type", "input");
        assertThat(node)
                .extracting(entry -> entry.get("runtime_source"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("service", "gmail")
                .containsEntry("mode", "sender_email")
                .containsEntry("target", "sender@example.com")
                .containsEntry("canonical_input_type", "SINGLE_EMAIL")
                .containsEntry("config", gmailNode.getConfig());
    }

    @Test
    @DisplayName("Google Drive end node 파일명 설정은 runtime_sink config에 보존된다")
    void toRuntimeModel_preservesGoogleDriveSinkFilenameConfig() {
        NodeDefinition driveNode = NodeDefinition.builder()
                .id("drive_sink")
                .category("service")
                .type("google_drive")
                .role("end")
                .label("Google Drive")
                .dataType("TEXT")
                .config(Map.of(
                        "service", "google_drive",
                        "folder_id", "folder_123",
                        "filename_template", "summary_{{date}}",
                        "file_format", "txt",
                        "isConfigured", true))
                .build();

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(driveNode));
        Map<String, Object> node = firstRuntimeNode(runtime);

        assertThat(node).containsEntry("runtime_type", "output");
        assertThat(node)
                .extracting(entry -> entry.get("runtime_sink"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("service", "google_drive")
                .extracting(entry -> entry.get("config"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("folder_id", "folder_123")
                .containsEntry("filename_template", "summary_{{date}}")
                .containsEntry("file_format", "txt");
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
                .containsEntry("output_data_type", "TEXT")
                .containsEntry("requires_content", true);
    }

    @Test
    @DisplayName("명시적 requires_content=false는 자동 본문 필요 추론보다 우선한다")
    void toRuntimeModel_explicitRequiresContentFalseWins() {
        NodeDefinition aiNode = NodeDefinition.builder()
                .id("node_ai")
                .category("ai")
                .type("AI")
                .label("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of(
                        "choiceActionId", "summarize",
                        "requires_content", false))
                .build();
        when(choiceNodeTypeResolver.resolve(aiNode)).thenReturn("AI");
        when(choicePromptResolver.resolve(aiNode, "AI")).thenReturn(Map.of(
                "action", "process",
                "prompt", "resolved prompt"));

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(aiNode));
        Map<String, Object> runtimeConfig = firstNodeRuntimeConfig(runtime);

        assertThat(runtimeConfig).containsEntry("requires_content", false);
    }

    @Test
    @DisplayName("legacy choiceSelections action key로 본문 필요 여부를 추론한다")
    void toRuntimeModel_infersRequiresContentFromChoiceSelectionsKey() {
        NodeDefinition aiNode = NodeDefinition.builder()
                .id("node_ai")
                .category("ai")
                .type("AI")
                .label("AI")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of("choiceSelections", Map.of("summarize", "brief")))
                .build();
        when(choiceNodeTypeResolver.resolve(aiNode)).thenReturn("AI");
        when(choicePromptResolver.resolve(aiNode, "AI")).thenReturn(Map.of(
                "action", "process",
                "prompt", "manual prompt"));

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(aiNode));
        Map<String, Object> runtimeConfig = firstNodeRuntimeConfig(runtime);

        assertThat(runtimeConfig).containsEntry("requires_content", true);
    }

    @Test
    @DisplayName("본문 의존 action은 requires_content=true로 추론한다")
    void toRuntimeModel_infersRequiresContentForContentDependentActions() {
        for (String action : List.of(
                "summarize",
                "extract_info",
                "translate",
                "classify_by_content",
                "describe_image",
                "ocr",
                "ai_summarize",
                "ai_analyze")) {
            NodeDefinition aiNode = NodeDefinition.builder()
                    .id("node_" + action)
                    .category("ai")
                    .type("AI")
                    .label("AI")
                    .dataType("SINGLE_FILE")
                    .outputDataType("TEXT")
                    .config(Map.of("choiceActionId", action))
                    .build();
            when(choiceNodeTypeResolver.resolve(aiNode)).thenReturn("AI");
            when(choicePromptResolver.resolve(aiNode, "AI")).thenReturn(Map.of(
                    "action", "process",
                    "prompt", "resolved prompt"));

            Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(aiNode));
            Map<String, Object> runtimeConfig = firstNodeRuntimeConfig(runtime);

            assertThat(runtimeConfig)
                    .as("action=%s", action)
                    .containsEntry("requires_content", true);
        }
    }

    @Test
    @DisplayName("UI 조건 노드를 choice node type 기반으로 if_else로 변환한다")
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
                .containsEntry("requires_content", false)
                .doesNotContainKeys("prompt", "prompt_source");
    }

    @Test
    @DisplayName("content classification branch config is included in runtime config")
    void toRuntimeModel_includesContentClassificationBranchConfig() {
        NodeDefinition conditionNode = NodeDefinition.builder()
                .id("node_condition")
                .category("control")
                .type("condition")
                .label("branch")
                .dataType("TEXT")
                .outputDataType("TEXT")
                .config(Map.of(
                        "choiceActionId", "classify_by_content",
                        "choiceNodeType", "CONDITION_BRANCH",
                        "choiceSelections", Map.of("classify_by_content", List.of("important_ref"))))
                .build();
        when(choiceNodeTypeResolver.resolve(conditionNode)).thenReturn("CONDITION_BRANCH");
        when(choicePromptResolver.resolve(conditionNode, "CONDITION_BRANCH")).thenReturn(Map.of());

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(conditionNode));
        Map<String, Object> runtimeConfig = firstNodeRuntimeConfig(runtime);

        assertThat(runtimeConfig)
                .containsEntry("branch_type", "content_classification")
                .containsEntry("node_type", "CONDITION_BRANCH")
                .containsEntry("output_data_type", "TEXT")
                .containsEntry("requires_content", true);
        assertThat(branchRules(runtimeConfig))
                .extracting(rule -> rule.get("key"))
                .containsExactly("important", "reference");
    }

    @Test
    @DisplayName("edge branch metadata is included in runtime edges")
    void toRuntimeModel_includesEdgeMetadata() {
        NodeDefinition sourceNode = NodeDefinition.builder()
                .id("node_branch")
                .category("control")
                .type("condition")
                .label("분기")
                .dataType("FILE_LIST")
                .outputDataType("FILE_LIST")
                .build();
        NodeDefinition targetNode = NodeDefinition.builder()
                .id("node_pdf")
                .category("processing")
                .type("loop")
                .label("PDF 처리")
                .dataType("FILE_LIST")
                .outputDataType("SINGLE_FILE")
                .build();
        EdgeDefinition edge = EdgeDefinition.builder()
                .id("edge_pdf")
                .source("node_branch")
                .target("node_pdf")
                .label("pdf")
                .sourceHandle("pdf")
                .targetHandle("input")
                .build();
        when(choiceNodeTypeResolver.resolve(sourceNode)).thenReturn("CONDITION_BRANCH");
        when(choiceNodeTypeResolver.resolve(targetNode)).thenReturn("LOOP");
        when(choicePromptResolver.resolve(sourceNode, "CONDITION_BRANCH")).thenReturn(Map.of());
        when(choicePromptResolver.resolve(targetNode, "LOOP")).thenReturn(Map.of());

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(
                workflowWith(List.of(sourceNode, targetNode), List.of(edge)));
        List<Map<String, Object>> edges = runtimeEdges(runtime);

        assertThat(edges).singleElement()
                .satisfies(runtimeEdge -> assertThat(runtimeEdge)
                        .containsEntry("id", "edge_pdf")
                        .containsEntry("source", "node_branch")
                        .containsEntry("target", "node_pdf")
                        .containsEntry("label", "pdf")
                        .containsEntry("sourceHandle", "pdf")
                        .containsEntry("targetHandle", "input"));
    }

    @Test
    @DisplayName("중간 Google Sheets 노드는 integration runtime_action으로 변환된다")
    void toRuntimeModel_translatesGoogleSheetsMiddleNodeToIntegration() {
        NodeDefinition sheetsNode = NodeDefinition.builder()
                .id("node_sheets")
                .category("spreadsheet")
                .type("google_sheets")
                .role("middle")
                .dataType("TEXT")
                .outputDataType("SPREADSHEET_DATA")
                .config(Map.of(
                        "service", "google_sheets",
                        "action", "search_text",
                        "spreadsheet_id", "sheet-1",
                        "sheet_name", "Sheet1"))
                .build();
        when(choiceNodeTypeResolver.resolve(sheetsNode)).thenReturn("google_sheets");
        when(workflowNodeStateService.getStateMap("workflow-1"))
                .thenReturn(Map.of("node_sheets", Map.of("last_seen_row_index", 10)));

        Map<String, Object> runtime = workflowTranslator.toRuntimeModel(workflowWith(sheetsNode));
        Map<String, Object> node = firstRuntimeNode(runtime);

        assertThat(node).containsEntry("runtime_type", "integration");
        assertThat(node)
                .extracting(entry -> entry.get("runtime_action"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("service", "google_sheets")
                .containsEntry("action", "search_text")
                .containsKey("state");
    }

    private Workflow workflowWith(NodeDefinition node) {
        return workflowWith(List.of(node), List.of());
    }

    private Workflow workflowWith(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        return Workflow.builder()
                .id("workflow-1")
                .name("테스트 워크플로우")
                .userId("user-1")
                .nodes(nodes)
                .edges(edges)
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> branchRules(Map<String, Object> runtimeConfig) {
        return (List<Map<String, Object>>) runtimeConfig.get("branch_rules");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runtimeEdges(Map<String, Object> runtime) {
        return (List<Map<String, Object>>) runtime.get("edges");
    }
}
