package org.github.flowify.workflow.service.generation;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowGenerationContextService {

    private static final Map<String, Set<String>> SUPPORTED_SOURCE_MODES = Map.of(
            "google_drive", Set.of("single_file", "file_changed", "new_file", "folder_new_file", "folder_all_files"),
            "gmail", Set.of("single_email", "new_email", "sender_email", "starred_email", "label_emails", "attachment_email"),
            "google_sheets", Set.of("sheet_all", "new_row", "row_updated"),
            "slack", Set.of("channel_messages"),
            "canvas_lms", Set.of("course_files", "course_new_file", "term_all_files"),
            "naver_news", Set.of("article_search", "new_articles"),
            "web_news", Set.of("seboard_posts", "seboard_new_posts", "website_feed")
    );
    private static final Set<String> SUPPORTED_SINKS = Set.of(
            "slack",
            "discord",
            "gmail",
            "notion",
            "google_drive",
            "google_sheets",
            "google_calendar"
    );
    private static final Set<String> SUPPORTED_PROCESSORS = Set.of("AI", "DATA_FILTER", "AI_FILTER");

    private final CatalogService catalogService;
    private final ChoiceMappingService choiceMappingService;

    public Map<String, Object> buildContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("schemaVersion", "flowify-workflow-generate-v1");
        context.put("allowedRoles", List.of("start", "middle", "end"));
        context.put("topology", buildTopology());
        context.put("defaultTrigger", Map.of(
                "type", WorkflowTriggerSupport.TYPE_MANUAL,
                "config", Map.of()
        ));
        context.put("rules", buildRules());
        context.put("sources", buildSourceSpecs());
        context.put("sinks", buildSinkSpecs());
        context.put("processors", buildProcessorSpecs());
        return context;
    }

    private Map<String, Object> buildTopology() {
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("startCount", 1);
        topology.put("maxMiddleCount", 1);
        topology.put("endCount", 1);
        topology.put("allowBranch", false);
        topology.put("allowLoop", false);
        topology.put("allowMultipleSinks", false);
        topology.put("allowScheduleTrigger", false);
        return topology;
    }

    private List<String> buildRules() {
        return List.of(
                "Generate a reviewable draft workflow, not an immediately executable automation.",
                "Use only services, source modes, sink services, processors, and data types listed in this context.",
                "Do not invent external resource ids such as folder ids, label ids, page ids, channel ids, or webhook urls.",
                "Use an empty string or null for unknown required resources.",
                "Set config.isConfigured=false when any required setting is unknown or missing.",
                "Do not include runtime_source, runtime_sink, runtime_config, or runtime_action fields.",
                "Trigger must be manual for this generation phase.",
                "The workflow must have exactly one start node, at most one middle node, and exactly one end node."
        );
    }

    private List<Map<String, Object>> buildSourceSpecs() {
        return catalogService.getSourceCatalog().getServices().stream()
                .filter(service -> SUPPORTED_SOURCE_MODES.containsKey(service.getKey()))
                .map(this::toSourceSpec)
                .toList();
    }

    private Map<String, Object> toSourceSpec(SourceService service) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("key", service.getKey());
        spec.put("label", service.getLabel());
        spec.put("authRequired", service.isAuthRequired());
        spec.put("modes", service.getSourceModes().stream()
                .filter(mode -> SUPPORTED_SOURCE_MODES.get(service.getKey()).contains(mode.getKey()))
                .map(this::toSourceModeSpec)
                .toList());
        return spec;
    }

    private Map<String, Object> toSourceModeSpec(SourceMode mode) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("key", mode.getKey());
        spec.put("label", mode.getLabel());
        spec.put("outputDataType", mode.getCanonicalInputType());
        spec.put("triggerKind", mode.getTriggerKind());
        spec.put("targetRequired", mode.getTargetSchema() != null && !mode.getTargetSchema().isEmpty());
        spec.put("targetSchema", mode.getTargetSchema() != null ? mode.getTargetSchema() : Map.of());
        return spec;
    }

    private List<Map<String, Object>> buildSinkSpecs() {
        return catalogService.getSinkCatalog().getServices().stream()
                .filter(service -> SUPPORTED_SINKS.contains(service.getKey()))
                .map(this::toSinkSpec)
                .toList();
    }

    private Map<String, Object> toSinkSpec(SinkService service) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("key", service.getKey());
        spec.put("label", service.getLabel());
        spec.put("authRequired", service.isAuthRequired());
        spec.put("acceptedInputTypes", service.getAcceptedInputTypes());
        spec.put("requiredConfigFields", catalogService.getSinkRequiredFields(service.getKey()));
        spec.put("configSchema", service.getConfigSchema() != null ? service.getConfigSchema() : Map.of());
        return spec;
    }

    private List<Map<String, Object>> buildProcessorSpecs() {
        MappingRules mappingRules = choiceMappingService.getMappingRules();
        if (mappingRules == null || mappingRules.getDataTypes() == null) {
            return List.of();
        }

        List<Map<String, Object>> specs = new ArrayList<>();
        for (Map.Entry<String, DataTypeConfig> entry : mappingRules.getDataTypes().entrySet()) {
            DataTypeConfig dataTypeConfig = entry.getValue();
            if (dataTypeConfig.getActions() == null) {
                continue;
            }

            List<Map<String, Object>> actions = dataTypeConfig.getActions().stream()
                    .filter(action -> SUPPORTED_PROCESSORS.contains(action.getNodeType()))
                    .sorted(Comparator.comparingInt(Action::getPriority))
                    .map(this::toProcessorActionSpec)
                    .toList();

            if (actions.isEmpty()) {
                continue;
            }

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("inputDataType", entry.getKey());
            spec.put("label", dataTypeConfig.getLabel());
            spec.put("description", dataTypeConfig.getDescription());
            spec.put("actions", actions);
            specs.add(spec);
        }
        return specs;
    }

    private Map<String, Object> toProcessorActionSpec(Action action) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("id", action.getId());
        spec.put("label", action.getLabel());
        spec.put("nodeType", action.getNodeType());
        spec.put("outputDataType", action.getOutputDataType());
        spec.put("description", action.getDescription());
        return spec;
    }
}
