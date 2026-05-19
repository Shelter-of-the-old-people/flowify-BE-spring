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
}
