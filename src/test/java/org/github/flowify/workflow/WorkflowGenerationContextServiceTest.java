package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.catalog.dto.SinkCatalog;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceCatalog;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.picker.WebFeedSourceRegistry;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.BranchConfig;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.github.flowify.workflow.service.choice.dto.Option;
import org.github.flowify.workflow.service.choice.dto.ProcessingMethod;
import org.github.flowify.workflow.service.generation.WorkflowGenerationContextService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowGenerationContextServiceTest {

    @Test
    void buildContext_includesProcessingMethodsForListDataTypes() {
        CatalogService catalogService = mock(CatalogService.class);
        ChoiceMappingService choiceMappingService = mock(ChoiceMappingService.class);
        when(catalogService.getSourceCatalog()).thenReturn(new SourceCatalog(
                new SourceCatalog.Meta("test", "now"),
                List.of(new SourceService(
                        "gmail",
                        "Gmail",
                        true,
                        List.of(new SourceMode("label_emails", "Label emails", "EMAIL_LIST", "manual", Map.of()))
                ))
        ));
        when(catalogService.getSinkCatalog()).thenReturn(new SinkCatalog(
                new SourceCatalog.Meta("test", "now"),
                List.of(new SinkService("slack", "Slack", true, List.of("TEXT"), "per_service", Map.of()))
        ));
        when(catalogService.getSinkRequiredFields("slack")).thenReturn(List.of("channel"));
        when(choiceMappingService.getMappingRules()).thenReturn(mappingRules());

        WorkflowGenerationContextService service = new WorkflowGenerationContextService(
                catalogService,
                choiceMappingService,
                webFeedSourceRegistry()
        );

        Map<String, Object> context = service.buildContext();

        @SuppressWarnings("unchecked")
        Map<String, Object> topology = (Map<String, Object>) context.get("topology");
        assertThat(topology).containsEntry("maxMiddleCount", 15);
        assertThat(topology).containsEntry("maxEndCount", 7);
        assertThat(topology).containsEntry("allowLoop", true);
        assertThat(topology).containsEntry("allowBranch", true);
        assertThat(topology).containsEntry("allowMultipleSinks", true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processors = (List<Map<String, Object>>) context.get("processors");
        Map<String, Object> fileListSpec = processors.stream()
                .filter(spec -> "FILE_LIST".equals(spec.get("inputDataType")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fileListMethods =
                (List<Map<String, Object>>) fileListSpec.get("processingMethods");
        assertThat(fileListMethods)
                .extracting(method -> method.get("id"))
                .contains("branch_by_file_type");

        Map<String, Object> emailListSpec = processors.stream()
                .filter(spec -> "EMAIL_LIST".equals(spec.get("inputDataType")))
                .findFirst()
                .orElseThrow();
        assertThat(emailListSpec).containsEntry("requiresProcessingMethod", true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processingMethods =
                (List<Map<String, Object>>) emailListSpec.get("processingMethods");
        assertThat(processingMethods).singleElement()
                .satisfies(method -> assertThat(method)
                        .containsEntry("id", "one_by_one")
                        .containsEntry("nodeType", "LOOP")
                        .containsEntry("outputDataType", "SINGLE_EMAIL"));

        Map<String, Object> singleEmailSpec = processors.stream()
                .filter(spec -> "SINGLE_EMAIL".equals(spec.get("inputDataType")))
                .findFirst()
                .orElseThrow();
        assertThat(singleEmailSpec).containsEntry("requiresProcessingMethod", false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) singleEmailSpec.get("actions");
        assertThat(actions).singleElement()
                .satisfies(action -> assertThat(action)
                        .containsEntry("id", "summarize")
                        .containsEntry("nodeType", "AI")
                        .containsEntry("outputDataType", "TEXT"));

        Map<String, Object> spreadsheetSpec = processors.stream()
                .filter(spec -> "SPREADSHEET_DATA".equals(spec.get("inputDataType")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> spreadsheetActions =
                (List<Map<String, Object>>) spreadsheetSpec.get("actions");
        assertThat(spreadsheetActions)
                .extracting(action -> action.get("id"))
                .contains("filter_fields")
                .doesNotContain("filter_condition");

        @SuppressWarnings("unchecked")
        Map<String, Object> contractTables = (Map<String, Object>) context.get("contractTables");
        assertThat(contractTables).containsKeys("sourceOutputs", "processorTransitions", "sinkInputs");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourceOutputs =
                (List<Map<String, Object>>) contractTables.get("sourceOutputs");
        assertThat(sourceOutputs).singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("service", "gmail")
                        .containsEntry("sourceMode", "label_emails")
                        .containsEntry("outputDataType", "EMAIL_LIST"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processorTransitions =
                (List<Map<String, Object>>) contractTables.get("processorTransitions");
        assertThat(processorTransitions)
                .anySatisfy(row -> assertThat(row)
                        .containsEntry("inputDataType", "EMAIL_LIST")
                        .containsEntry("stepKind", "processing_method")
                        .containsEntry("id", "one_by_one")
                        .containsEntry("nodeType", "LOOP")
                        .containsEntry("outputDataType", "SINGLE_EMAIL"))
                .anySatisfy(row -> assertThat(row)
                        .containsEntry("inputDataType", "TEXT")
                        .containsEntry("stepKind", "action")
                        .containsEntry("id", "ai_summarize")
                        .containsEntry("nodeType", "AI")
                        .containsEntry("outputDataType", "TEXT"))
                .noneSatisfy(row -> assertThat(row)
                        .containsEntry("inputDataType", "ARTICLE_LIST")
                        .containsEntry("id", "ai_summarize"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sinkInputs =
                (List<Map<String, Object>>) contractTables.get("sinkInputs");
        assertThat(sinkInputs).singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("service", "slack")
                        .containsEntry("acceptedInputTypes", List.of("TEXT"))
                        .containsEntry("requiredConfigFields", List.of("channel")));
    }

    @Test
    void buildContext_includesGithubNewPrGenerationContract() {
        CatalogService catalogService = mock(CatalogService.class);
        ChoiceMappingService choiceMappingService = mock(ChoiceMappingService.class);
        when(catalogService.getSourceCatalog()).thenReturn(new SourceCatalog(
                new SourceCatalog.Meta("test", "now"),
                List.of(new SourceService(
                        "github",
                        "GitHub",
                        true,
                        List.of(new SourceMode(
                                "new_pr",
                                "New pull request",
                                "API_RESPONSE",
                                "event",
                                Map.of(
                                        "type", "text_input",
                                        "validation", "github_repo",
                                        "picker_supported", true,
                                        "manual_input_supported", true,
                                        "backfill_default", 5,
                                        "backfill_min", 1,
                                        "backfill_max", 20,
                                        "supported_filters", List.of("base_branch", "labels", "authors", "include_drafts")
                                )
                        ))
                ))
        ));
        when(catalogService.getSinkCatalog()).thenReturn(new SinkCatalog(
                new SourceCatalog.Meta("test", "now"),
                List.of()
        ));
        when(choiceMappingService.getMappingRules()).thenReturn(mappingRules());

        WorkflowGenerationContextService service = new WorkflowGenerationContextService(
                catalogService,
                choiceMappingService,
                webFeedSourceRegistry()
        );

        Map<String, Object> context = service.buildContext();

        @SuppressWarnings("unchecked")
        Map<String, Object> contractTables = (Map<String, Object>) context.get("contractTables");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourceOutputs =
                (List<Map<String, Object>>) contractTables.get("sourceOutputs");
        assertThat(sourceOutputs).singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("service", "github")
                        .containsEntry("sourceMode", "new_pr")
                        .containsEntry("outputDataType", "API_RESPONSE"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourcePolicies =
                (List<Map<String, Object>>) contractTables.get("sourceConfigPolicies");
        assertThat(sourcePolicies).singleElement()
                .satisfies(row -> {
                    assertThat(row)
                            .containsEntry("service", "github")
                            .containsEntry("sourceMode", "new_pr")
                            .containsEntry("targetSchemaType", "text_input")
                            .containsEntry("targetValuePolicy", "github_repo");
                    assertThat(stringList(row, "aiWritableFields"))
                            .contains("target", "backfill_count", "base_branch", "labels", "authors", "include_drafts");
                    assertThat(stringList(row, "requiredConfigFields")).contains("target");
                    assertThat(stringList(row, "supportedFilters"))
                            .containsExactly("base_branch", "labels", "authors", "include_drafts");
                    assertThat(row).containsEntry("backfillDefault", 5);
                    assertThat(row).containsEntry("backfillMin", 1);
                    assertThat(row).containsEntry("backfillMax", 20);
                });
    }

    @Test
    void buildContext_includesAiWritableConfigPolicies() {
        CatalogService catalogService = mock(CatalogService.class);
        ChoiceMappingService choiceMappingService = mock(ChoiceMappingService.class);
        when(catalogService.getSourceCatalog()).thenReturn(new SourceCatalog(
                new SourceCatalog.Meta("test", "now"),
                List.of(
                        new SourceService(
                                "gmail",
                                "Gmail",
                                true,
                                List.of(new SourceMode(
                                        "sender_email",
                                        "Sender email",
                                        "SINGLE_EMAIL",
                                        "event",
                                        Map.of(
                                                "type", "text_input",
                                                "required", true,
                                                "validation", "email"
                                        )
                                ))
                        ),
                        new SourceService(
                                "google_drive",
                                "Google Drive",
                                true,
                                List.of(new SourceMode(
                                        "folder_all_files",
                                        "Folder files",
                                        "FILE_LIST",
                                        "manual",
                                        Map.of("type", "folder_picker")
                                ))
                        ),
                        new SourceService(
                                "naver_news",
                                "Naver News",
                                false,
                                List.of(
                                        new SourceMode(
                                                "article_search",
                                                "Article search",
                                                "ARTICLE_LIST",
                                                "manual",
                                                Map.of("type", "text_input")
                                        ),
                                        new SourceMode(
                                                "new_articles",
                                                "New articles",
                                                "ARTICLE_LIST",
                                                "event",
                                                Map.of("type", "text_input")
                                        )
                                )
                        ),
                        new SourceService(
                                "web_news",
                                "Web News",
                                false,
                                List.of(new SourceMode(
                                        "website_feed",
                                        "Website feed",
                                        "ARTICLE_LIST",
                                        "manual",
                                        Map.of(
                                                "type", "feed_source_picker",
                                                "keyword_supported", true
                                        )
                                ))
                        )
                )
        ));
        when(catalogService.getSinkCatalog()).thenReturn(new SinkCatalog(
                new SourceCatalog.Meta("test", "now"),
                List.of(
                        new SinkService(
                                "discord",
                                "Discord",
                                false,
                                List.of("TEXT"),
                                "per_service",
                                Map.of("fields", List.of(
                                        Map.of("key", "webhook_url", "type", "secret_text", "required", true),
                                        Map.of("key", "message_template", "type", "textarea", "required", false)
                                ))
                        ),
                        new SinkService(
                                "google_drive",
                                "Google Drive",
                                true,
                                List.of("TEXT"),
                                "per_service",
                                Map.of("fields", List.of(
                                        Map.of("key", "folder_id", "type", "folder_picker", "required", true),
                                        Map.of("key", "filename_template", "type", "text", "required", false)
                                ))
                        ),
                        new SinkService(
                                "google_sheets",
                                "Google Sheets",
                                true,
                                List.of("TEXT"),
                                "per_service",
                                Map.of("fields", List.of(
                                        Map.of("key", "spreadsheet_id", "type", "sheet_picker", "required", true),
                                        Map.of("key", "sheet_name", "type", "text", "required", false),
                                        Map.of(
                                                "key", "write_mode",
                                                "type", "select",
                                                "options", List.of("append_rows", "overwrite_range"),
                                                "required", true
                                        )
                                ))
                        ),
                        new SinkService(
                                "gmail",
                                "Gmail",
                                true,
                                List.of("TEXT"),
                                "per_service",
                                Map.of("fields", List.of(
                                        Map.of("key", "to", "type", "email_input", "required", true),
                                        Map.of("key", "subject", "type", "text", "required", true),
                                        Map.of(
                                                "key", "action",
                                                "type", "select",
                                                "options", List.of("send"),
                                                "required", true
                                        )
                                ))
                        )
                )
        ));
        when(catalogService.getSinkRequiredFields("discord")).thenReturn(List.of("webhook_url"));
        when(catalogService.getSinkRequiredFields("google_drive")).thenReturn(List.of("folder_id"));
        when(catalogService.getSinkRequiredFields("google_sheets"))
                .thenReturn(List.of("spreadsheet_id", "write_mode"));
        when(catalogService.getSinkRequiredFields("gmail")).thenReturn(List.of("to", "subject", "action"));
        when(choiceMappingService.getMappingRules()).thenReturn(mappingRules());

        WorkflowGenerationContextService service = new WorkflowGenerationContextService(
                catalogService,
                choiceMappingService,
                webFeedSourceRegistry()
        );

        Map<String, Object> context = service.buildContext();

        @SuppressWarnings("unchecked")
        Map<String, Object> contractTables = (Map<String, Object>) context.get("contractTables");
        assertThat(contractTables).containsKeys("sourceConfigPolicies", "sinkConfigPolicies");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourcePolicies =
                (List<Map<String, Object>>) contractTables.get("sourceConfigPolicies");
        assertThat(sourcePolicies)
                .anySatisfy(row -> {
                    assertThat(row)
                            .containsEntry("service", "gmail")
                            .containsEntry("sourceMode", "sender_email")
                            .containsEntry("targetSchemaType", "text_input");
                    assertThat(row).doesNotContainKey("targetValuePolicy");
                    assertThat(stringList(row, "aiWritableFields")).contains("target");
                    assertThat(stringList(row, "aiWritableFields")).doesNotContain("keyword");
                    assertThat(stringList(row, "requiredConfigFields")).contains("target");
                    assertThat(stringList(row, "aiForbiddenFields")).contains("target_label", "target_meta", "targets");
                })
                .anySatisfy(row -> {
                    assertThat(row)
                            .containsEntry("service", "google_drive")
                            .containsEntry("sourceMode", "folder_all_files");
                    assertThat(row).doesNotContainKey("targetValuePolicy");
                    assertThat(stringList(row, "aiForbiddenFields")).contains("target");
                })
                .anySatisfy(row -> {
                    assertThat(row)
                            .containsEntry("service", "naver_news")
                            .containsEntry("sourceMode", "article_search")
                            .containsEntry("targetValuePolicy", "prompt_keyword");
                    assertThat(stringList(row, "aiWritableFields")).contains("target");
                })
                .anySatisfy(row -> assertThat(row)
                        .containsEntry("service", "naver_news")
                        .containsEntry("sourceMode", "new_articles")
                        .containsEntry("targetValuePolicy", "prompt_keyword"))
                .anySatisfy(row -> {
                    assertThat(row)
                            .containsEntry("service", "web_news")
                            .containsEntry("sourceMode", "website_feed")
                            .containsEntry("targetValuePolicy", "feed_source");
                    assertThat(stringList(row, "aiWritableFields"))
                            .contains("target_preset_ids", "custom_target_urls", "keyword");
                    assertThat(stringList(row, "aiForbiddenFields")).contains("target", "targets");
                    assertThat(stringList(row, "requiredAnyConfigFields"))
                            .contains("target_preset_ids", "custom_target_urls");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> presetTargets =
                            (List<Map<String, Object>>) row.get("presetTargets");
                    assertThat(presetTargets)
                            .isNotEmpty()
                            .anySatisfy(target -> assertThat(target)
                                    .containsEntry("id", "bbc_news")
                                    .containsEntry("label", "BBC News")
                                    .doesNotContainKey("url"));
                });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sinkPolicies =
                (List<Map<String, Object>>) contractTables.get("sinkConfigPolicies");
        assertThat(sinkPolicies)
                .anySatisfy(row -> {
                    assertThat(row).containsEntry("service", "discord");
                    assertThat(stringList(row, "aiForbiddenFields")).contains("webhook_url");
                    assertThat(stringList(row, "aiWritableFields")).contains("message_template");
                })
                .anySatisfy(row -> {
                    assertThat(row).containsEntry("service", "google_drive");
                    assertThat(stringList(row, "aiForbiddenFields")).contains("folder_id");
                    assertThat(stringList(row, "aiWritableFields")).contains("filename_template");
                })
                .anySatisfy(row -> {
                    assertThat(row).containsEntry("service", "google_sheets");
                    assertThat(stringList(row, "aiForbiddenFields"))
                            .contains("spreadsheet_id", "sheet_name");
                    assertThat(stringList(row, "aiWritableFields")).contains("write_mode");
                })
                .anySatisfy(row -> {
                    assertThat(row).containsEntry("service", "gmail");
                    assertThat(stringList(row, "aiWritableFields")).contains("to", "to_source", "subject", "action");
                    assertThat(stringList(row, "aiForbiddenFields")).doesNotContain("to");
                    assertThat(stringMap(row, "fieldValuePolicies"))
                            .containsEntry("to", "explicit_email")
                            .containsEntry("to_source", "current_user_email");
                });
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Map<String, Object> row, String key) {
        return (List<String>) row.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMap(Map<String, Object> row, String key) {
        return (Map<String, String>) row.get(key);
    }

    private static WebFeedSourceRegistry webFeedSourceRegistry() {
        return new WebFeedSourceRegistry(new ObjectMapper());
    }

    private MappingRules mappingRules() {
        return MappingRules.builder()
                .dataTypes(Map.of(
                        "EMAIL_LIST", DataTypeConfig.builder()
                                .label("Email list")
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
                        "FILE_LIST", DataTypeConfig.builder()
                                .label("File list")
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
                        "SINGLE_EMAIL", DataTypeConfig.builder()
                                .label("Single email")
                                .actions(List.of(Action.builder()
                                        .id("summarize")
                                        .label("Summary")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .priority(1)
                                        .build()))
                                .build(),
                        "SPREADSHEET_DATA", DataTypeConfig.builder()
                                .label("Spreadsheet data")
                                .actions(List.of(
                                        Action.builder()
                                                .id("filter_condition")
                                                .label("Filter condition")
                                                .nodeType("DATA_FILTER")
                                                .outputDataType("SPREADSHEET_DATA")
                                                .priority(1)
                                                .build(),
                                        Action.builder()
                                                .id("filter_fields")
                                                .label("Filter fields")
                                                .nodeType("DATA_FILTER")
                                                .outputDataType("SPREADSHEET_DATA")
                                                .priority(2)
                                                .build()
                                ))
                                .build(),
                        "ARTICLE_LIST", DataTypeConfig.builder()
                                .label("Article list")
                                .requiresProcessingMethod(true)
                                .processingMethod(ProcessingMethod.builder()
                                        .options(List.of(Option.builder()
                                                .id("one_by_one")
                                                .label("One by one")
                                                .nodeType("LOOP")
                                                .outputDataType("TEXT")
                                                .priority(1)
                                                .build()))
                                        .build())
                                .actions(List.of(Action.builder()
                                        .id("ai_summarize")
                                        .label("AI summarize")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .priority(1)
                                        .build()))
                                .build(),
                        "TEXT", DataTypeConfig.builder()
                                .label("Text")
                                .actions(List.of(Action.builder()
                                        .id("ai_summarize")
                                        .label("AI summarize")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .priority(1)
                                        .build()))
                                .build()
                ))
                .build();
    }
}
