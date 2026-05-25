package org.github.flowify.workflow.service.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.picker.WebFeedSourceRegistry;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.ChoicePromptResolver;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.BranchConfig;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.github.flowify.workflow.service.choice.dto.Option;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkflowGenerationResultService {

    private static final String ROLE_START = "start";
    private static final String ROLE_MIDDLE = "middle";
    private static final String ROLE_END = "end";
    private static final int NODE_GAP_X = 360;
    private static final int MAX_GENERATED_MIDDLE_COUNT = 15;
    private static final int MAX_GENERATED_END_COUNT = 7;
    private static final String CONDITION_BRANCH_NODE_TYPE = "CONDITION_BRANCH";
    private static final String CHOICE_SELECTIONS_KEY = "choiceSelections";
    private static final String BRANCH_CONFIG_KEY = "branch_config";
    private static final String FILE_LIST_DATA_TYPE = "FILE_LIST";
    private static final String TEXT_DATA_TYPE = "TEXT";
    private static final String SPREADSHEET_DATA_TYPE = "SPREADSHEET_DATA";
    private static final String FILE_TYPE_BRANCH_ACTION_ID = "branch_by_file_type";
    private static final String BRANCH_BY_FILENAME_ACTION_ID = "branch_by_filename";
    private static final String CONTENT_BRANCH_ACTION_ID = "classify_by_content";
    private static final String CLASSIFY_BY_FIELD_ACTION_ID = "classify_by_field";
    private static final String SPLIT_EMAIL_PARTS_ACTION_ID = "split_email_parts";
    private static final String SPLIT_ANNOUNCEMENT_PARTS_ACTION_ID = "split_announcement_parts";
    private static final String OTHER_BRANCH_KEY = "other";
    private static final String BODY_BRANCH_KEY = "body";
    private static final String ATTACHMENTS_BRANCH_KEY = "attachments";
    private static final String POSITIVE_NEGATIVE_CONTENT_PRESET = "positive_negative";
    private static final String IMPORTANT_REF_CONTENT_PRESET = "important_ref";
    private static final String IMPORTANT_CHECK_REF_CONTENT_PRESET = "important_check_ref";
    private static final String IMPORTANT_INQUIRY_REF_CONTENT_PRESET = "important_inquiry_ref";
    private static final Pattern FILENAME_BRANCH_KEY_PATTERN = Pattern.compile("^filename_\\d+$");
    private static final Pattern FIELD_VALUE_BRANCH_KEY_PATTERN = Pattern.compile("^field_value_\\d+$");
    private static final Map<String, Map<String, String>> BRANCH_EDGE_OUTPUT_DATA_TYPES = Map.of(
            FILE_TYPE_BRANCH_ACTION_ID,
            Map.of(
                    "pdf", FILE_LIST_DATA_TYPE,
                    "archive", FILE_LIST_DATA_TYPE,
                    "image", FILE_LIST_DATA_TYPE,
                    "spreadsheet", FILE_LIST_DATA_TYPE,
                    "document", FILE_LIST_DATA_TYPE,
                    "presentation", FILE_LIST_DATA_TYPE,
                    OTHER_BRANCH_KEY, FILE_LIST_DATA_TYPE
            ),
            SPLIT_EMAIL_PARTS_ACTION_ID,
            Map.of(
                    BODY_BRANCH_KEY, TEXT_DATA_TYPE,
                    ATTACHMENTS_BRANCH_KEY, FILE_LIST_DATA_TYPE
            ),
            SPLIT_ANNOUNCEMENT_PARTS_ACTION_ID,
            Map.of(
                    BODY_BRANCH_KEY, TEXT_DATA_TYPE,
                    ATTACHMENTS_BRANCH_KEY, FILE_LIST_DATA_TYPE
            )
    );
    private static final Map<String, List<String>> CONTENT_BRANCH_KEYS_BY_PRESET = Map.of(
            POSITIVE_NEGATIVE_CONTENT_PRESET, List.of("positive", "negative", OTHER_BRANCH_KEY),
            IMPORTANT_REF_CONTENT_PRESET, List.of("important", "reference", OTHER_BRANCH_KEY),
            IMPORTANT_CHECK_REF_CONTENT_PRESET, List.of("important", "check", "reference", OTHER_BRANCH_KEY),
            IMPORTANT_INQUIRY_REF_CONTENT_PRESET, List.of("important", "inquiry", "reference", OTHER_BRANCH_KEY)
    );
    private static final Map<String, Set<String>> CONTENT_BRANCH_PRESETS_BY_DATA_TYPE = Map.of(
            TEXT_DATA_TYPE, Set.of(
                    POSITIVE_NEGATIVE_CONTENT_PRESET,
                    IMPORTANT_REF_CONTENT_PRESET,
                    IMPORTANT_CHECK_REF_CONTENT_PRESET
            ),
            "SINGLE_EMAIL", Set.of(
                    IMPORTANT_REF_CONTENT_PRESET,
                    IMPORTANT_INQUIRY_REF_CONTENT_PRESET
            )
    );
    private static final Set<String> PROMPT_NODE_TYPES = Set.of("AI", "AI_FILTER");

    private final ObjectMapper objectMapper;
    private final WorkflowValidator workflowValidator;
    private final ChoiceMappingService choiceMappingService;
    private final ChoicePromptResolver choicePromptResolver;
    private final CatalogService catalogService;
    private final WebFeedSourceRegistry webFeedSourceRegistry;

    public WorkflowCreateRequest toCreateRequest(Map<String, Object> generated) {
        return toCreateRequest(generated, "");
    }

    public WorkflowCreateRequest toCreateRequest(Map<String, Object> generated, String prompt) {
        if (generated == null) {
            throw invalid("Generated workflow is empty.");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("name", normalizedName(generated.get("name")));
        normalized.put("description", generated.get("description"));

        List<Map<String, Object>> nodes = normalizeNodes(generated.get("nodes"));
        List<Map<String, Object>> edges = normalizeEdges(generated.get("edges"), nodes);
        validateTopology(nodes, edges);
        normalizeMiddleNodes(nodes, edges, buildProcessorActionLookup(), buildProcessingMethodLookup());
        validateBranchEdges(nodes, edges);
        normalizeSinkInputDataTypes(nodes, edges);
        validateGeneratedDataFlow(nodes, edges);
        sanitizeGeneratedServiceConfigs(nodes, prompt);

        normalized.put("nodes", nodes);
        normalized.put("edges", edges);
        normalized.put("trigger", Map.of(
                "type", WorkflowTriggerSupport.TYPE_MANUAL,
                "config", Map.of()
        ));

        WorkflowCreateRequest request = objectMapper.convertValue(normalized, WorkflowCreateRequest.class);
        workflowValidator.validate(Workflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .nodes(request.getNodes())
                .edges(request.getEdges())
                .trigger(request.getTrigger())
                .build());
        return request;
    }

    private List<Map<String, Object>> normalizeNodes(Object rawNodes) {
        if (!(rawNodes instanceof List<?> nodeValues) || nodeValues.isEmpty()) {
            throw invalid("Generated workflow must contain nodes.");
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();
        for (int index = 0; index < nodeValues.size(); index++) {
            if (!(nodeValues.get(index) instanceof Map<?, ?> rawNode)) {
                throw invalid("Generated node must be an object.");
            }

            rejectRuntimeFields(rawNode, "node");

            Map<String, Object> node = new LinkedHashMap<>();
            String id = requiredText(rawNode.get("id"), "Node id is required.");
            if (!nodeIds.add(id)) {
                throw invalid("Duplicate node id: " + id);
            }

            String role = requiredText(rawNode.get("role"), "Node role is required.");
            validateRole(role);

            String type = requiredText(rawNode.get("type"), "Node type is required.");
            Map<String, Object> config = normalizeConfig(rawNode.get("config"));
            normalizeServiceNodeConfig(role, type, config);
            validateNodeType(role, type, config);

            node.put("id", id);
            node.put("category", requiredText(rawNode.get("category"), "Node category is required."));
            node.put("type", type);
            node.put("label", textOrNull(rawNode.get("label")));
            node.put("role", role);
            node.put("position", normalizePosition(rawNode.get("position"), index));
            node.put("config", config);
            if (!ROLE_START.equals(role)) {
                putIfPresent(node, "dataType", rawNode.get("dataType"));
            }
            putIfPresent(node, "outputDataType", normalizeSourceOutputDataType(
                    role,
                    type,
                    config,
                    rawNode.get("outputDataType")
            ));
            node.put("authWarning", rawNode.get("authWarning") instanceof Boolean value && value);
            nodes.add(node);
        }

        return nodes;
    }

    private List<Map<String, Object>> normalizeEdges(Object rawEdges, List<Map<String, Object>> nodes) {
        if (rawEdges == null) {
            return List.of();
        }
        if (!(rawEdges instanceof List<?> edgeValues)) {
            throw invalid("Generated edges must be an array.");
        }

        Set<String> nodeIds = new HashSet<>();
        Set<String> conditionBranchNodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = textOrNull(node.get("id"));
            if (nodeId == null) {
                continue;
            }
            nodeIds.add(nodeId);
            if (CONDITION_BRANCH_NODE_TYPE.equals(textOrNull(node.get("type")))) {
                conditionBranchNodeIds.add(nodeId);
            }
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> edgeIds = new HashSet<>();
        for (Object edgeValue : edgeValues) {
            if (!(edgeValue instanceof Map<?, ?> rawEdge)) {
                throw invalid("Generated edge must be an object.");
            }

            rejectRuntimeFields(rawEdge, "edge");

            String source = requiredText(rawEdge.get("source"), "Edge source is required.");
            String target = requiredText(rawEdge.get("target"), "Edge target is required.");
            if (!nodeIds.contains(source) || !nodeIds.contains(target)) {
                throw invalid("Edge references an unknown node.");
            }

            Map<String, Object> edge = new LinkedHashMap<>();
            String id = textOrNull(rawEdge.get("id"));
            if (id == null) {
                id = "edge_" + source + "_" + target;
            }
            if (!edgeIds.add(id)) {
                throw invalid("Duplicate edge id: " + id);
            }

            edge.put("id", id);
            edge.put("source", source);
            edge.put("target", target);
            if (conditionBranchNodeIds.contains(source)) {
                putTextIfPresent(edge, "label", rawEdge.get("label"));
                putTextIfPresent(edge, "sourceHandle", rawEdge.get("sourceHandle"));
                putTextIfPresent(edge, "targetHandle", rawEdge.get("targetHandle"));
            } else {
                putTextIfPresent(edge, "label", rawEdge.get("label"));
                putTextIfPresent(edge, "targetHandle", rawEdge.get("targetHandle"));
            }
            edges.add(edge);
        }

        return edges;
    }

    private void validateTopology(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        long startCount = countRole(nodes, ROLE_START);
        long middleCount = countRole(nodes, ROLE_MIDDLE);
        long endCount = countRole(nodes, ROLE_END);
        if (startCount != 1 || middleCount > MAX_GENERATED_MIDDLE_COUNT
                || endCount < 1 || endCount > MAX_GENERATED_END_COUNT) {
            throw invalid("Generated workflow must have exactly one start, up to "
                    + MAX_GENERATED_MIDDLE_COUNT
                    + " middle nodes, and one to "
                    + MAX_GENERATED_END_COUNT
                    + " end nodes.");
        }

        if (nodes.size() > 1 && edges.size() != nodes.size() - 1) {
            throw invalid("Generated workflow must be a connected tree.");
        }

        Map<String, Map<String, Object>> nodesById = indexNodesById(nodes);
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, Integer> outgoing = new HashMap<>();
        Map<String, List<String>> nextTargetsBySource = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            incoming.put(String.valueOf(node.get("id")), 0);
            outgoing.put(String.valueOf(node.get("id")), 0);
        }

        for (Map<String, Object> edge : edges) {
            String source = String.valueOf(edge.get("source"));
            String target = String.valueOf(edge.get("target"));
            outgoing.compute(source, (key, value) -> value == null ? 1 : value + 1);
            incoming.compute(target, (key, value) -> value == null ? 1 : value + 1);
            nextTargetsBySource.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
        }

        String startId = null;
        long branchCount = 0;
        for (Map<String, Object> node : nodes) {
            String id = String.valueOf(node.get("id"));
            String role = String.valueOf(node.get("role"));
            String type = String.valueOf(node.get("type"));
            if (ROLE_START.equals(role)) {
                startId = id;
            }
            if (CONDITION_BRANCH_NODE_TYPE.equals(type)) {
                branchCount++;
            }
            if (incoming.get(id) > 1) {
                throw invalid("Generated workflow cannot merge.");
            }
            validateNodeDegree(role, type, incoming.get(id), outgoing.get(id), nodes.size());
        }
        if (branchCount > 1) {
            throw invalid("Generated workflow can contain only one branch node.");
        }

        Set<String> visited = new HashSet<>();
        visitGeneratedTree(startId, nextTargetsBySource, visited);
        if (visited.size() != nodes.size()) {
            throw invalid("Generated workflow must connect every node from start to end.");
        }

        for (String visitedId : visited) {
            Map<String, Object> node = nodesById.get(visitedId);
            if (node == null) {
                continue;
            }
            String role = textOrNull(node.get("role"));
            if (ROLE_END.equals(role)) {
                continue;
            }
            if (nextTargetsBySource.getOrDefault(visitedId, List.of()).isEmpty()) {
                throw invalid("Generated workflow path must end at a sink.");
            }
        }
    }

    private void visitGeneratedTree(
            String nodeId,
            Map<String, List<String>> nextTargetsBySource,
            Set<String> visited
    ) {
        if (nodeId == null) {
            return;
        }
        if (!visited.add(nodeId)) {
            throw invalid("Generated workflow cannot contain a cycle.");
        }
        for (String targetId : nextTargetsBySource.getOrDefault(nodeId, List.of())) {
            visitGeneratedTree(targetId, nextTargetsBySource, visited);
        }
    }

    private void validateNodeDegree(String role, String type, int incoming, int outgoing, int nodeCount) {
        if (nodeCount == 1) {
            throw invalid("Generated workflow must include start and end nodes.");
        }
        if (ROLE_START.equals(role) && (incoming != 0 || outgoing != 1)) {
            throw invalid("Start node must be the first node in the path.");
        }
        if (ROLE_MIDDLE.equals(role)
                && CONDITION_BRANCH_NODE_TYPE.equals(type)
                && (incoming != 1 || outgoing < 2)) {
            throw invalid("Branch node must have one input and at least two outgoing branches.");
        }
        if (ROLE_MIDDLE.equals(role)
                && !CONDITION_BRANCH_NODE_TYPE.equals(type)
                && (incoming != 1 || outgoing != 1)) {
            throw invalid("Middle node must be connected between start and end.");
        }
        if (ROLE_END.equals(role) && (incoming != 1 || outgoing != 0)) {
            throw invalid("End node must be the last node in the path.");
        }
    }

    private void validateNodeType(String role, String type, Map<String, Object> config) {
        if (ROLE_START.equals(role)) {
            if (!WorkflowGenerationSupport.SUPPORTED_SOURCE_MODES.containsKey(type)) {
                throw invalid("Unsupported source service: " + type);
            }
            String sourceModeText = textOrNull(config.get("source_mode"));
            if (sourceModeText == null) {
                throw invalid("Start node source mode is required.");
            }
            if (!WorkflowGenerationSupport.SUPPORTED_SOURCE_MODES.get(type).contains(sourceModeText)) {
                throw invalid("Unsupported source mode: " + sourceModeText);
            }
            return;
        }
        if (ROLE_MIDDLE.equals(role) && !WorkflowGenerationSupport.SUPPORTED_MIDDLE_NODE_TYPES.contains(type)) {
            throw invalid("Unsupported processor: " + type);
        }
        if (ROLE_END.equals(role) && !WorkflowGenerationSupport.SUPPORTED_SINKS.contains(type)) {
            throw invalid("Unsupported sink service: " + type);
        }
    }

    @SuppressWarnings("unchecked")
    private void sanitizeGeneratedServiceConfigs(List<Map<String, Object>> nodes, String prompt) {
        for (Map<String, Object> node : nodes) {
            String role = textOrNull(node.get("role"));
            if (!ROLE_START.equals(role) && !ROLE_END.equals(role)) {
                continue;
            }

            String type = requiredText(node.get("type"), "Node type is required.");
            Object rawConfig = node.get("config");
            if (!(rawConfig instanceof Map<?, ?>)) {
                continue;
            }

            Map<String, Object> config = (Map<String, Object>) rawConfig;
            if (ROLE_START.equals(role)) {
                WorkflowGenerationConfigPolicy.sanitizeStartNodeConfig(
                        catalogService,
                        webFeedSourceRegistry,
                        type,
                        config,
                        prompt
                );
            } else {
                WorkflowGenerationConfigPolicy.sanitizeEndNodeConfig(catalogService, type, config);
            }
        }
    }

    private void normalizeServiceNodeConfig(String role, String type, Map<String, Object> config) {
        if (!ROLE_START.equals(role) && !ROLE_END.equals(role)) {
            return;
        }

        String service = textOrNull(config.get("service"));
        if (service == null) {
            config.put("service", type);
        } else if (!type.equals(service)) {
            throw invalid("Service node config.service must equal node type.");
        } else {
            config.put("service", service);
        }

        if (ROLE_START.equals(role) && textOrNull(config.get("source_mode")) == null) {
            String legacyMode = textOrNull(config.get("mode"));
            if (legacyMode != null) {
                config.put("source_mode", legacyMode);
            }
        }
    }

    private Object normalizeSourceOutputDataType(
            String role,
            String type,
            Map<String, Object> config,
            Object rawOutputDataType
    ) {
        if (!ROLE_START.equals(role)) {
            return rawOutputDataType;
        }

        String sourceModeKey = textOrNull(config.get("source_mode"));
        if (sourceModeKey == null) {
            return rawOutputDataType;
        }

        SourceMode sourceMode = catalogService.findSourceMode(type, sourceModeKey);
        String canonicalInputType = sourceMode != null ? textOrNull(sourceMode.getCanonicalInputType()) : null;
        if (canonicalInputType == null) {
            if (textOrNull(rawOutputDataType) == null) {
                throw invalid("Start node outputDataType is required.");
            }
            return rawOutputDataType;
        }

        String outputDataType = textOrNull(rawOutputDataType);
        if (outputDataType != null && !canonicalInputType.equals(outputDataType)) {
            throw invalid("Start node outputDataType must match source mode.");
        }
        return canonicalInputType;
    }

    private void normalizeMiddleNodes(
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            ProcessorActionLookup processorActionLookup,
            ProcessingMethodLookup processingMethodLookup
    ) {
        for (Map<String, Object> node : nodes) {
            if (!ROLE_MIDDLE.equals(textOrNull(node.get("role")))) {
                continue;
            }

            String type = requiredText(node.get("type"), "Node type is required.");
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) node.get("config");
            MiddleNodeSpec middleNode = WorkflowGenerationSupport.SUPPORTED_PROCESSING_METHOD_NODE_TYPES.contains(type)
                    ? normalizeProcessingMethodNodeConfig(node, type, config, nodes, edges, processingMethodLookup)
                    : normalizeProcessorNodeConfig(node, type, config, nodes, edges, processorActionLookup);

            node.put("dataType", middleNode.dataType());
            node.put("label", middleNode.label());
            node.put("outputDataType", normalizeOutputDataType(node.get("outputDataType"), middleNode));
        }
    }

    private void normalizeSinkInputDataTypes(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Map<String, Object>> nodesById = indexNodesById(nodes);

        for (Map<String, Object> node : nodes) {
            if (!ROLE_END.equals(textOrNull(node.get("role")))) {
                continue;
            }

            String sinkInputDataType = resolveIncomingOutputDataType(
                    node,
                    nodesById,
                    edges,
                    "End node input dataType is required."
            );
            String currentDataType = textOrNull(node.get("dataType"));
            if (currentDataType != null && !currentDataType.equals(sinkInputDataType)) {
                throw invalid("End node dataType must match previous outputDataType.");
            }

            String sinkType = requiredText(node.get("type"), "Node type is required.");
            validateSinkInputDataType(sinkType, sinkInputDataType);
            node.put("dataType", sinkInputDataType);
            node.remove("outputDataType");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateBranchEdges(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        for (Map<String, Object> node : nodes) {
            if (!CONDITION_BRANCH_NODE_TYPE.equals(textOrNull(node.get("type")))) {
                continue;
            }

            Object rawConfig = node.get("config");
            if (!(rawConfig instanceof Map<?, ?>)) {
                throw invalid("Branch node config is required.");
            }

            Map<String, Object> config = (Map<String, Object>) rawConfig;
            Set<String> selectedBranchKeys = new HashSet<>(branchSelections(config));
            if (selectedBranchKeys.isEmpty()) {
                throw invalid("Branch node choice selections are required.");
            }
            Set<String> expectedEdgeBranchKeys = expectedBranchEdgeKeys(node, config, selectedBranchKeys);

            String nodeId = requiredText(node.get("id"), "Node id is required.");
            Set<String> edgeBranchKeys = new HashSet<>();
            int outgoingCount = 0;
            for (Map<String, Object> edge : edges) {
                if (!nodeId.equals(textOrNull(edge.get("source")))) {
                    continue;
                }

                outgoingCount++;
                String label = textOrNull(edge.get("label"));
                String sourceHandle = textOrNull(edge.get("sourceHandle"));
                String targetHandle = textOrNull(edge.get("targetHandle"));
                if (label == null || sourceHandle == null || targetHandle == null) {
                    throw invalid("Branch edge label, sourceHandle, and targetHandle are required.");
                }
                if (!label.equals(sourceHandle)) {
                    throw invalid("Branch edge label must match sourceHandle.");
                }
                if (!"input".equals(targetHandle)) {
                    throw invalid("Branch edge targetHandle must be input.");
                }
                if (!expectedEdgeBranchKeys.contains(label)) {
                    throw invalid("Branch edge label is not selected in branch config: " + label);
                }
                if (!edgeBranchKeys.add(label)) {
                    throw invalid("Duplicate branch edge label: " + label);
                }
            }

            if (outgoingCount < 2) {
                throw invalid("Branch node must have at least two outgoing branches.");
            }
            if (!edgeBranchKeys.equals(expectedEdgeBranchKeys)) {
                throw invalid("Branch edge labels must match selected branch config.");
            }
        }
    }

    private Set<String> expectedBranchEdgeKeys(
            Map<String, Object> node,
            Map<String, Object> config,
            Set<String> selectedBranchKeys
    ) {
        String choiceActionId = firstText(
                config.get("choiceActionId"),
                config.get("choice_action_id"),
                config.get("actionId"),
                config.get("action_id"),
                config.get("action")
        );
        if (!CONTENT_BRANCH_ACTION_ID.equals(choiceActionId)) {
            return selectedBranchKeys;
        }

        return new LinkedHashSet<>(expandedContentBranchKeys(textOrNull(node.get("dataType")), selectedBranchKeys));
    }

    private void validateGeneratedDataFlow(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Map<String, Object>> nodesById = indexNodesById(nodes);

        for (Map<String, Object> node : nodes) {
            String role = textOrNull(node.get("role"));
            String dataType = textOrNull(node.get("dataType"));
            String outputDataType = textOrNull(node.get("outputDataType"));

            if (ROLE_START.equals(role)) {
                if (dataType != null) {
                    throw invalid("Start node dataType must be empty.");
                }
                if (outputDataType == null) {
                    throw invalid("Start node outputDataType is required.");
                }
                continue;
            }

            if (ROLE_MIDDLE.equals(role)) {
                if (dataType == null) {
                    throw invalid("Middle node input dataType is required.");
                }
                if (outputDataType == null) {
                    throw invalid("Middle node outputDataType is required.");
                }
                continue;
            }

            if (ROLE_END.equals(role)) {
                if (dataType == null) {
                    throw invalid("End node input dataType is required.");
                }
                if (outputDataType != null) {
                    throw invalid("End node outputDataType must be empty.");
                }
            }
        }

        for (Map<String, Object> edge : edges) {
            Map<String, Object> sourceNode = nodesById.get(textOrNull(edge.get("source")));
            Map<String, Object> targetNode = nodesById.get(textOrNull(edge.get("target")));
            if (sourceNode == null || targetNode == null) {
                throw invalid("Edge references an unknown node.");
            }

            String sourceOutputDataType = resolveEdgeSourceOutputDataType(sourceNode, edge);
            String targetInputDataType = textOrNull(targetNode.get("dataType"));
            if (sourceOutputDataType == null) {
                throw invalid("Edge source outputDataType is required.");
            }
            if (targetInputDataType == null) {
                throw invalid("Edge target dataType is required.");
            }
            if (!sourceOutputDataType.equals(targetInputDataType)) {
                throw invalid("Edge dataType must match source outputDataType.");
            }
        }
    }

    private MiddleNodeSpec normalizeProcessorNodeConfig(
            Map<String, Object> node,
            String type,
            Map<String, Object> config,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            ProcessorActionLookup processorActionLookup
    ) {
        String choiceActionId = firstText(
                config.get("choiceActionId"),
                config.get("choice_action_id"),
                config.get("actionId"),
                config.get("action_id"),
                config.get("action")
        );
        if (choiceActionId == null) {
            throw invalid("Middle node choice action is required.");
        }

        String dataType = resolveInputDataType(node, nodes, edges);
        ProcessorActionSpec processorAction = resolveProcessorAction(dataType, choiceActionId, processorActionLookup);
        if (!type.equals(processorAction.nodeType())) {
            throw invalid("Middle node type must match selected processor action.");
        }

        String choiceNodeType = firstText(config.get("choiceNodeType"), config.get("choice_node_type"));
        if (choiceNodeType != null && !type.equals(choiceNodeType)) {
            throw invalid("Middle node choiceNodeType must match node type.");
        }

        config.put("choiceActionId", processorAction.id());
        config.put("choiceNodeType", processorAction.nodeType());
        config.put("isConfigured", isGeneratedProcessorActionConfigured(processorAction));
        return new MiddleNodeSpec(
                processorAction.dataType(),
                processorAction.id(),
                processorAction.label(),
                processorAction.nodeType(),
                processorAction.outputDataType(),
                "Middle node outputDataType must match selected processor action."
        );
    }

    private MiddleNodeSpec normalizeProcessingMethodNodeConfig(
            Map<String, Object> node,
            String type,
            Map<String, Object> config,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            ProcessingMethodLookup processingMethodLookup
    ) {
        String choiceActionId = firstText(
                config.get("choiceActionId"),
                config.get("choice_action_id"),
                config.get("actionId"),
                config.get("action_id"),
                config.get("action")
        );
        if (choiceActionId == null) {
            throw invalid("Middle node processing method is required.");
        }

        String dataType = resolveInputDataType(node, nodes, edges);
        ProcessingMethodSpec processingMethod = resolveProcessingMethod(
                dataType,
                choiceActionId,
                processingMethodLookup
        );
        if (!type.equals(processingMethod.nodeType())) {
            throw invalid("Middle node type must match selected processing method.");
        }

        String choiceNodeType = firstText(config.get("choiceNodeType"), config.get("choice_node_type"));
        if (choiceNodeType != null && !type.equals(choiceNodeType)) {
            throw invalid("Middle node choiceNodeType must match node type.");
        }

        config.put("choiceActionId", processingMethod.id());
        config.put("choiceNodeType", processingMethod.nodeType());
        if (processingMethod.requiresFollowUp()) {
            List<String> branchSelections = normalizeBranchSelections(config, processingMethod);
            if (branchSelections.isEmpty()) {
                throw invalid("Branch node choice selections are required.");
            }
            config.put(CHOICE_SELECTIONS_KEY, Map.of(BRANCH_CONFIG_KEY, branchSelections));
            config.put("isConfigured", true);
        } else {
            config.put("isConfigured", true);
        }
        return new MiddleNodeSpec(
                processingMethod.dataType(),
                processingMethod.id(),
                processingMethod.label(),
                processingMethod.nodeType(),
                processingMethod.outputDataType(),
                "Middle node outputDataType must match selected processing method."
        );
    }

    private String resolveInputDataType(
            Map<String, Object> node,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges
    ) {
        String explicitDataType = textOrNull(node.get("dataType"));
        if (explicitDataType != null) {
            return explicitDataType;
        }

        Map<String, Map<String, Object>> nodesById = indexNodesById(nodes);
        return resolveIncomingOutputDataType(
                node,
                nodesById,
                edges,
                "Middle node input dataType is required."
        );
    }

    private String resolveIncomingOutputDataType(
            Map<String, Object> node,
            Map<String, Map<String, Object>> nodesById,
            List<Map<String, Object>> edges,
            String missingMessage
    ) {
        String nodeId = requiredText(node.get("id"), "Node id is required.");
        Set<String> candidates = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            if (!nodeId.equals(textOrNull(edge.get("target")))) {
                continue;
            }

            String sourceId = textOrNull(edge.get("source"));
            Map<String, Object> sourceNode = sourceId != null ? nodesById.get(sourceId) : null;
            if (sourceNode == null) {
                continue;
            }

            String outputDataType = resolveEdgeSourceOutputDataType(sourceNode, edge);
            if (outputDataType != null) {
                candidates.add(outputDataType);
            }
        }

        if (candidates.isEmpty()) {
            throw invalid(missingMessage);
        }
        if (candidates.size() > 1) {
            throw invalid("Node has multiple input data types.");
        }
        return candidates.iterator().next();
    }

    @SuppressWarnings("unchecked")
    private String resolveEdgeSourceOutputDataType(Map<String, Object> sourceNode, Map<String, Object> edge) {
        if (sourceNode == null) {
            return null;
        }

        if (!CONDITION_BRANCH_NODE_TYPE.equals(textOrNull(sourceNode.get("type")))) {
            return textOrNull(sourceNode.get("outputDataType"));
        }

        Object rawConfig = sourceNode.get("config");
        if (!(rawConfig instanceof Map<?, ?> rawConfigMap)) {
            return textOrNull(sourceNode.get("outputDataType"));
        }

        Map<String, Object> config = (Map<String, Object>) rawConfigMap;
        String choiceActionId = firstText(
                config.get("choiceActionId"),
                config.get("choice_action_id"),
                config.get("actionId"),
                config.get("action_id"),
                config.get("action")
        );
        String branchKey = firstText(edge.get("sourceHandle"), edge.get("label"));

        if (BRANCH_BY_FILENAME_ACTION_ID.equals(choiceActionId) && isFilenameBranchKey(branchKey)) {
            return FILE_LIST_DATA_TYPE;
        }
        if (CONTENT_BRANCH_ACTION_ID.equals(choiceActionId) && isContentBranchKey(branchKey)) {
            return TEXT_DATA_TYPE;
        }
        if (CLASSIFY_BY_FIELD_ACTION_ID.equals(choiceActionId) && isFieldValueBranchKey(branchKey)) {
            return SPREADSHEET_DATA_TYPE;
        }

        Map<String, String> outputTypes = BRANCH_EDGE_OUTPUT_DATA_TYPES.get(choiceActionId);
        if (outputTypes != null && branchKey != null && outputTypes.containsKey(branchKey)) {
            return outputTypes.get(branchKey);
        }

        return textOrNull(sourceNode.get("outputDataType"));
    }

    private boolean isFilenameBranchKey(String value) {
        return OTHER_BRANCH_KEY.equals(value)
                || (value != null && FILENAME_BRANCH_KEY_PATTERN.matcher(value).matches());
    }

    private boolean isContentBranchKey(String value) {
        if (value == null) {
            return false;
        }
        return CONTENT_BRANCH_KEYS_BY_PRESET.values().stream()
                .flatMap(List::stream)
                .anyMatch(value::equals);
    }

    private boolean isFieldValueBranchKey(String value) {
        return OTHER_BRANCH_KEY.equals(value)
                || (value != null && FIELD_VALUE_BRANCH_KEY_PATTERN.matcher(value).matches());
    }

    private void validateSinkInputDataType(String serviceKey, String dataType) {
        SinkService sinkService = catalogService.findSinkService(serviceKey);
        List<String> acceptedInputTypes = sinkService.getAcceptedInputTypes();
        if (acceptedInputTypes == null || !acceptedInputTypes.contains(dataType)) {
            throw invalid("Sink service does not support input dataType: " + serviceKey + " / " + dataType);
        }
    }

    private Map<String, Map<String, Object>> indexNodesById(List<Map<String, Object>> nodes) {
        Map<String, Map<String, Object>> nodesById = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = textOrNull(node.get("id"));
            if (nodeId != null) {
                nodesById.put(nodeId, node);
            }
        }
        return nodesById;
    }

    private Map<String, Object> normalizeConfig(Object rawConfig) {
        if (rawConfig == null) {
            return new LinkedHashMap<>();
        }
        if (!(rawConfig instanceof Map<?, ?> rawConfigMap)) {
            throw invalid("Node config must be an object.");
        }
        rejectRuntimeFields(rawConfigMap, "node config");

        Map<String, Object> config = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawConfigMap.entrySet()) {
            if (entry.getKey() != null) {
                config.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return config;
    }

    private Map<String, Object> normalizePosition(Object rawPosition, int index) {
        if (!(rawPosition instanceof Map<?, ?> rawPositionMap)) {
            return Map.of("x", index * NODE_GAP_X, "y", 0);
        }

        Object x = rawPositionMap.get("x");
        Object y = rawPositionMap.get("y");
        if (!(x instanceof Number) || !(y instanceof Number)) {
            return Map.of("x", index * NODE_GAP_X, "y", 0);
        }
        return Map.of("x", ((Number) x).doubleValue(), "y", ((Number) y).doubleValue());
    }

    private Object normalizeOutputDataType(Object rawOutputDataType, MiddleNodeSpec middleNode) {
        if (middleNode == null || middleNode.outputDataType() == null) {
            return rawOutputDataType;
        }

        String outputDataType = textOrNull(rawOutputDataType);
        if (outputDataType != null && !middleNode.outputDataType().equals(outputDataType)) {
            throw invalid(middleNode.outputMismatchMessage());
        }
        return middleNode.outputDataType();
    }

    private Set<String> branchOptionIds(BranchConfig branchConfig) {
        if (branchConfig == null || branchConfig.getOptions() == null) {
            return Set.of();
        }

        Set<String> optionIds = new HashSet<>();
        for (Option option : branchConfig.getOptions()) {
            String optionId = option != null ? textOrNull(option.getId()) : null;
            if (optionId != null) {
                optionIds.add(optionId);
            }
        }
        return optionIds;
    }

    private List<String> normalizeBranchSelections(
            Map<String, Object> config,
            ProcessingMethodSpec processingMethod
    ) {
        Set<String> selectedBranchKeys = new HashSet<>(branchSelections(config));
        if (selectedBranchKeys.isEmpty()) {
            return List.of();
        }
        if (BRANCH_BY_FILENAME_ACTION_ID.equals(processingMethod.id())) {
            return normalizeFilenameBranchSelections(config, selectedBranchKeys);
        }
        if (CONTENT_BRANCH_ACTION_ID.equals(processingMethod.id())) {
            return normalizeContentBranchSelections(processingMethod.dataType(), selectedBranchKeys);
        }
        if (CLASSIFY_BY_FIELD_ACTION_ID.equals(processingMethod.id())) {
            return normalizeFieldValueBranchSelections(processingMethod.dataType(), config, selectedBranchKeys);
        }
        if (!processingMethod.branchOptionIds().containsAll(selectedBranchKeys)) {
            throw invalid("Unsupported branch selection.");
        }
        return selectedBranchKeys.stream()
                .sorted()
                .toList();
    }

    private List<String> normalizeFilenameBranchSelections(
            Map<String, Object> config,
            Set<String> selectedBranchKeys
    ) {
        List<String> ruleKeys = filenameRuleKeys(config);
        if (ruleKeys.isEmpty()) {
            throw invalid("Filename branch filenameRules are required.");
        }

        Set<String> supportedKeys = new LinkedHashSet<>(ruleKeys);
        supportedKeys.add(OTHER_BRANCH_KEY);
        if (!supportedKeys.containsAll(selectedBranchKeys)) {
            throw invalid("Unsupported branch selection.");
        }
        if (!selectedBranchKeys.containsAll(ruleKeys)) {
            throw invalid("Filename branch selections must include every filenameRules key.");
        }

        List<String> normalized = new ArrayList<>(ruleKeys);
        if (selectedBranchKeys.contains(OTHER_BRANCH_KEY)) {
            normalized.add(OTHER_BRANCH_KEY);
        }
        return normalized;
    }

    private List<String> normalizeContentBranchSelections(String dataType, Set<String> selectedBranchKeys) {
        if (selectedBranchKeys.size() != 1) {
            throw invalid("Content branch must select exactly one preset.");
        }

        String preset = selectedBranchKeys.iterator().next();
        if (!CONTENT_BRANCH_KEYS_BY_PRESET.containsKey(preset)) {
            throw invalid("Unsupported content branch preset.");
        }
        if (!CONTENT_BRANCH_PRESETS_BY_DATA_TYPE
                .getOrDefault(dataType, Set.of())
                .contains(preset)) {
            throw invalid("Content branch preset is not supported for input dataType.");
        }
        return List.of(preset);
    }

    private List<String> normalizeFieldValueBranchSelections(
            String dataType,
            Map<String, Object> config,
            Set<String> selectedBranchKeys
    ) {
        if (!SPREADSHEET_DATA_TYPE.equals(dataType)) {
            throw invalid("Field value branch requires SPREADSHEET_DATA input.");
        }

        List<String> ruleKeys = fieldValueRuleKeys(config);
        if (ruleKeys.isEmpty()) {
            throw invalid("Field value branch fieldValueRules are required.");
        }

        Set<String> supportedKeys = new LinkedHashSet<>(ruleKeys);
        supportedKeys.add(OTHER_BRANCH_KEY);
        if (!supportedKeys.containsAll(selectedBranchKeys)) {
            throw invalid("Unsupported branch selection.");
        }
        if (!selectedBranchKeys.containsAll(ruleKeys)) {
            throw invalid("Field value branch selections must include every fieldValueRules key.");
        }
        if (!selectedBranchKeys.contains(OTHER_BRANCH_KEY)) {
            throw invalid("Field value branch selections must include other.");
        }

        List<String> normalized = new ArrayList<>(ruleKeys);
        normalized.add(OTHER_BRANCH_KEY);
        return normalized;
    }

    private List<String> expandedContentBranchKeys(String dataType, Set<String> selectedBranchKeys) {
        List<String> presets = normalizeContentBranchSelections(dataType, selectedBranchKeys);
        return CONTENT_BRANCH_KEYS_BY_PRESET.getOrDefault(presets.getFirst(), List.of());
    }

    private List<String> filenameRuleKeys(Map<String, Object> config) {
        Object rawRules = config.get("filenameRules");
        if (!(rawRules instanceof List<?>)) {
            rawRules = config.get("filename_rules");
        }
        if (!(rawRules instanceof List<?> rules)) {
            return List.of();
        }

        List<String> keys = new ArrayList<>();
        for (Object rule : rules) {
            if (!(rule instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            String key = firstText(ruleMap.get("key"), ruleMap.get("id"));
            if (key == null || !FILENAME_BRANCH_KEY_PATTERN.matcher(key).matches()) {
                continue;
            }
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private List<String> fieldValueRuleKeys(Map<String, Object> config) {
        Object rawRules = config.get("fieldValueRules");
        if (!(rawRules instanceof List<?>)) {
            rawRules = config.get("field_value_rules");
        }
        if (!(rawRules instanceof List<?> rules)) {
            return List.of();
        }

        List<String> keys = new ArrayList<>();
        int expectedIndex = 1;
        for (Object rule : rules) {
            if (!(rule instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            String key = firstText(ruleMap.get("key"), ruleMap.get("id"));
            if (key == null || !FIELD_VALUE_BRANCH_KEY_PATTERN.matcher(key).matches()) {
                continue;
            }
            if (!key.equals("field_value_" + expectedIndex)) {
                throw invalid("fieldValueRules keys must start at field_value_1 and be sequential.");
            }
            String field = firstText(ruleMap.get("field"), ruleMap.get("column"));
            String value = firstText(ruleMap.get("value"), ruleMap.get("equals"));
            String label = firstText(ruleMap.get("label"), value);
            if (field == null || value == null || label == null) {
                continue;
            }
            if (!keys.contains(key)) {
                keys.add(key);
            }
            expectedIndex++;
        }
        return keys;
    }

    private List<String> branchSelections(Map<String, Object> config) {
        Object rawSelections = config.get(CHOICE_SELECTIONS_KEY);
        if (!(rawSelections instanceof Map<?, ?> selections)) {
            return List.of();
        }

        Set<String> selectedBranchKeys = new HashSet<>();
        appendBranchSelections(selectedBranchKeys, selections.get(BRANCH_CONFIG_KEY));
        appendBranchSelectionsForKey(selectedBranchKeys, selections, textOrNull(config.get("choiceActionId")));
        appendBranchSelectionsForKey(selectedBranchKeys, selections, textOrNull(config.get("choice_action_id")));
        return selectedBranchKeys.stream()
                .sorted()
                .toList();
    }

    private void appendBranchSelectionsForKey(
            Set<String> selectedBranchKeys,
            Map<?, ?> selections,
            String key
    ) {
        if (key != null) {
            appendBranchSelections(selectedBranchKeys, selections.get(key));
        }
    }

    private void appendBranchSelections(Set<String> selectedBranchKeys, Object rawValue) {
        if (rawValue instanceof List<?> values) {
            values.forEach(value -> {
                String selectedKey = textOrNull(value);
                if (selectedKey != null) {
                    selectedBranchKeys.add(selectedKey);
                }
            });
            return;
        }

        String selectedKey = textOrNull(rawValue);
        if (selectedKey != null) {
            selectedBranchKeys.add(selectedKey);
        }
    }

    private ProcessorActionLookup buildProcessorActionLookup() {
        MappingRules mappingRules = choiceMappingService.getMappingRules();
        if (mappingRules == null || mappingRules.getDataTypes() == null) {
            return new ProcessorActionLookup(new HashMap<>(), new HashMap<>());
        }

        Map<String, List<ProcessorActionSpec>> byActionId = new HashMap<>();
        Map<String, Map<String, ProcessorActionSpec>> byDataTypeAndActionId = new HashMap<>();
        for (Map.Entry<String, DataTypeConfig> entry : mappingRules.getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            DataTypeConfig dataTypeConfig = entry.getValue();
            if (dataType == null || dataTypeConfig == null || dataTypeConfig.getActions() == null) {
                continue;
            }

            for (Action action : dataTypeConfig.getActions()) {
                if (!WorkflowGenerationSupport.isSupportedGeneratedProcessorAction(dataType, action)) {
                    continue;
                }

                String actionId = textOrNull(action.getId());
                if (actionId == null) {
                    continue;
                }

                ProcessorActionSpec actionSpec = new ProcessorActionSpec(
                        dataType,
                        actionId,
                        textOrNull(action.getLabel()),
                        textOrNull(action.getNodeType()),
                        textOrNull(action.getOutputDataType()),
                        action.getFollowUp() != null,
                        action.getBranchConfig() != null,
                        Boolean.TRUE.equals(action.getGenerationReadyWithoutFollowUp())
                );
                byActionId.computeIfAbsent(actionId, ignored -> new ArrayList<>()).add(actionSpec);
                byDataTypeAndActionId
                        .computeIfAbsent(dataType, ignored -> new HashMap<>())
                        .putIfAbsent(actionId, actionSpec);
            }
        }
        return new ProcessorActionLookup(byActionId, byDataTypeAndActionId);
    }

    private ProcessingMethodLookup buildProcessingMethodLookup() {
        MappingRules mappingRules = choiceMappingService.getMappingRules();
        if (mappingRules == null || mappingRules.getDataTypes() == null) {
            return new ProcessingMethodLookup(new HashMap<>(), new HashMap<>());
        }

        Map<String, List<ProcessingMethodSpec>> byOptionId = new HashMap<>();
        Map<String, Map<String, ProcessingMethodSpec>> byDataTypeAndOptionId = new HashMap<>();
        for (Map.Entry<String, DataTypeConfig> entry : mappingRules.getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            DataTypeConfig dataTypeConfig = entry.getValue();
            if (dataType == null || dataTypeConfig == null) {
                continue;
            }

            if (dataTypeConfig.getProcessingMethod() != null
                    && dataTypeConfig.getProcessingMethod().getOptions() != null) {
                for (Option option : dataTypeConfig.getProcessingMethod().getOptions()) {
                    if (option == null
                            || !WorkflowGenerationSupport.SUPPORTED_PROCESSING_METHOD_NODE_TYPES.contains(option.getNodeType())) {
                        continue;
                    }

                    String optionId = textOrNull(option.getId());
                    if (optionId == null) {
                        continue;
                    }

                    registerProcessingMethodSpec(
                            byOptionId,
                            byDataTypeAndOptionId,
                            new ProcessingMethodSpec(
                                    dataType,
                                    optionId,
                                    textOrNull(option.getLabel()),
                                    textOrNull(option.getNodeType()),
                                    textOrNull(option.getOutputDataType()),
                                    option.getBranchConfig() != null,
                                    branchOptionIds(option.getBranchConfig())
                            )
                    );
                }
            }

            if (dataTypeConfig.getActions() == null) {
                continue;
            }

            for (Action action : dataTypeConfig.getActions()) {
                if (!WorkflowGenerationSupport.isSupportedGeneratedBranchAction(dataType, action)) {
                    continue;
                }

                String actionId = textOrNull(action.getId());
                if (actionId == null) {
                    continue;
                }

                registerProcessingMethodSpec(
                        byOptionId,
                        byDataTypeAndOptionId,
                        new ProcessingMethodSpec(
                                dataType,
                                actionId,
                                textOrNull(action.getLabel()),
                                textOrNull(action.getNodeType()),
                                textOrNull(action.getOutputDataType()),
                                action.getBranchConfig() != null,
                                branchOptionIds(action.getBranchConfig())
                        )
                );
            }
        }
        return new ProcessingMethodLookup(byOptionId, byDataTypeAndOptionId);
    }

    private void registerProcessingMethodSpec(
            Map<String, List<ProcessingMethodSpec>> byOptionId,
            Map<String, Map<String, ProcessingMethodSpec>> byDataTypeAndOptionId,
            ProcessingMethodSpec methodSpec
    ) {
        byOptionId.computeIfAbsent(methodSpec.id(), ignored -> new ArrayList<>()).add(methodSpec);
        byDataTypeAndOptionId
                .computeIfAbsent(methodSpec.dataType(), ignored -> new HashMap<>())
                .putIfAbsent(methodSpec.id(), methodSpec);
    }

    private ProcessorActionSpec resolveProcessorAction(
            Object rawDataType,
            String actionId,
            ProcessorActionLookup processorActionLookup
    ) {
        String dataType = textOrNull(rawDataType);
        if (dataType != null) {
            Map<String, ProcessorActionSpec> dataTypeActions = processorActionLookup.byDataTypeAndActionId().get(dataType);
            if (dataTypeActions != null && dataTypeActions.containsKey(actionId)) {
                return dataTypeActions.get(actionId);
            }
            throw invalid("Unsupported processor action for dataType: " + actionId);
        }

        List<ProcessorActionSpec> candidates = processorActionLookup.byActionId().getOrDefault(actionId, List.of());
        if (candidates.isEmpty()) {
            throw invalid("Unsupported processor action: " + actionId);
        }
        if (candidates.size() == 1 || hasSameProcessorPresentation(candidates)) {
            return candidates.getFirst();
        }
        throw invalid("Middle node dataType is required for processor action: " + actionId);
    }

    private ProcessingMethodSpec resolveProcessingMethod(
            Object rawDataType,
            String optionId,
            ProcessingMethodLookup processingMethodLookup
    ) {
        String dataType = textOrNull(rawDataType);
        if (dataType != null) {
            Map<String, ProcessingMethodSpec> dataTypeMethods =
                    processingMethodLookup.byDataTypeAndOptionId().get(dataType);
            if (dataTypeMethods != null && dataTypeMethods.containsKey(optionId)) {
                return dataTypeMethods.get(optionId);
            }
            throw invalid("Unsupported processing method for dataType: " + optionId);
        }

        List<ProcessingMethodSpec> candidates = processingMethodLookup.byOptionId().getOrDefault(optionId, List.of());
        if (candidates.isEmpty()) {
            throw invalid("Unsupported processing method: " + optionId);
        }
        if (candidates.size() == 1 || hasSameProcessingMethodPresentation(candidates)) {
            return candidates.getFirst();
        }
        throw invalid("Middle node dataType is required for processing method: " + optionId);
    }

    private boolean hasSameProcessorPresentation(List<ProcessorActionSpec> candidates) {
        ProcessorActionSpec first = candidates.getFirst();
        return candidates.stream().allMatch(candidate ->
                Objects.equals(first.label(), candidate.label())
                        && Objects.equals(first.nodeType(), candidate.nodeType())
                        && Objects.equals(first.outputDataType(), candidate.outputDataType())
        );
    }

    private boolean isGeneratedProcessorActionConfigured(ProcessorActionSpec processorAction) {
        if (processorAction.hasBranchConfig()) {
            return false;
        }
        if (!processorAction.hasFollowUp()) {
            return true;
        }
        if (!processorAction.generationReadyWithoutFollowUp()) {
            return false;
        }
        if (!PROMPT_NODE_TYPES.contains(processorAction.nodeType())) {
            return false;
        }
        return choicePromptResolver.hasActionPrompt(processorAction.dataType(), processorAction.id());
    }

    private boolean hasSameProcessingMethodPresentation(List<ProcessingMethodSpec> candidates) {
        ProcessingMethodSpec first = candidates.getFirst();
        return candidates.stream().allMatch(candidate ->
                Objects.equals(first.label(), candidate.label())
                        && Objects.equals(first.nodeType(), candidate.nodeType())
                        && Objects.equals(first.outputDataType(), candidate.outputDataType())
        );
    }

    private void rejectRuntimeFields(Map<?, ?> value, String objectName) {
        for (Object key : value.keySet()) {
            if (key != null && String.valueOf(key).startsWith("runtime_")) {
                throw invalid("Generated " + objectName + " cannot include runtime fields.");
            }
        }
    }

    private long countRole(List<Map<String, Object>> nodes, String role) {
        return nodes.stream()
                .filter(node -> role.equals(node.get("role")))
                .count();
    }

    private void validateRole(String role) {
        if (!Set.of(ROLE_START, ROLE_MIDDLE, ROLE_END).contains(role)) {
            throw invalid("Unsupported node role: " + role);
        }
    }

    private String normalizedName(Object value) {
        String name = textOrNull(value);
        return name != null ? name : "AI Generated Workflow";
    }

    private String requiredText(Object value, String message) {
        String text = textOrNull(value);
        if (text == null) {
            throw invalid(message);
        }
        return text;
    }

    private String textOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = textOrNull(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putTextIfPresent(Map<String, Object> target, String key, Object value) {
        String text = textOrNull(value);
        if (text != null) {
            target.put(key, text);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.LLM_GENERATION_FAILED, message);
    }

    private record ProcessorActionLookup(
            Map<String, List<ProcessorActionSpec>> byActionId,
            Map<String, Map<String, ProcessorActionSpec>> byDataTypeAndActionId
    ) {
    }

    private record ProcessorActionSpec(
            String dataType,
            String id,
            String label,
            String nodeType,
            String outputDataType,
            boolean hasFollowUp,
            boolean hasBranchConfig,
            boolean generationReadyWithoutFollowUp
    ) {
    }

    private record ProcessingMethodLookup(
            Map<String, List<ProcessingMethodSpec>> byOptionId,
            Map<String, Map<String, ProcessingMethodSpec>> byDataTypeAndOptionId
    ) {
    }

    private record ProcessingMethodSpec(
            String dataType,
            String id,
            String label,
            String nodeType,
            String outputDataType,
            boolean requiresFollowUp,
            Set<String> branchOptionIds
    ) {
    }

    private record MiddleNodeSpec(
            String dataType,
            String id,
            String label,
            String nodeType,
            String outputDataType,
            String outputMismatchMessage
    ) {
    }
}
