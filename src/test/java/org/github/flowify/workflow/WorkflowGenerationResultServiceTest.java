package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowGenerationResultServiceTest {

    private WorkflowGenerationResultService service;

    @BeforeEach
    void setUp() {
        ChoiceMappingService choiceMappingService = mock(ChoiceMappingService.class);
        when(choiceMappingService.getMappingRules()).thenReturn(mappingRules());
        service = new WorkflowGenerationResultService(
                new ObjectMapper(),
                new WorkflowValidator(),
                choiceMappingService
        );
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
        assertThat(request.getNodes().getFirst().getConfig()).containsEntry("service", "gmail");
        assertThat(request.getNodes().get(1).getLabel()).isEqualTo("내용 요약");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("choiceActionId", "summarize");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("choiceNodeType", "AI");
        assertThat(request.getNodes().get(1).getOutputDataType()).isEqualTo("TEXT");
        assertThat(request.getNodes().getLast().getConfig()).containsEntry("service", "slack");
    }

    @Test
    @DisplayName("Missing end config is accepted for draft workflow")
    void toCreateRequest_allowsMissingEndConfig() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("config", Map.of());

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().get(1).getConfig()).containsEntry("choiceActionId", "summarize");
        assertThat(request.getNodes().getLast().getConfig()).containsEntry("service", "slack");
    }

    @Test
    @DisplayName("Legacy source mode is normalized for start node")
    void toCreateRequest_normalizesLegacySourceMode() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of("mode", "new_email"));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getFirst().getConfig()).containsEntry("service", "gmail");
        assertThat(request.getNodes().getFirst().getConfig()).containsEntry("source_mode", "new_email");
    }

    @Test
    @DisplayName("Start node source mode is required")
    void toCreateRequest_rejectsMissingStartSourceMode() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of());

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("source mode");
    }

    @Test
    @DisplayName("Service config must match service node type")
    void toCreateRequest_rejectsServiceConfigMismatch() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of(
                "service", "slack",
                "source_mode", "new_email"
        ));

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("config.service");
    }

    @Test
    @DisplayName("Unsupported source mode is rejected")
    void toCreateRequest_rejectsUnsupportedSourceMode() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of("source_mode", "unknown_mode"));

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported source mode");
    }

    @Test
    @DisplayName("Middle node choice action is required")
    void toCreateRequest_rejectsMissingMiddleChoiceAction() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.get(1).put("config", Map.of("isConfigured", false));

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("choice action");
    }

    @Test
    @DisplayName("Unsupported middle node choice action is rejected")
    void toCreateRequest_rejectsUnsupportedMiddleChoiceAction() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.get(1).put("config", Map.of("choiceActionId", "unknown_action"));

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported processor action");
    }

    @Test
    @DisplayName("Middle node type must match selected choice action")
    void toCreateRequest_rejectsMiddleChoiceNodeTypeMismatch() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.get(1).put("type", "DATA_FILTER");

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("node type");
    }

    @Test
    @DisplayName("Middle node output data type must match selected choice action")
    void toCreateRequest_rejectsMiddleOutputDataTypeMismatch() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.get(1).put("outputDataType", "SINGLE_EMAIL");

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outputDataType");
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
                                "dataType", "SINGLE_EMAIL",
                                "config", Map.of("action", "summarize", "isConfigured", false)
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

    private MappingRules mappingRules() {
        return MappingRules.builder()
                .dataTypes(Map.of(
                        "SINGLE_EMAIL", DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("summarize")
                                        .label("내용 요약")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .build()))
                                .build()
                ))
                .build();
    }

    private Map<String, Object> mutableMap(Object... values) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
