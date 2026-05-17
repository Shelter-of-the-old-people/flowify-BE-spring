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
import org.github.flowify.workflow.service.choice.dto.Option;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowGenerationContextService {

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
        topology.put("maxMiddleCount", 3);
        topology.put("endCount", 1);
        topology.put("allowBranch", false);
        topology.put("allowLoop", true);
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
                "The workflow must have exactly one start node and exactly one end node.",
                "Use middle nodes only as needed, and keep the generated workflow a single path.",
                "When a data type requires a processing method, create a processing method node before choosing an action.",
                "Do not connect list data directly to a single-item action.",
                "Do not create branches, merges, multiple sinks, or schedule triggers."
        );
    }

    private List<Map<String, Object>> buildSourceSpecs() {
        return catalogService.getSourceCatalog().getServices().stream()
                .filter(service -> WorkflowGenerationSupport.SUPPORTED_SOURCE_MODES.containsKey(service.getKey()))
                .map(this::toSourceSpec)
                .toList();
    }

    private Map<String, Object> toSourceSpec(SourceService service) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("key", service.getKey());
        spec.put("label", service.getLabel());
        spec.put("authRequired", service.isAuthRequired());
        spec.put("modes", service.getSourceModes().stream()
                .filter(mode -> WorkflowGenerationSupport.SUPPORTED_SOURCE_MODES
                        .get(service.getKey())
                        .contains(mode.getKey()))
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
                .filter(service -> WorkflowGenerationSupport.SUPPORTED_SINKS.contains(service.getKey()))
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
            if (dataTypeConfig == null) {
                continue;
            }

            List<Map<String, Object>> processingMethods = toProcessingMethodSpecs(dataTypeConfig);
            List<Map<String, Object>> actions = dataTypeConfig.getActions() == null
                    ? List.of()
                    : dataTypeConfig.getActions().stream()
                    .filter(action -> WorkflowGenerationSupport.SUPPORTED_ACTION_NODE_TYPES.contains(action.getNodeType()))
                    .sorted(Comparator.comparingInt(Action::getPriority))
                    .map(this::toProcessorActionSpec)
                    .toList();

            if (processingMethods.isEmpty() && actions.isEmpty()) {
                continue;
            }

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("inputDataType", entry.getKey());
            spec.put("label", dataTypeConfig.getLabel());
            spec.put("description", dataTypeConfig.getDescription());
            spec.put("requiresProcessingMethod", dataTypeConfig.isRequiresProcessingMethod());
            spec.put("processingMethods", processingMethods);
            spec.put("actions", actions);
            specs.add(spec);
        }
        return specs;
    }

    private List<Map<String, Object>> toProcessingMethodSpecs(DataTypeConfig dataTypeConfig) {
        if (dataTypeConfig.getProcessingMethod() == null
                || dataTypeConfig.getProcessingMethod().getOptions() == null) {
            return List.of();
        }

        return dataTypeConfig.getProcessingMethod().getOptions().stream()
                .filter(option -> WorkflowGenerationSupport.SUPPORTED_PROCESSING_METHOD_NODE_TYPES.contains(option.getNodeType()))
                .sorted(Comparator.comparingInt(this::optionPriority))
                .map(this::toProcessingMethodSpec)
                .toList();
    }

    private int optionPriority(Option option) {
        return option.getPriority() != null ? option.getPriority() : Integer.MAX_VALUE;
    }

    private Map<String, Object> toProcessingMethodSpec(Option option) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("id", option.getId());
        spec.put("label", option.getLabel());
        spec.put("nodeType", option.getNodeType());
        spec.put("outputDataType", option.getOutputDataType());
        return spec;
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
