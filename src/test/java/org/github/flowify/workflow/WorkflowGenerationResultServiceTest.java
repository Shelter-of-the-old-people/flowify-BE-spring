package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.github.flowify.workflow.service.choice.dto.Option;
import org.github.flowify.workflow.service.choice.dto.ProcessingMethod;
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
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        ChoiceMappingService choiceMappingService = mock(ChoiceMappingService.class);
        catalogService = mock(CatalogService.class);
        when(choiceMappingService.getMappingRules()).thenReturn(mappingRules());
        when(catalogService.findSourceMode("gmail", "new_email"))
                .thenReturn(new SourceMode("new_email", "새 메일", "SINGLE_EMAIL", "event", Map.of()));
        when(catalogService.findSourceMode("gmail", "label_emails"))
                .thenReturn(new SourceMode("label_emails", "Label emails", "EMAIL_LIST", "manual", Map.of()));
        when(catalogService.findSinkService("slack"))
                .thenReturn(new SinkService(
                        "slack",
                        "Slack",
                        true,
                        List.of("SINGLE_EMAIL", "TEXT"),
                        "per_service",
                        Map.of()
                ));
        service = new WorkflowGenerationResultService(
                new ObjectMapper(),
                new WorkflowValidator(),
                choiceMappingService,
                catalogService
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
        assertThat(request.getNodes().getFirst().getOutputDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(request.getNodes().get(1).getLabel()).isEqualTo("내용 요약");
        assertThat(request.getNodes().get(1).getDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("choiceActionId", "summarize");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("choiceNodeType", "AI");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("isConfigured", true);
        assertThat(request.getNodes().get(1).getOutputDataType()).isEqualTo("TEXT");
        assertThat(request.getNodes().getLast().getConfig()).containsEntry("service", "slack");
        assertThat(request.getNodes().getLast().getDataType()).isEqualTo("TEXT");
        assertThat(request.getNodes().getLast().getOutputDataType()).isNull();
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
    @DisplayName("Middle node input data type is inferred from previous node output")
    void toCreateRequest_infersMiddleDataTypeFromPreviousOutput() {
        Map<String, Object> draft = validDraft();

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().get(1).getDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(request.getNodes().get(1).getLabel()).isEqualTo("내용 요약");
    }

    @Test
    @DisplayName("End node input data type is inferred from previous node output")
    void toCreateRequest_infersEndDataTypeFromPreviousOutput() {
        Map<String, Object> draft = validDraft();

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getLast().getDataType()).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("End node data type must match previous node output")
    void toCreateRequest_rejectsEndDataTypeMismatch() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("dataType", "SINGLE_EMAIL");

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dataType");
    }

    @Test
    @DisplayName("End node output data type is cleared")
    void toCreateRequest_clearsEndOutputDataType() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("outputDataType", "TEXT");

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getLast().getOutputDataType()).isNull();
    }

    @Test
    @DisplayName("End node input data type must be accepted by sink")
    void toCreateRequest_rejectsUnsupportedEndInputDataType() {
        Map<String, Object> draft = validDraft();
        when(catalogService.findSinkService("slack"))
                .thenReturn(new SinkService(
                        "slack",
                        "Slack",
                        true,
                        List.of("SINGLE_EMAIL"),
                        "per_service",
                        Map.of()
                ));

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not support");
    }

    @Test
    @DisplayName("Start node output data type is inferred from source mode")
    void toCreateRequest_infersStartOutputDataTypeFromSourceMode() {
        Map<String, Object> draft = validDraft();

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getFirst().getOutputDataType()).isEqualTo("SINGLE_EMAIL");
    }

    @Test
    @DisplayName("Start node output data type is required when source mode metadata is missing")
    void toCreateRequest_rejectsMissingSourceOutputDataType() {
        Map<String, Object> draft = validDraft();
        when(catalogService.findSourceMode("gmail", "new_email"))
                .thenReturn(new SourceMode("new_email", "새 메일", null, "event", Map.of()));

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outputDataType");
    }

    @Test
    @DisplayName("Loop processing method before action is accepted")
    void toCreateRequest_allowsLoopProcessingMethodBeforeAction() {
        Map<String, Object> draft = loopDraft();

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes()).hasSize(4);
        assertThat(request.getNodes().get(1).getType()).isEqualTo("LOOP");
        assertThat(request.getNodes().get(1).getLabel()).isEqualTo("One by one");
        assertThat(request.getNodes().get(1).getDataType()).isEqualTo("EMAIL_LIST");
        assertThat(request.getNodes().get(1).getOutputDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("choiceActionId", "one_by_one");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("choiceNodeType", "LOOP");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("isConfigured", true);
        assertThat(request.getNodes().get(2).getType()).isEqualTo("AI");
        assertThat(request.getNodes().get(2).getDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(request.getNodes().get(2).getOutputDataType()).isEqualTo("TEXT");
        assertThat(request.getNodes().get(2).getConfig()).containsEntry("isConfigured", true);
        assertThat(request.getNodes().getLast().getDataType()).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("List data cannot skip required processing method")
    void toCreateRequest_rejectsListDataDirectlyConnectedToSingleItemAction() {
        Map<String, Object> draft = listDirectToActionDraft();

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported processor action for dataType");
    }

    @Test
    @DisplayName("Generated workflow rejects too many middle nodes")
    void toCreateRequest_rejectsTooManyMiddleNodes() {
        Map<String, Object> draft = tooManyMiddleNodesDraft();

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("up to 3 middle nodes");
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

    private Map<String, Object> loopDraft() {
        return mutableMap(
                "name", "Label mail summary",
                "description", "Summarize label mails and send to Slack",
                "nodes", new java.util.ArrayList<>(List.of(
                        mutableMap(
                                "id", "start",
                                "category", "service",
                                "type", "gmail",
                                "label", "Gmail",
                                "role", "start",
                                "position", Map.of("x", 0, "y", 0),
                                "config", Map.of("source_mode", "label_emails")
                        ),
                        mutableMap(
                                "id", "loop",
                                "category", "logic",
                                "type", "LOOP",
                                "label", "Loop",
                                "role", "middle",
                                "config", Map.of("action", "one_by_one", "isConfigured", false)
                        ),
                        mutableMap(
                                "id", "ai",
                                "category", "logic",
                                "type", "AI",
                                "label", "AI",
                                "role", "middle",
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
                        mutableMap("source", "start", "target", "loop"),
                        mutableMap("source", "loop", "target", "ai"),
                        mutableMap("source", "ai", "target", "end")
                )),
                "trigger", Map.of("type", "manual", "config", Map.of())
        );
    }

    private Map<String, Object> listDirectToActionDraft() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of("source_mode", "label_emails"));
        return draft;
    }

    private Map<String, Object> tooManyMiddleNodesDraft() {
        return mutableMap(
                "name", "Too many middle nodes",
                "nodes", new java.util.ArrayList<>(List.of(
                        mutableMap("id", "start", "category", "service", "type", "gmail", "label", "Gmail",
                                "role", "start", "config", Map.of("source_mode", "new_email")),
                        mutableMap("id", "m1", "category", "logic", "type", "AI", "label", "Summary",
                                "role", "middle", "config", Map.of("action", "summarize")),
                        mutableMap("id", "m2", "category", "logic", "type", "AI", "label", "Summary",
                                "role", "middle", "config", Map.of("action", "summarize")),
                        mutableMap("id", "m3", "category", "logic", "type", "AI", "label", "Summary",
                                "role", "middle", "config", Map.of("action", "summarize")),
                        mutableMap("id", "m4", "category", "logic", "type", "AI", "label", "Summary",
                                "role", "middle", "config", Map.of("action", "summarize")),
                        mutableMap("id", "end", "category", "service", "type", "slack", "label", "Slack",
                                "role", "end", "config", Map.of())
                )),
                "edges", new java.util.ArrayList<>(List.of(
                        mutableMap("source", "start", "target", "m1"),
                        mutableMap("source", "m1", "target", "m2"),
                        mutableMap("source", "m2", "target", "m3"),
                        mutableMap("source", "m3", "target", "m4"),
                        mutableMap("source", "m4", "target", "end")
                ))
        );
    }

    private MappingRules mappingRules() {
        return MappingRules.builder()
                .dataTypes(Map.of(
                        "EMAIL_LIST", DataTypeConfig.builder()
                                .requiresProcessingMethod(true)
                                .processingMethod(ProcessingMethod.builder()
                                        .options(List.of(Option.builder()
                                                .id("one_by_one")
                                                .label("One by one")
                                                .nodeType("LOOP")
                                                .outputDataType("SINGLE_EMAIL")
                                                .priority(1)
                                                .build()))
                                        .build())
                                .actions(List.of())
                                .build(),
                        "SINGLE_EMAIL", DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("summarize")
                                        .label("내용 요약")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .build()))
                                .build(),
                        "SINGLE_FILE", DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("summarize")
                                        .label("내용 요약/정리")
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
