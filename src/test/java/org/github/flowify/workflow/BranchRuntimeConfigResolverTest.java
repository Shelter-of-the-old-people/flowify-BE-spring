package org.github.flowify.workflow;

import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.choice.BranchRuntimeConfigResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
                .contains("pdf", "image", "spreadsheet", "document", "presentation");
    }

    @Test
    @DisplayName("선택된 파일 종류가 있으면 해당 branch rule만 남긴다")
    void resolve_filtersSelectedFileTypeBranches() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_branch")
                .config(Map.of(
                        "choiceActionId", "branch_by_file_type",
                        "choiceSelections", Map.of("branch_by_file_type", List.of("pdf", "image", "other"))))
                .build();

        Map<String, Object> runtimeConfig = resolver.resolve(node, "CONDITION_BRANCH");

        assertThat(branchRules(runtimeConfig))
                .extracting(rule -> rule.get("key"))
                .containsExactly("pdf", "image");
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> branchRules(Map<String, Object> runtimeConfig) {
        return (List<Map<String, Object>>) runtimeConfig.get("branch_rules");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fallbackBranch(Map<String, Object> runtimeConfig) {
        return (Map<String, Object>) runtimeConfig.get("fallback_branch");
    }
}
