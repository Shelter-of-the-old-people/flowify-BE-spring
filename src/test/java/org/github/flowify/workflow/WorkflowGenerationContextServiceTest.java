package org.github.flowify.workflow;

import org.github.flowify.catalog.dto.SinkCatalog;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceCatalog;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.Action;
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
                choiceMappingService
        );

        Map<String, Object> context = service.buildContext();

        @SuppressWarnings("unchecked")
        Map<String, Object> topology = (Map<String, Object>) context.get("topology");
        assertThat(topology).containsEntry("maxMiddleCount", 3);
        assertThat(topology).containsEntry("allowLoop", true);
        assertThat(topology).containsEntry("allowBranch", false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processors = (List<Map<String, Object>>) context.get("processors");
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
                        "SINGLE_EMAIL", DataTypeConfig.builder()
                                .label("Single email")
                                .actions(List.of(Action.builder()
                                        .id("summarize")
                                        .label("Summary")
                                        .nodeType("AI")
                                        .outputDataType("TEXT")
                                        .priority(1)
                                        .build()))
                                .build()
                ))
                .build();
    }
}
