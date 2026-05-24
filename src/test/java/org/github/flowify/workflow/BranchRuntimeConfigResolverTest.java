package org.github.flowify.workflow;

import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.choice.BranchRuntimeConfigResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class BranchRuntimeConfigResolverTest {

    private BranchRuntimeConfigResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BranchRuntimeConfigResolver();
    }

    @Test
    @DisplayName("파일 종류 분기 선택을 runtime branch rules로 정규화한다")
    void resolve_returnsFileTypeBranchRules() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of("choiceActionId", "branch_by_file_type"))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(runtimeConfig)
                .containsEntry("branch_type", "file_type")
                .containsKeys("branch_rules", "fallback_branch");
        assertThat(branchRules(runtimeConfig))
                .extracting(rule -> rule.get("key"))
                .contains("pdf", "archive", "image", "spreadsheet", "document", "presentation");
    }

    @Test
    @DisplayName("선택된 파일 종류가 있으면 해당 branch rule만 남긴다")
    void resolve_filtersSelectedFileTypeBranches() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of(
                        "choiceActionId", "branch_by_file_type",
                        "choiceSelections", Map.of("branch_by_file_type", List.of("pdf", "archive", "other"))))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(branchRules(runtimeConfig))
                .extracting(rule -> rule.get("key"))
                .containsExactly("pdf", "archive");
        assertThat(fallbackBranch(runtimeConfig)).containsEntry("key", "other");
    }

    @Test
    @DisplayName("분기 노드가 아니면 runtime config를 만들지 않는다")
    void resolve_returnsEmptyForNonBranchNode() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .config(Map.of("choiceActionId", "branch_by_file_type"))
                .build();

        assertThat(resolver.resolve(node, "AI")).isEmpty();
    }

    @Test
    @DisplayName("branch_config key also filters file type branches")
    void resolve_filtersSelectedFileTypeBranchesFromBranchConfig() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of(
                        "choiceActionId", "branch_by_file_type",
                        "choiceSelections", Map.of("branch_config", List.of("pdf", "archive", "other"))))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(branchRules(runtimeConfig))
                .extracting(rule -> rule.get("key"))
                .containsExactly("pdf", "archive");
        assertThat(fallbackBranch(runtimeConfig)).containsEntry("key", "other");
    }

    @Test
    @DisplayName("branch_config other only keeps fallback without file type branch rules")
    void resolve_keepsFallbackOnlyBranchConfigSelection() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of(
                        "choiceActionId", "branch_by_file_type",
                        "choiceSelections", Map.of("branch_config", List.of("other"))))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(branchRules(runtimeConfig)).isEmpty();
        assertThat(fallbackBranch(runtimeConfig)).containsEntry("key", "other");
    }

    @Test
    @DisplayName("other only selection keeps fallback without branch rules")
    void resolve_keepsFallbackOnlySelection() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of(
                        "choiceActionId", "branch_by_file_type",
                        "choiceSelections", Map.of("branch_by_file_type", List.of("other"))))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(branchRules(runtimeConfig)).isEmpty();
        assertThat(fallbackBranch(runtimeConfig)).containsEntry("key", "other");
    }

    @Test
    @DisplayName("single file classify action does not create file list branch runtime config")
    void resolve_returnsEmptyForSingleFileClassifyAction() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of("choiceActionId", "classify_by_type"))
                .build();

        assertThat(resolver.resolve(node, "CONDITION_BRANCH")).isEmpty();
    }

    @Test
    @DisplayName("content classify action creates content branch runtime config")
    void resolve_returnsContentClassificationBranchRules() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of(
                        "choiceActionId", "classify_by_content",
                        "choiceSelections", Map.of("classify_by_content", List.of("positive_negative"))))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(runtimeConfig)
                .containsEntry("branch_type", "content_classification")
                .containsKeys("branch_rules", "fallback_branch");
        assertThat(branchRules(runtimeConfig))
                .extracting(rule -> rule.get("key"))
                .containsExactly("positive", "negative");
        assertThat(fallbackBranch(runtimeConfig)).containsEntry("key", "other");
    }

    @Test
    @DisplayName("content classify action expands multi target presets")
    void resolve_expandsContentClassificationPresetSelections() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of(
                        "choiceActionId", "classify_by_content",
                        "choiceSelections", Map.of("branch_config", List.of("important_check_ref"))))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(branchRules(runtimeConfig))
                .extracting(rule -> rule.get("key"))
                .containsExactly("important", "check", "reference");
    }

    @Test
    @DisplayName("email parts action creates body and attachments branch runtime config")
    void resolve_returnsEmailPartsBranchRules() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of("choiceActionId", "split_email_parts"))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(runtimeConfig)
                .containsEntry("branch_type", "email_parts")
                .containsKey("branch_rules");
        assertThat(branchRules(runtimeConfig))
                .extracting(
                        rule -> rule.get("key"),
                        rule -> rule.get("output_data_type"))
                .containsExactly(
                        tuple("body", "TEXT"),
                        tuple("attachments", "FILE_LIST"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> branchRules(Map<String, Object> runtimeConfig) {
        return (List<Map<String, Object>>) runtimeConfig.get("branch_rules");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fallbackBranch(Map<String, Object> runtimeConfig) {
        return (Map<String, Object>) runtimeConfig.get("fallback_branch");
    }
}
