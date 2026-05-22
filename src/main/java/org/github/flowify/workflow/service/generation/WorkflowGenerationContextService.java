package org.github.flowify.workflow.service.generation;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.picker.WebFeedSourceRegistry;
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

    private static final int MAX_GENERATED_MIDDLE_COUNT = 15;
    private static final int MAX_GENERATED_END_COUNT = 7;

    private final CatalogService catalogService;
    private final ChoiceMappingService choiceMappingService;
    private final WebFeedSourceRegistry webFeedSourceRegistry;

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
        context.put("contractTables", buildContractTables());
        return context;
    }

    private Map<String, Object> buildTopology() {
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("startCount", 1);
        topology.put("maxMiddleCount", MAX_GENERATED_MIDDLE_COUNT);
        topology.put("minEndCount", 1);
        topology.put("maxEndCount", MAX_GENERATED_END_COUNT);
        topology.put("allowBranch", true);
        topology.put("allowLoop", true);
        topology.put("allowMultipleSinks", true);
        topology.put("allowedBranchNodeTypes", List.of("CONDITION_BRANCH"));
        topology.put("allowedBranchActions", List.of("branch_by_file_type"));
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
                "The workflow must have exactly one start node.",
                "Use middle nodes only as needed. Branching is allowed only for FILE_LIST branch_by_file_type CONDITION_BRANCH.",
                "When using a branch node, each branch edge must include label and sourceHandle equal to the branch key, and targetHandle=input.",
                "When using branch_by_file_type, include config.choiceSelections.branch_config with the exact branch keys used by outgoing edges.",
                "Do not create merges. Branch paths must remain a tree and every path must end at a sink.",
                "When a data type requires a processing method, create a processing method node before choosing an action.",
                "Do not connect list data directly to a single-item action.",
                "Use contractTables.processorTransitions as the source of truth for allowed middle-node steps.",
                "Do not use processor actions omitted from contractTables, even if they appear in older mapping rules.",
                "Do not create schedule triggers."
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
                    .filter(action -> WorkflowGenerationSupport.isSupportedGeneratedProcessorAction(
                            entry.getKey(),
                            action
                    ))
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

    private Map<String, Object> buildContractTables() {
        Map<String, Object> contractTables = new LinkedHashMap<>();
        contractTables.put("sourceOutputs", buildSourceOutputTable());
        contractTables.put("processorTransitions", buildProcessorTransitionTable());
        contractTables.put("sinkInputs", buildSinkInputTable());
        contractTables.put("sourceConfigPolicies",
                WorkflowGenerationConfigPolicy.buildSourceConfigPolicies(catalogService, webFeedSourceRegistry));
        contractTables.put("sinkConfigPolicies",
                WorkflowGenerationConfigPolicy.buildSinkConfigPolicies(catalogService));
        contractTables.put("requiredPathHints", buildRequiredPathHints());
        return contractTables;
    }

    private List<Map<String, Object>> buildSourceOutputTable() {
        return catalogService.getSourceCatalog().getServices().stream()
                .filter(service -> WorkflowGenerationSupport.SUPPORTED_SOURCE_MODES.containsKey(service.getKey()))
                .flatMap(service -> service.getSourceModes().stream()
                        .filter(mode -> WorkflowGenerationSupport.SUPPORTED_SOURCE_MODES
                                .get(service.getKey())
                                .contains(mode.getKey()))
                        .map(mode -> toSourceOutputContract(service, mode)))
                .toList();
    }

    private Map<String, Object> toSourceOutputContract(SourceService service, SourceMode mode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("service", service.getKey());
        row.put("serviceLabel", service.getLabel());
        row.put("sourceMode", mode.getKey());
        row.put("sourceModeLabel", mode.getLabel());
        row.put("outputDataType", mode.getCanonicalInputType());
        row.put("targetRequired", mode.getTargetSchema() != null && !mode.getTargetSchema().isEmpty());
        return row;
    }

    private List<Map<String, Object>> buildProcessorTransitionTable() {
        MappingRules mappingRules = choiceMappingService.getMappingRules();
        if (mappingRules == null || mappingRules.getDataTypes() == null) {
            return List.of();
        }

        List<Map<String, Object>> transitions = new ArrayList<>();
        for (Map.Entry<String, DataTypeConfig> entry : mappingRules.getDataTypes().entrySet()) {
            String inputDataType = entry.getKey();
            DataTypeConfig dataTypeConfig = entry.getValue();
            if (inputDataType == null || dataTypeConfig == null) {
                continue;
            }

            if (dataTypeConfig.getProcessingMethod() != null
                    && dataTypeConfig.getProcessingMethod().getOptions() != null) {
                dataTypeConfig.getProcessingMethod().getOptions().stream()
                        .filter(option -> WorkflowGenerationSupport.SUPPORTED_PROCESSING_METHOD_NODE_TYPES
                                .contains(option.getNodeType()))
                        .sorted(Comparator.comparingInt(this::optionPriority))
                        .map(option -> toProcessorTransitionContract(
                                inputDataType,
                                "processing_method",
                                option.getId(),
                                option.getLabel(),
                                option.getNodeType(),
                                option.getOutputDataType()
                        ))
                        .forEach(transitions::add);
            }

            if (dataTypeConfig.getActions() == null) {
                continue;
            }

            dataTypeConfig.getActions().stream()
                    .filter(action -> WorkflowGenerationSupport.isSupportedGeneratedProcessorAction(
                            inputDataType,
                            action
                    ))
                    .sorted(Comparator.comparingInt(Action::getPriority))
                    .map(action -> toProcessorTransitionContract(
                            inputDataType,
                            "action",
                            action.getId(),
                            action.getLabel(),
                            action.getNodeType(),
                            action.getOutputDataType()
                    ))
                    .forEach(transitions::add);
        }
        return transitions;
    }

    private Map<String, Object> toProcessorTransitionContract(
            String inputDataType,
            String stepKind,
            String id,
            String label,
            String nodeType,
            String outputDataType
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("inputDataType", inputDataType);
        row.put("stepKind", stepKind);
        row.put("id", id);
        row.put("label", label);
        row.put("nodeType", nodeType);
        row.put("outputDataType", outputDataType);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("choiceActionId", id);
        config.put("choiceNodeType", nodeType);
        row.put("config", config);
        return row;
    }

    private List<Map<String, Object>> buildSinkInputTable() {
        return catalogService.getSinkCatalog().getServices().stream()
                .filter(service -> WorkflowGenerationSupport.SUPPORTED_SINKS.contains(service.getKey()))
                .map(service -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("service", service.getKey());
                    row.put("serviceLabel", service.getLabel());
                    row.put("acceptedInputTypes", service.getAcceptedInputTypes());
                    row.put("requiredConfigFields", catalogService.getSinkRequiredFields(service.getKey()));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRequiredPathHints() {
        return List.of(
                Map.of(
                        "fromDataType", "FILE_LIST",
                        "branchStep", "branch_by_file_type CONDITION_BRANCH",
                        "branchEdgeContract", "label=branch key, sourceHandle=branch key, targetHandle=input",
                        "branchSelectionConfig", "choiceSelections.branch_config must contain the same branch keys as outgoing branch edges",
                        "example", "FILE_LIST -> branch_by_file_type CONDITION_BRANCH -> pdf LOOP -> SINGLE_FILE -> summarize AI -> TEXT -> Google Drive, archive FILE_LIST -> Gmail"
                ),
                Map.of(
                        "fromDataType", "ARTICLE_LIST",
                        "requiredFirstStep", "one_by_one LOOP",
                        "afterFirstStepDataType", "TEXT",
                        "thenChooseActionFromDataType", "TEXT",
                        "preferredAction", "ai_summarize AI",
                        "example", "ARTICLE_LIST -> one_by_one LOOP -> TEXT -> ai_summarize AI -> TEXT"
                )
        );
    }
}
