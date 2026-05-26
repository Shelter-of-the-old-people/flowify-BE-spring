package org.github.flowify.workflow;

import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Position;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.service.generation.WorkflowGenerationSnapshotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGenerationSnapshotServiceTest {

    private final WorkflowGenerationSnapshotService service = new WorkflowGenerationSnapshotService();

    @Test
    void buildSnapshot_excludesUnsafeConfigValues() {
        WorkflowResponse workflow = WorkflowResponse.builder()
                .id("wf1")
                .name("Existing workflow")
                .description("Existing draft")
                .nodes(List.of(
                        NodeDefinition.builder()
                                .id("start_1")
                                .category("service")
                                .type("gmail")
                                .label("Gmail")
                                .role("start")
                                .position(Position.builder().x(10).y(20).build())
                                .outputDataType("SINGLE_EMAIL")
                                .config(Map.of(
                                        "service", "gmail",
                                        "source_mode", "new_email",
                                        "target", "inbox",
                                        "folder_id", "folder-123",
                                        "webhook_url", "https://example.com/hook",
                                        "access_token", "token",
                                        "keyword", "ai"))
                                .build()
                ))
                .edges(List.of(
                        EdgeDefinition.builder()
                                .id("edge_1")
                                .source("start_1")
                                .target("end_1")
                                .build()
                ))
                .trigger(TriggerConfig.builder()
                        .type("schedule")
                        .config(Map.of("cron", "* * * * *"))
                        .build())
                .nodeStatuses(List.of(
                        NodeStatusResponse.builder()
                                .nodeId("start_1")
                                .configured(false)
                                .saveable(true)
                                .choiceable(true)
                                .executable(false)
                                .missingFields(List.of("config.target"))
                                .build()
                ))
                .build();

        Map<String, Object> snapshot = service.buildSnapshot(workflow);

        assertThat(snapshot).containsEntry("id", "wf1");
        assertThat(snapshot.get("nodes")).asList().hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) ((List<?>) snapshot.get("nodes")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> configSummary = (Map<String, Object>) node.get("configSummary");
        assertThat(configSummary)
                .containsEntry("service", "gmail")
                .containsEntry("source_mode", "new_email")
                .containsEntry("keyword", "ai")
                .doesNotContainKeys("target", "folder_id", "webhook_url", "access_token");
        @SuppressWarnings("unchecked")
        Map<String, Object> trigger = (Map<String, Object>) snapshot.get("trigger");
        assertThat(trigger)
                .containsEntry("type", "schedule")
                .containsEntry("config", Map.of());
    }

    @Test
    void buildSnapshot_preservesSupportedBranchSelectionsOnly() {
        WorkflowResponse workflow = WorkflowResponse.builder()
                .id("wf_branch")
                .nodes(List.of(
                        NodeDefinition.builder()
                                .id("branch_1")
                                .category("logic")
                                .type("CONDITION_BRANCH")
                                .label("File type branch")
                                .role("middle")
                                .dataType("FILE_LIST")
                                .outputDataType("FILE_LIST")
                                .config(Map.of(
                                        "choiceActionId", "branch_by_file_type",
                                        "choiceNodeType", "CONDITION_BRANCH",
                                        "choiceSelections", Map.of(
                                                "branch_config", List.of("pdf", "image", "other"),
                                                "internal_custom", List.of("secret")),
                                        "webhook_url", "https://example.com/hook"))
                                .build(),
                        NodeDefinition.builder()
                                .id("ai_1")
                                .category("logic")
                                .type("AI")
                                .label("Summarize")
                                .role("middle")
                                .dataType("TEXT")
                                .outputDataType("TEXT")
                                .config(Map.of(
                                        "choiceActionId", "summarize",
                                        "choiceNodeType", "AI",
                                        "choiceSelections", Map.of("summarize", "brief")))
                                .build()
                ))
                .edges(List.of())
                .build();

        Map<String, Object> snapshot = service.buildSnapshot(workflow);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) snapshot.get("nodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchConfig = (Map<String, Object>) nodes.get(0).get("configSummary");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchSelections =
                (Map<String, Object>) branchConfig.get("choiceSelections");
        @SuppressWarnings("unchecked")
        Map<String, Object> aiConfig = (Map<String, Object>) nodes.get(1).get("configSummary");

        assertThat(branchConfig)
                .containsEntry("choiceActionId", "branch_by_file_type")
                .containsEntry("choiceNodeType", "CONDITION_BRANCH")
                .doesNotContainKey("webhook_url");
        assertThat(branchSelections)
                .containsEntry("branch_config", List.of("pdf", "image", "other"))
                .doesNotContainKey("internal_custom");
        assertThat(aiConfig)
                .containsEntry("choiceActionId", "summarize")
                .doesNotContainKey("choiceSelections");
    }

    @Test
    void buildSnapshot_preservesBodyAttachmentBranchSelectionsOnly() {
        WorkflowResponse workflow = WorkflowResponse.builder()
                .id("wf_email_branch")
                .nodes(List.of(NodeDefinition.builder()
                        .id("branch_1")
                        .category("logic")
                        .type("CONDITION_BRANCH")
                        .label("Email parts branch")
                        .role("middle")
                        .dataType("SINGLE_EMAIL")
                        .outputDataType("SINGLE_EMAIL")
                        .config(Map.of(
                                "choiceActionId", "split_email_parts",
                                "choiceNodeType", "CONDITION_BRANCH",
                                "choiceSelections", Map.of(
                                        "branch_config", List.of("body", "attachments", "secret"),
                                        "split_email_parts", List.of("body")),
                                "webhook_url", "https://example.com/hook"))
                        .build()))
                .edges(List.of())
                .build();

        Map<String, Object> snapshot = service.buildSnapshot(workflow);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) snapshot.get("nodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchConfig = (Map<String, Object>) nodes.getFirst().get("configSummary");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchSelections =
                (Map<String, Object>) branchConfig.get("choiceSelections");

        assertThat(branchConfig)
                .containsEntry("choiceActionId", "split_email_parts")
                .containsEntry("choiceNodeType", "CONDITION_BRANCH")
                .doesNotContainKey("webhook_url");
        assertThat(branchSelections)
                .containsEntry("branch_config", List.of("body", "attachments"));
    }

    @Test
    void buildSnapshot_preservesContentBranchPresetSelection() {
        Map<String, Object> branchConfig = snapshotContentBranchConfig(
                "TEXT",
                Map.of("branch_config", List.of("positive_negative", "positive", "other")));

        @SuppressWarnings("unchecked")
        Map<String, Object> branchSelections =
                (Map<String, Object>) branchConfig.get("choiceSelections");

        assertThat(branchConfig)
                .containsEntry("choiceActionId", "classify_by_content")
                .containsEntry("choiceNodeType", "CONDITION_BRANCH");
        assertThat(branchSelections)
                .containsEntry("branch_config", List.of("positive_negative"));
    }

    @Test
    void buildSnapshot_preservesContentBranchPresetFromActionSelectionKey() {
        Map<String, Object> branchConfig = snapshotContentBranchConfig(
                "SINGLE_EMAIL",
                Map.of("classify_by_content", List.of("important_ref")));

        @SuppressWarnings("unchecked")
        Map<String, Object> branchSelections =
                (Map<String, Object>) branchConfig.get("choiceSelections");

        assertThat(branchSelections)
                .containsEntry("branch_config", List.of("important_ref"));
    }

    @Test
    void buildSnapshot_ignoresContentBranchEdgeKeysWithoutPreset() {
        Map<String, Object> branchConfig = snapshotContentBranchConfig(
                "TEXT",
                Map.of("branch_config", List.of("positive", "negative", "other")));

        assertThat(branchConfig).doesNotContainKey("choiceSelections");
    }

    @Test
    void buildSnapshot_ignoresAmbiguousContentBranchPresets() {
        Map<String, Object> branchConfig = snapshotContentBranchConfig(
                "TEXT",
                Map.of("branch_config", List.of("positive_negative", "important_ref")));

        assertThat(branchConfig).doesNotContainKey("choiceSelections");
    }

    @Test
    void buildSnapshot_ignoresContentBranchPresetUnsupportedForDataType() {
        Map<String, Object> branchConfig = snapshotContentBranchConfig(
                "SINGLE_EMAIL",
                Map.of("branch_config", List.of("positive_negative")));

        assertThat(branchConfig).doesNotContainKey("choiceSelections");
    }

    @Test
    void buildSnapshot_ignoresContentBranchPresetWithoutSupportedDataType() {
        Map<String, Object> branchConfig = snapshotContentBranchConfig(
                "FILE_LIST",
                Map.of("branch_config", List.of("positive_negative")));

        assertThat(branchConfig).doesNotContainKey("choiceSelections");
    }

    @Test
    void buildSnapshot_preservesFilenameBranchRulesOnly() {
        WorkflowResponse workflow = WorkflowResponse.builder()
                .id("wf_filename_branch")
                .nodes(List.of(NodeDefinition.builder()
                        .id("branch_1")
                        .category("logic")
                        .type("CONDITION_BRANCH")
                        .label("Filename branch")
                        .role("middle")
                        .dataType("FILE_LIST")
                        .outputDataType("FILE_LIST")
                        .config(Map.of(
                                "choiceActionId", "branch_by_filename",
                                "choiceNodeType", "CONDITION_BRANCH",
                                "filenameRules", List.of(
                                        Map.of(
                                                "key", "filename_1",
                                                "label", "공지",
                                                "keywords", List.of("공지", "")),
                                        Map.of(
                                                "key", "filename_custom",
                                                "label", "비정상",
                                                "keywords", List.of("secret")),
                                        Map.of(
                                                "key", "filename_2",
                                                "label", "과제",
                                                "keywords", List.of("과제", "assignment"))),
                                "choiceSelections", Map.of(
                                        "branch_config", List.of("filename_1", "filename_2", "filename_9", "other"),
                                        "branch_by_filename", List.of("filename_1", "other"),
                                        "branches", List.of("filename_2", "other")),
                                "webhook_url", "https://example.com/hook"))
                        .build()))
                .edges(List.of())
                .build();

        Map<String, Object> snapshot = service.buildSnapshot(workflow);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) snapshot.get("nodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchConfig = (Map<String, Object>) nodes.getFirst().get("configSummary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) branchConfig.get("filenameRules");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchSelections =
                (Map<String, Object>) branchConfig.get("choiceSelections");

        assertThat(branchConfig)
                .containsEntry("choiceActionId", "branch_by_filename")
                .containsEntry("choiceNodeType", "CONDITION_BRANCH")
                .doesNotContainKey("webhook_url");
        assertThat(rules)
                .extracting(rule -> rule.get("key"))
                .containsExactly("filename_1", "filename_2");
        assertThat(rules.getFirst()).containsEntry("keywords", List.of("공지"));
        assertThat(branchSelections)
                .containsEntry("branch_config", List.of("filename_1", "filename_2", "other"));
    }

    @Test
    void buildSnapshot_preservesFieldValueBranchRulesOnly() {
        WorkflowResponse workflow = WorkflowResponse.builder()
                .id("wf_field_value_branch")
                .nodes(List.of(NodeDefinition.builder()
                        .id("branch_1")
                        .category("logic")
                        .type("CONDITION_BRANCH")
                        .label("Field value branch")
                        .role("middle")
                        .dataType("SPREADSHEET_DATA")
                        .outputDataType("SPREADSHEET_DATA")
                        .config(Map.of(
                                "choiceActionId", "classify_by_field",
                                "choiceNodeType", "CONDITION_BRANCH",
                                "fieldValueRules", List.of(
                                        Map.of(
                                                "key", "field_value_1",
                                                "label", "완료",
                                                "field", "상태",
                                                "value", "완료"),
                                        Map.of(
                                                "key", "field_value_custom",
                                                "label", "비정상",
                                                "field", "상태",
                                                "value", "비밀"),
                                        Map.of(
                                                "key", "field_value_2",
                                                "label", "진행중",
                                                "field", "상태",
                                                "value", "진행중")),
                                "choiceSelections", Map.of(
                                        "branch_config", List.of(
                                                "field_value_1",
                                                "field_value_2",
                                                "field_value_9",
                                                "other"),
                                        "classify_by_field", List.of("field_value_1", "other"),
                                        "branches", List.of("field_value_2", "other")),
                                "webhook_url", "https://example.com/hook"))
                        .build()))
                .edges(List.of())
                .build();

        Map<String, Object> snapshot = service.buildSnapshot(workflow);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) snapshot.get("nodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchConfig = (Map<String, Object>) nodes.getFirst().get("configSummary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) branchConfig.get("fieldValueRules");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchSelections =
                (Map<String, Object>) branchConfig.get("choiceSelections");

        assertThat(branchConfig)
                .containsEntry("choiceActionId", "classify_by_field")
                .containsEntry("choiceNodeType", "CONDITION_BRANCH")
                .doesNotContainKey("webhook_url");
        assertThat(rules)
                .extracting(rule -> rule.get("key"))
                .containsExactly("field_value_1", "field_value_2");
        assertThat(rules.getFirst())
                .containsEntry("field", "상태")
                .containsEntry("value", "완료");
        assertThat(branchSelections)
                .containsEntry("branch_config", List.of("field_value_1", "field_value_2", "other"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotContentBranchConfig(
            String dataType,
            Map<String, Object> choiceSelections
    ) {
        WorkflowResponse workflow = WorkflowResponse.builder()
                .id("wf_content_branch")
                .nodes(List.of(NodeDefinition.builder()
                        .id("branch_1")
                        .category("logic")
                        .type("CONDITION_BRANCH")
                        .label("Content branch")
                        .role("middle")
                        .dataType(dataType)
                        .outputDataType("TEXT")
                        .config(Map.of(
                                "choiceActionId", "classify_by_content",
                                "choiceNodeType", "CONDITION_BRANCH",
                                "choiceSelections", choiceSelections,
                                "webhook_url", "https://example.com/hook"))
                        .build()))
                .edges(List.of())
                .build();

        Map<String, Object> snapshot = service.buildSnapshot(workflow);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) snapshot.get("nodes");
        return (Map<String, Object>) nodes.getFirst().get("configSummary");
    }
}
