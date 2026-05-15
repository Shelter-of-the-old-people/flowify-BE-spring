package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.service.generation.WorkflowGenerationResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGenerationResultServiceTest {

    private WorkflowGenerationResultService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowGenerationResultService(new ObjectMapper(), new WorkflowValidator());
    }

    @Test
    @DisplayName("AI draft is normalized to create request")
    void toCreateRequest_normalizesGeneratedDraft() {
        WorkflowCreateRequest request = service.toCreateRequest(validDraft());

        assertThat(request.getName()).isEqualTo("Mail summary");
        assertThat(request.getTrigger().getType()).isEqualTo(WorkflowTriggerSupport.TYPE_MANUAL);
        assertThat(request.getTrigger().getConfig()).isEmpty();
        assertThat(request.getNodes()).hasSize(3);
        assertThat(request.getEdges()).hasSize(2);
        assertThat(request.getEdges().getFirst().getId()).isEqualTo("edge_start_ai");
    }

    @Test
    @DisplayName("Missing node config is accepted for draft workflow")
    void toCreateRequest_allowsMissingConfig() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of());
        nodes.getLast().put("config", Map.of());

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getFirst().getConfig()).isEmpty();
        assertThat(request.getNodes().getLast().getConfig()).isEmpty();
    }

    @Test
    @DisplayName("Branch topology is rejected")
    void toCreateRequest_rejectsBranch() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) draft.get("edges");
        edges.add(Map.of("source", "start", "target", "end"));

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("single path");
    }

    @Test
    @DisplayName("Unsupported sink is rejected")
    void toCreateRequest_rejectsUnsupportedSink() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("type", "unknown_sink");

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported sink");
    }

    @Test
    @DisplayName("Runtime fields are rejected")
    void toCreateRequest_rejectsRuntimeFields() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("runtime_source", Map.of("service", "gmail"));

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("runtime fields");
    }

    private Map<String, Object> validDraft() {
        return mutableMap(
                "name", "Mail summary",
                "description", "Summarize mail and send to Slack",
                "nodes", new java.util.ArrayList<>(List.of(
                        mutableMap(
                                "id", "start",
                                "category", "service",
                                "type", "gmail",
                                "label", "Gmail",
                                "role", "start",
                                "position", Map.of("x", 0, "y", 0),
                                "config", Map.of("source_mode", "new_email")
                        ),
                        mutableMap(
                                "id", "ai",
                                "category", "logic",
                                "type", "AI",
                                "label", "AI",
                                "role", "middle",
                                "config", Map.of("isConfigured", false)
                        ),
                        mutableMap(
                                "id", "end",
                                "category", "service",
                                "type", "slack",
                                "label", "Slack",
                                "role", "end",
                                "config", Map.of("isConfigured", false)
                        )
                )),
                "edges", new java.util.ArrayList<>(List.of(
                        mutableMap("source", "start", "target", "ai"),
                        mutableMap("id", "edge_ai_end", "source", "ai", "target", "end")
                )),
                "trigger", Map.of("type", "schedule", "config", Map.of("interval_hours", 4))
        );
    }

    private Map<String, Object> mutableMap(Object... values) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
