package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.picker.WebFeedSourceRegistry;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.ChoicePromptResolver;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.BranchConfig;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.FollowUp;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.github.flowify.workflow.service.choice.dto.Option;
import org.github.flowify.workflow.service.choice.dto.ProcessingMethod;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.service.generation.WorkflowGenerationResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
        ChoicePromptResolver choicePromptResolver = new ChoicePromptResolver(new ObjectMapper());
        ReflectionTestUtils.setField(choicePromptResolver, "promptRulesPath", "docs/ai_prompt_rules.json");
        ReflectionTestUtils.invokeMethod(choicePromptResolver, "loadPromptRules");
        when(choiceMappingService.getMappingRules()).thenReturn(mappingRules());
        when(catalogService.findSourceMode("gmail", "new_email"))
                .thenReturn(new SourceMode("new_email", "새 메일", "SINGLE_EMAIL", "event", Map.of()));
        when(catalogService.findSourceMode("gmail", "label_emails"))
                .thenReturn(new SourceMode("label_emails", "Label emails", "EMAIL_LIST", "manual", Map.of()));
        when(catalogService.findSourceMode("gmail", "sender_email"))
                .thenReturn(new SourceMode("sender_email", "Sender email", "SINGLE_EMAIL", "event",
                        Map.of("type", "text_input", "required", true, "validation", "email")));
        when(catalogService.findSourceMode("google_drive", "single_file"))
                .thenReturn(new SourceMode("single_file", "Single file", "SINGLE_FILE", "manual",
                        Map.of("type", "file_picker")));
        when(catalogService.findSourceMode("google_drive", "folder_all_files"))
                .thenReturn(new SourceMode("folder_all_files", "Folder files", "FILE_LIST", "manual",
                        Map.of("type", "folder_picker")));
        when(catalogService.findSourceMode("naver_news", "article_search"))
                .thenReturn(new SourceMode("article_search", "Article search", "ARTICLE_LIST", "manual",
                        Map.of("type", "text_input")));
        when(catalogService.findSourceMode("web_news", "website_feed"))
                .thenReturn(new SourceMode("website_feed", "Website feed", "ARTICLE_LIST", "manual",
                        Map.of("type", "feed_source_picker", "keyword_supported", true)));
        when(catalogService.findSourceMode("google_sheets", "sheet_all"))
                .thenReturn(new SourceMode("sheet_all", "Sheet all", "SPREADSHEET_DATA", "manual",
                        Map.of("type", "sheet_picker")));
        when(catalogService.findSourceMode("github", "new_pr"))
                .thenReturn(new SourceMode("new_pr", "New pull request", "API_RESPONSE", "event",
                        Map.of("type", "text_input", "validation", "github_repo")));
        when(catalogService.findSinkService("slack"))
                .thenReturn(new SinkService(
                        "slack",
                        "Slack",
                        true,
                        List.of("SINGLE_EMAIL", "TEXT"),
                        "per_service",
                        Map.of()
                ));
        when(catalogService.findSinkService("discord"))
                .thenReturn(new SinkService(
                        "discord",
                        "Discord",
                        false,
                        List.of("TEXT"),
                        "per_service",
                        Map.of("fields", List.of(
                                Map.of("key", "webhook_url", "type", "secret_text", "required", true),
                                Map.of("key", "message_template", "type", "textarea", "required", false),
                                Map.of("key", "avatar_url", "type", "text", "required", false)
                        ))
                ));
        when(catalogService.findSinkService("gmail"))
                .thenReturn(new SinkService(
                        "gmail",
                        "Gmail",
                        true,
                        List.of("TEXT", "SINGLE_FILE", "FILE_LIST"),
                        "per_service",
                        Map.of("fields", List.of(
                                Map.of("key", "to", "type", "email_input", "required", true),
                                Map.of("key", "subject", "type", "text", "required", true),
                                Map.of(
                                        "key", "body_format",
                                        "type", "select",
                                        "options", List.of("plain", "html"),
                                        "required", false
                                ),
                                Map.of(
                                        "key", "text_delivery_mode",
                                        "type", "select",
                                        "options", List.of("body", "attachment"),
                                        "required", false
                                ),
                                Map.of(
                                        "key", "action",
                                        "type", "select",
                                        "options", List.of("send"),
                                        "required", true
                                )
                        ))
                ));
        when(catalogService.findSinkService("google_drive"))
                .thenReturn(new SinkService(
                        "google_drive",
                        "Google Drive",
                        true,
                        List.of("TEXT", "SINGLE_FILE", "FILE_LIST", "SPREADSHEET_DATA"),
                        "per_service",
                        Map.of("fields", List.of(
                                Map.of("key", "folder_id", "type", "folder_picker", "required", true),
                                Map.of("key", "filename_template", "type", "text", "required", false),
                                Map.of(
                                        "key", "file_format",
                                        "type", "select",
                                        "options", List.of("pdf", "docx", "txt", "original"),
                                        "required", false
                                )
                        ))
                ));
        when(catalogService.findSinkService("google_sheets"))
                .thenReturn(new SinkService(
                        "google_sheets",
                        "Google Sheets",
                        true,
                        List.of("TEXT", "SPREADSHEET_DATA", "API_RESPONSE"),
                        "per_service",
                        Map.of("fields", List.of(
                                Map.of("key", "spreadsheet_id", "type", "sheet_picker", "required", true),
                                Map.of("key", "sheet_name", "type", "text", "required", false),
                                Map.of("key", "key_column", "type", "text", "required", false),
                                Map.of(
                                        "key", "write_mode",
                                        "type", "select",
                                        "options", List.of("append_rows", "overwrite_range",
                                                "update_row_by_key", "upsert_row_by_key"),
                                        "required", true
                                )
                        ))
                ));
        service = new WorkflowGenerationResultService(
                new ObjectMapper(),
                new WorkflowValidator(),
                choiceMappingService,
                choicePromptResolver,
                catalogService,
                new WebFeedSourceRegistry(new ObjectMapper())
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
    @DisplayName("Generated AI action with follow-up stays pending without generation-ready flag")
    void toCreateRequest_keepsFollowUpActionPendingWithoutGenerationReadyFlag() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("type", "google_drive");
        nodes.getFirst().put("config", Map.of("source_mode", "single_file"));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().get(1).getDataType()).isEqualTo("SINGLE_FILE");
        assertThat(request.getNodes().get(1).getConfig()).containsEntry("isConfigured", false);
    }

    @Test
    @DisplayName("Article list loop can summarize each text item")
    void toCreateRequest_allowsArticleListLoopToTextSummarize() {
        Map<String, Object> draft = articleLoopDraft();

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes()).hasSize(4);
        assertThat(request.getNodes().get(1).getType()).isEqualTo("LOOP");
        assertThat(request.getNodes().get(1).getDataType()).isEqualTo("ARTICLE_LIST");
        assertThat(request.getNodes().get(1).getOutputDataType()).isEqualTo("TEXT");
        assertThat(request.getNodes().get(2).getType()).isEqualTo("AI");
        assertThat(request.getNodes().get(2).getDataType()).isEqualTo("TEXT");
        assertThat(request.getNodes().get(2).getOutputDataType()).isEqualTo("TEXT");
        assertThat(request.getNodes().get(2).getConfig()).containsEntry("choiceActionId", "ai_summarize");
        assertThat(request.getNodes().get(2).getConfig()).containsEntry("isConfigured", true);
    }

    @Test
    @DisplayName("Article list cannot connect directly to AI summarize in generated drafts")
    void toCreateRequest_rejectsArticleListDirectlyConnectedToAiSummarize() {
        Map<String, Object> draft = articleDirectToAiDraft();

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported processor action for dataType");
    }

    @Test
    @DisplayName("Runtime-unsupported data filter actions are rejected for generated drafts")
    void toCreateRequest_rejectsUnsupportedGeneratedDataFilterAction() {
        Map<String, Object> draft = unsupportedDataFilterDraft();

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported processor action");
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
                .hasMessageContaining("up to 15 middle nodes");
    }

    @Test
    @DisplayName("File type branch can route PDF and archive paths to separate sinks")
    void toCreateRequest_allowsFileTypeBranchToMultipleSinks() {
        Map<String, Object> draft = fileTypeBranchDraft();

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes()).hasSize(6);
        assertThat(request.getNodes()).filteredOn(node -> "end".equals(node.getRole())).hasSize(2);
        assertThat(request.getNodes().get(1).getType()).isEqualTo("CONDITION_BRANCH");
        assertThat(request.getNodes().get(1).getConfig())
                .containsEntry("choiceActionId", "branch_by_file_type")
                .containsEntry("choiceNodeType", "CONDITION_BRANCH")
                .containsEntry("isConfigured", true);
        assertThat(request.getEdges())
                .anySatisfy(edge -> assertThat(edge)
                        .hasFieldOrPropertyWithValue("source", "branch")
                        .hasFieldOrPropertyWithValue("target", "loop_pdf")
                        .hasFieldOrPropertyWithValue("label", "pdf")
                        .hasFieldOrPropertyWithValue("sourceHandle", "pdf")
                        .hasFieldOrPropertyWithValue("targetHandle", "input"))
                .anySatisfy(edge -> assertThat(edge)
                        .hasFieldOrPropertyWithValue("source", "branch")
                        .hasFieldOrPropertyWithValue("target", "gmail_archive")
                        .hasFieldOrPropertyWithValue("label", "archive")
                        .hasFieldOrPropertyWithValue("sourceHandle", "archive")
                        .hasFieldOrPropertyWithValue("targetHandle", "input"));
    }

    @Test
    @DisplayName("Generated branch requires explicit branch selections")
    void toCreateRequest_rejectsBranchWithoutSelections() {
        Map<String, Object> draft = fileTypeBranchDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) nodes.get(1).get("config");
        config.remove("choiceSelections");

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Branch node choice selections are required");
    }

    @Test
    @DisplayName("Generated branch edge labels must match selected branch config")
    void toCreateRequest_rejectsUnselectedBranchEdgeLabel() {
        Map<String, Object> draft = fileTypeBranchDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) draft.get("edges");
        edges.get(1).put("label", "image");
        edges.get(1).put("sourceHandle", "image");

        assertThatThrownBy(() -> service.toCreateRequest(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Branch edge label is not selected");
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

    @Test
    @DisplayName("Generated start node removes fake picker target config")
    void toCreateRequest_sanitizesFakeStartPickerTarget() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("type", "google_drive");
        nodes.getFirst().put("config", Map.of(
                "source_mode", "single_file",
                "target", "fake_file_id",
                "target_label", "Fake file",
                "target_meta", Map.of("name", "Fake file"),
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getFirst().getConfig();
        assertThat(config)
                .containsEntry("service", "google_drive")
                .containsEntry("source_mode", "single_file")
                .containsEntry("isConfigured", false)
                .doesNotContainKeys("target", "target_label", "target_meta");
    }

    @Test
    @DisplayName("Generated public text source target is preserved")
    void toCreateRequest_keepsPublicTextSourceTarget() {
        Map<String, Object> draft = articleLoopDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("type", "naver_news");
        nodes.getFirst().put("config", Map.of(
                "source_mode", "article_search",
                "target", "AI workflow",
                "isConfigured", false
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getFirst().getConfig();
        assertThat(config)
                .containsEntry("service", "naver_news")
                .containsEntry("source_mode", "article_search")
                .containsEntry("target", "AI workflow")
                .containsEntry("isConfigured", true);
    }

    @Test
    @DisplayName("Generated Gmail sender_email source keeps sender target and removes source-level keyword")
    void toCreateRequest_keepsGmailSenderEmailTargetOnly() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of(
                "source_mode", "sender_email",
                "target", "sender@example.com",
                "keyword", "invoice",
                "target_label", "Sender",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getFirst().getConfig();
        assertThat(config)
                .containsEntry("service", "gmail")
                .containsEntry("source_mode", "sender_email")
                .containsEntry("target", "sender@example.com")
                .containsEntry("isConfigured", true)
                .doesNotContainKeys("keyword", "target_label");
        assertThat(request.getNodes().getFirst().getOutputDataType()).isEqualTo("SINGLE_EMAIL");
    }

    @Test
    @DisplayName("Generated feed source keeps keyword but removes fake source targets")
    void toCreateRequest_sanitizesFeedSourceTargetsButKeepsKeyword() {
        Map<String, Object> draft = articleLoopDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of(
                "source_mode", "website_feed",
                "target", "https://example.com/rss",
                "targets", List.of("https://example.com/rss"),
                "target_label", "Example RSS",
                "keyword", "AI",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getFirst().getConfig();
        assertThat(config)
                .containsEntry("service", "web_news")
                .containsEntry("source_mode", "website_feed")
                .containsEntry("keyword", "AI")
                .containsEntry("isConfigured", false)
                .doesNotContainKeys("target", "targets", "target_label");
    }

    @Test
    @DisplayName("Generated feed source preset ids are converted to picker config")
    void toCreateRequest_convertsFeedSourcePresetIdsToPickerConfig() {
        Map<String, Object> draft = articleLoopDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of(
                "source_mode", "website_feed",
                "target_preset_ids", List.of("bbc_news"),
                "target", "https://fake.example.com/rss",
                "targets", List.of("https://fake.example.com/rss"),
                "target_label", "Fake RSS",
                "target_meta", Map.of("label", "Fake RSS"),
                "keyword", "world",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getFirst().getConfig();
        assertThat(config)
                .containsEntry("service", "web_news")
                .containsEntry("source_mode", "website_feed")
                .containsEntry("target", "https://feeds.bbci.co.uk/news/rss.xml")
                .containsEntry("targets", List.of("https://feeds.bbci.co.uk/news/rss.xml"))
                .containsEntry("target_label", "BBC News")
                .containsEntry("keyword", "world")
                .containsEntry("isConfigured", true)
                .doesNotContainKeys("target_preset_ids", "custom_target_urls");

        @SuppressWarnings("unchecked")
        Map<String, Object> targetMeta = (Map<String, Object>) config.get("target_meta");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> selectedSources =
                (List<Map<String, Object>>) targetMeta.get("selectedSources");
        assertThat(selectedSources).singleElement()
                .satisfies(source -> assertThat(source)
                        .containsEntry("presetId", "bbc_news")
                        .containsEntry("label", "BBC News")
                        .containsEntry("url", "https://feeds.bbci.co.uk/news/rss.xml"));
    }

    @Test
    @DisplayName("Generated feed source custom URLs must be present in prompt")
    void toCreateRequest_keepsOnlyPromptCustomFeedSourceUrls() {
        Map<String, Object> draft = articleLoopDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getFirst().put("config", Map.of(
                "source_mode", "website_feed",
                "custom_target_urls", List.of(
                        "https://example.com/rss.xml",
                        "https://fake.example.com/rss.xml"
                ),
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(
                draft,
                "Use this RSS feed: https://example.com/rss.xml"
        );

        Map<String, Object> config = request.getNodes().getFirst().getConfig();
        assertThat(config)
                .containsEntry("target", "https://example.com/rss.xml")
                .containsEntry("targets", List.of("https://example.com/rss.xml"))
                .containsEntry("target_label", "https://example.com/rss.xml")
                .containsEntry("isConfigured", true)
                .doesNotContainKeys("target_preset_ids", "custom_target_urls");

        @SuppressWarnings("unchecked")
        Map<String, Object> targetMeta = (Map<String, Object>) config.get("target_meta");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customSources =
                (List<Map<String, Object>>) targetMeta.get("customSources");
        assertThat(customSources).singleElement()
                .satisfies(source -> assertThat(source)
                        .containsEntry("label", "https://example.com/rss.xml")
                        .containsEntry("url", "https://example.com/rss.xml"));
    }

    @Test
    @DisplayName("GitHub PR draft accepts owner repo target")
    void toCreateRequest_allowsGithubPrSummaryWithOwnerRepoTarget() {
        Map<String, Object> draft = githubPrDraft("openai/openai-python");

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getFirst().getOutputDataType()).isEqualTo("API_RESPONSE");
        assertThat(request.getNodes().getFirst().getConfig())
                .containsEntry("service", "github")
                .containsEntry("source_mode", "new_pr")
                .containsEntry("target", "openai/openai-python")
                .containsEntry("isConfigured", true);
        assertThat(request.getNodes().get(1).getDataType()).isEqualTo("API_RESPONSE");
        assertThat(request.getNodes().get(1).getConfig())
                .containsEntry("choiceActionId", "ai_analyze")
                .containsEntry("choiceNodeType", "AI")
                .containsEntry("isConfigured", true);
    }

    @Test
    @DisplayName("GitHub PR draft normalizes repository URL target")
    void toCreateRequest_normalizesGithubPrUrlTarget() {
        Map<String, Object> draft = githubPrDraft("https://github.com/openai/openai-python/pulls");

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getFirst().getConfig())
                .containsEntry("target", "openai/openai-python")
                .containsEntry("isConfigured", true);
    }

    @Test
    @DisplayName("GitHub PR draft stays pending without repository target")
    void toCreateRequest_marksGithubPrPendingWithoutTarget() {
        Map<String, Object> draft = githubPrDraft("");

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getFirst().getConfig())
                .containsEntry("target", "")
                .containsEntry("isConfigured", false);
    }

    @Test
    @DisplayName("GitHub PR draft stays pending with invalid repository target")
    void toCreateRequest_marksGithubPrPendingWithInvalidTarget() {
        Map<String, Object> draft = githubPrDraft("new PR");

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        assertThat(request.getNodes().getFirst().getConfig())
                .containsEntry("target", "new PR")
                .containsEntry("isConfigured", false);
    }

    @Test
    @DisplayName("Generated Google Drive sink removes fake folder id")
    void toCreateRequest_sanitizesFakeGoogleDriveFolderId() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("type", "google_drive");
        nodes.getLast().put("config", Map.of(
                "folder_id", "fake_folder_id",
                "folder_id_label", "Reports",
                "filename_template", "summary.txt",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getLast().getConfig();
        assertThat(config)
                .containsEntry("service", "google_drive")
                .containsEntry("filename_template", "summary.txt")
                .containsEntry("isConfigured", false)
                .doesNotContainKeys("folder_id", "folder_id_label");
    }

    @Test
    @DisplayName("Generated Discord sink removes fake webhook url")
    void toCreateRequest_sanitizesFakeDiscordWebhookUrl() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("type", "discord");
        nodes.getLast().put("config", Map.of(
                "webhook_url", "https://discord.com/api/webhooks/fake",
                "message_template", "{{text}}",
                "avatar_url", "https://example.com/avatar.png",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getLast().getConfig();
        assertThat(config)
                .containsEntry("service", "discord")
                .containsEntry("message_template", "{{text}}")
                .containsEntry("isConfigured", false)
                .doesNotContainKeys("webhook_url", "avatar_url");
    }

    @Test
    @DisplayName("Generated Gmail sink keeps explicit email recipient")
    void toCreateRequest_keepsGeneratedGmailRecipient() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("type", "gmail");
        nodes.getLast().put("config", Map.of(
                "to", "receiver@example.com",
                "subject", "요약 결과",
                "body_format", "plain",
                "text_delivery_mode", "body",
                "action", "send",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getLast().getConfig();
        assertThat(config)
                .containsEntry("service", "gmail")
                .containsEntry("to", "receiver@example.com")
                .containsEntry("subject", "요약 결과")
                .containsEntry("body_format", "plain")
                .containsEntry("text_delivery_mode", "body")
                .containsEntry("action", "send")
                .containsEntry("isConfigured", true);
    }

    @Test
    @DisplayName("Generated Gmail sink removes invalid recipient")
    void toCreateRequest_sanitizesInvalidGeneratedGmailRecipient() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("type", "gmail");
        nodes.getLast().put("config", Map.of(
                "to", "내 메일",
                "subject", "요약 결과",
                "body_format", "plain",
                "text_delivery_mode", "body",
                "action", "send",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getLast().getConfig();
        assertThat(config)
                .containsEntry("service", "gmail")
                .containsEntry("subject", "요약 결과")
                .containsEntry("body_format", "plain")
                .containsEntry("text_delivery_mode", "body")
                .containsEntry("action", "send")
                .containsEntry("isConfigured", false)
                .doesNotContainKey("to");
    }

    @Test
    @DisplayName("Generated Gmail sink allows current user email recipient source")
    void toCreateRequest_allowsCurrentUserEmailRecipientSource() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("type", "gmail");
        nodes.getLast().put("config", Map.of(
                "to", "",
                "to_source", "current_user_email",
                "subject", "?붿빟 寃곌낵",
                "body_format", "plain",
                "text_delivery_mode", "body",
                "action", "send",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getLast().getConfig();
        assertThat(config)
                .containsEntry("service", "gmail")
                .containsEntry("to_source", "current_user_email")
                .containsEntry("subject", "?붿빟 寃곌낵")
                .containsEntry("body_format", "plain")
                .containsEntry("text_delivery_mode", "body")
                .containsEntry("action", "send")
                .containsEntry("isConfigured", true)
                .doesNotContainKey("to");
    }

    @Test
    @DisplayName("Generated Gmail sink prefers explicit recipient over current user source")
    void toCreateRequest_prefersExplicitGmailRecipientOverCurrentUserSource() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("type", "gmail");
        nodes.getLast().put("config", Map.of(
                "to", "receiver@example.com",
                "to_source", "current_user_email",
                "subject", "?붿빟 寃곌낵",
                "body_format", "plain",
                "text_delivery_mode", "body",
                "action", "send",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getLast().getConfig();
        assertThat(config)
                .containsEntry("service", "gmail")
                .containsEntry("to", "receiver@example.com")
                .containsEntry("isConfigured", true)
                .doesNotContainKey("to_source");
    }

    @Test
    @DisplayName("Generated Google Sheets sink removes fake spreadsheet config")
    void toCreateRequest_sanitizesFakeGoogleSheetsConfig() {
        Map<String, Object> draft = validDraft();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) draft.get("nodes");
        nodes.getLast().put("type", "google_sheets");
        nodes.getLast().put("config", Map.of(
                "spreadsheet_id", "fake_spreadsheet_id",
                "sheet_name", "Sheet1",
                "key_column", "id",
                "write_mode", "append_rows",
                "isConfigured", true
        ));

        WorkflowCreateRequest request = service.toCreateRequest(draft);

        Map<String, Object> config = request.getNodes().getLast().getConfig();
        assertThat(config)
                .containsEntry("service", "google_sheets")
                .containsEntry("write_mode", "append_rows")
                .containsEntry("isConfigured", false)
                .doesNotContainKeys("spreadsheet_id", "sheet_name", "key_column");
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

    private Map<String, Object> githubPrDraft(String target) {
        return mutableMap(
                "name", "GitHub PR summary",
                "description", "Summarize new pull requests and send to Discord",
                "nodes", new java.util.ArrayList<>(List.of(
                        mutableMap(
                                "id", "start",
                                "category", "service",
                                "type", "github",
                                "label", "GitHub",
                                "role", "start",
                                "position", Map.of("x", 0, "y", 0),
                                "config", Map.of(
                                        "source_mode", "new_pr",
                                        "target", target,
                                        "isConfigured", true
                                ),
                                "outputDataType", "API_RESPONSE"
                        ),
                        mutableMap(
                                "id", "ai",
                                "category", "logic",
                                "type", "AI",
                                "label", "AI",
                                "role", "middle",
                                "config", Map.of("action", "ai_analyze")
                        ),
                        mutableMap(
                                "id", "end",
                                "category", "service",
                                "type", "discord",
                                "label", "Discord",
                                "role", "end",
                                "config", Map.of("isConfigured", false)
                        )
                )),
                "edges", new java.util.ArrayList<>(List.of(
                        mutableMap("source", "start", "target", "ai"),
                        mutableMap("source", "ai", "target", "end")
                )),
                "trigger", Map.of("type", "manual", "config", Map.of())
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

    private Map<String, Object> articleLoopDraft() {
        return mutableMap(
                "name", "News summary",
                "description", "Summarize each article and send to Slack",
                "nodes", new java.util.ArrayList<>(List.of(
                        mutableMap(
                                "id", "start",
                                "category", "service",
                                "type", "web_news",
                                "label", "Web news",
                                "role", "start",
                                "position", Map.of("x", 0, "y", 0),
                                "config", Map.of("source_mode", "website_feed")
                        ),
                        mutableMap(
                                "id", "loop",
                                "category", "logic",
                                "type", "LOOP",
                                "label", "Loop",
                                "role", "middle",
                                "config", Map.of("action", "one_by_one")
                        ),
                        mutableMap(
                                "id", "ai",
                                "category", "logic",
                                "type", "AI",
                                "label", "AI",
                                "role", "middle",
                                "config", Map.of("action", "ai_summarize")
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

    private Map<String, Object> articleDirectToAiDraft() {
        return mutableMap(
                "name", "News summary",
                "description", "Summarize articles directly",
                "nodes", new java.util.ArrayList<>(List.of(
                        mutableMap(
                                "id", "start",
                                "category", "service",
                                "type", "web_news",
                                "label", "Web news",
                                "role", "start",
                                "position", Map.of("x", 0, "y", 0),
                                "config", Map.of("source_mode", "website_feed")
                        ),
                        mutableMap(
                                "id", "ai",
                                "category", "logic",
                                "type", "AI",
                                "label", "AI",
                                "role", "middle",
                                "config", Map.of("action", "ai_summarize")
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
                        mutableMap("source", "ai", "target", "end")
                )),
                "trigger", Map.of("type", "manual", "config", Map.of())
        );
    }

    private Map<String, Object> unsupportedDataFilterDraft() {
        return mutableMap(
                "name", "Unsupported data filter",
                "nodes", new java.util.ArrayList<>(List.of(
                        mutableMap(
                                "id", "start",
                                "category", "service",
                                "type", "google_sheets",
                                "label", "Google Sheets",
                                "role", "start",
                                "config", Map.of("source_mode", "sheet_all")
                        ),
                        mutableMap(
                                "id", "filter",
                                "category", "logic",
                                "type", "DATA_FILTER",
                                "label", "조건 필터",
                                "role", "middle",
                                "config", Map.of("action", "filter_condition")
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
                        mutableMap("source", "start", "target", "filter"),
                        mutableMap("source", "filter", "target", "end")
                ))
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
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();
        nodes.add(mutableMap("id", "start", "category", "service", "type", "gmail", "label", "Gmail",
                "role", "start", "config", Map.of("source_mode", "new_email")));
        String previous = "start";
        for (int index = 1; index <= 16; index++) {
            String nodeId = "m" + index;
            nodes.add(mutableMap("id", nodeId, "category", "logic", "type", "AI", "label", "Summary",
                    "role", "middle", "config", Map.of("action", "summarize")));
            edges.add(mutableMap("source", previous, "target", nodeId));
            previous = nodeId;
        }
        nodes.add(mutableMap("id", "end", "category", "service", "type", "slack", "label", "Slack",
                "role", "end", "config", Map.of()));
        edges.add(mutableMap("source", previous, "target", "end"));

        return mutableMap(
                "name", "Too many middle nodes",
                "nodes", nodes,
                "edges", edges
        );
    }

    private Map<String, Object> fileTypeBranchDraft() {
        return mutableMap(
                "name", "File branch",
                "nodes", new java.util.ArrayList<>(List.of(
                        mutableMap(
                                "id", "start",
                                "category", "service",
                                "type", "google_drive",
                                "label", "Folder files",
                                "role", "start",
                                "config", Map.of(
                                        "service", "google_drive",
                                        "source_mode", "folder_all_files",
                                        "target", "",
                                        "isConfigured", false
                                ),
                                "outputDataType", "FILE_LIST"
                        ),
                        mutableMap(
                                "id", "branch",
                                "category", "logic",
                                "type", "CONDITION_BRANCH",
                                "label", "File type branch",
                                "role", "middle",
                                "config", new java.util.LinkedHashMap<>(Map.of(
                                        "choiceActionId", "branch_by_file_type",
                                        "choiceNodeType", "CONDITION_BRANCH",
                                        "choiceSelections", Map.of("branch_config", List.of("pdf", "archive")),
                                        "isConfigured", true
                                )),
                                "dataType", "FILE_LIST",
                                "outputDataType", "FILE_LIST"
                        ),
                        mutableMap(
                                "id", "loop_pdf",
                                "category", "logic",
                                "type", "LOOP",
                                "label", "Each PDF",
                                "role", "middle",
                                "config", Map.of(
                                        "choiceActionId", "one_by_one",
                                        "choiceNodeType", "LOOP",
                                        "isConfigured", true
                                ),
                                "dataType", "FILE_LIST",
                                "outputDataType", "SINGLE_FILE"
                        ),
                        mutableMap(
                                "id", "ai_pdf",
                                "category", "logic",
                                "type", "AI",
                                "label", "PDF summary",
                                "role", "middle",
                                "config", Map.of(
                                        "choiceActionId", "summarize",
                                        "choiceNodeType", "AI",
                                        "isConfigured", true
                                ),
                                "dataType", "SINGLE_FILE",
                                "outputDataType", "TEXT"
                        ),
                        mutableMap(
                                "id", "drive_pdf",
                                "category", "service",
                                "type", "google_drive",
                                "label", "Save summary",
                                "role", "end",
                                "config", Map.of(
                                        "service", "google_drive",
                                        "folder_id", "",
                                        "filename_template", "summary.txt",
                                        "file_format", "txt",
                                        "isConfigured", false
                                ),
                                "dataType", "TEXT"
                        ),
                        mutableMap(
                                "id", "gmail_archive",
                                "category", "service",
                                "type", "gmail",
                                "label", "Send archive",
                                "role", "end",
                                "config", Map.of(
                                        "service", "gmail",
                                        "to", "receiver@example.com",
                                        "subject", "Archive files",
                                        "body_format", "plain",
                                        "text_delivery_mode", "attachment",
                                        "action", "send",
                                        "isConfigured", true
                                ),
                                "dataType", "FILE_LIST"
                        )
                )),
                "edges", new java.util.ArrayList<>(List.of(
                        mutableMap("source", "start", "target", "branch"),
                        mutableMap(
                                "source", "branch",
                                "target", "loop_pdf",
                                "label", "pdf",
                                "sourceHandle", "pdf",
                                "targetHandle", "input"
                        ),
                        mutableMap("source", "loop_pdf", "target", "ai_pdf"),
                        mutableMap("source", "ai_pdf", "target", "drive_pdf"),
                        mutableMap(
                                "source", "branch",
                                "target", "gmail_archive",
                                "label", "archive",
                                "sourceHandle", "archive",
                                "targetHandle", "input"
                        )
                ))
        );
    }

    private MappingRules mappingRules() {
        return MappingRules.builder()
                .dataTypes(Map.of(
                        "FILE_LIST", DataTypeConfig.builder()
                                .requiresProcessingMethod(true)
                                .processingMethod(ProcessingMethod.builder()
                                        .options(List.of(
                                                Option.builder()
                                                        .id("one_by_one")
                                                        .label("One by one")
                                                        .nodeType("LOOP")
                                                        .outputDataType("SINGLE_FILE")
                                                        .priority(1)
                                                        .build(),
                                                Option.builder()
                                                        .id("branch_by_file_type")
                                                        .label("Branch by file type")
                                                        .nodeType("CONDITION_BRANCH")
                                                        .outputDataType("FILE_LIST")
                                                        .priority(2)
                                                        .branchConfig(BranchConfig.builder()
                                                                .options(List.of(
                                                                        Option.builder().id("pdf").label("PDF").build(),
                                                                        Option.builder().id("archive").label("Archive").build(),
                                                                        Option.builder().id("other").label("Other").build()
                                                                ))
                                                                .build())
                                                        .build()
                                        ))
                                        .build())
                                .actions(List.of())
                                .build(),
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
                                        .generationReadyWithoutFollowUp(true)
                                        .followUp(summaryFollowUp())
                                        .build()))
                                .build(),
                        "SINGLE_FILE", DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("summarize")
                                        .label("내용 요약/정리")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .followUp(summaryFollowUp())
                                        .build()))
                                .build(),
                        "ARTICLE_LIST", DataTypeConfig.builder()
                                .requiresProcessingMethod(true)
                                .processingMethod(ProcessingMethod.builder()
                                        .options(List.of(Option.builder()
                                                .id("one_by_one")
                                                .label("글 하나씩 처리")
                                                .nodeType("LOOP")
                                                .outputDataType("TEXT")
                                                .priority(1)
                                                .build()))
                                        .build())
                                .actions(List.of(Action.builder()
                                        .id("ai_summarize")
                                        .label("AI로 요약")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .build()))
                                .build(),
                        "TEXT", DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("ai_summarize")
                                        .label("AI로 요약")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .generationReadyWithoutFollowUp(true)
                                        .followUp(summaryFollowUp())
                                        .build()))
                                .build(),
                        "API_RESPONSE", DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("ai_analyze")
                                        .label("AI analysis")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .generationReadyWithoutFollowUp(true)
                                        .followUp(summaryFollowUp())
                                        .build()))
                                .build(),
                        "SPREADSHEET_DATA", DataTypeConfig.builder()
                                .actions(List.of(
                                        Action.builder()
                                                .id("filter_condition")
                                                .label("조건 필터")
                                                .nodeType("DATA_FILTER")
                                                .outputDataType("SPREADSHEET_DATA")
                                                .build(),
                                        Action.builder()
                                                .id("filter_fields")
                                                .label("필드 선택")
                                                .nodeType("DATA_FILTER")
                                                .outputDataType("SPREADSHEET_DATA")
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }

    private FollowUp summaryFollowUp() {
        return FollowUp.builder()
                .question("summary format")
                .options(List.of(Option.builder().id("brief").label("Brief").build()))
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
