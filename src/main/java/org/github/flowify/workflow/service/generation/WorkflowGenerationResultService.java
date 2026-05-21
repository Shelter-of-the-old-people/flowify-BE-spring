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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowGenerationResultService {

    private static final String ROLE_START = "start";
    private static final String ROLE_MIDDLE = "middle";
    private static final String ROLE_END = "end";
    private static final int NODE_GAP_X = 360;
    private static final int MAX_GENERATED_MIDDLE_COUNT = 3;
    private static final int MAX_GENERATED_END_COUNT = 3;
    private static final String CONDITION_BRANCH_NODE_TYPE = "CONDITION_BRANCH";
    private static final String CHOICE_SELECTIONS_KEY = "choiceSelections";
    private static final String BRANCH_CONFIG_KEY = "branch_config";
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
        for (Map<String, Object> node : nodes) {
            nodeIds.add(String.valueOf(node.get("id")));
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
            putIfPresent(edge, "label", rawEdge.get("label"));
            putIfPresent(edge, "sourceHandle", rawEdge.get("sourceHandle"));
            putIfPresent(edge, "targetHandle", rawEdge.get("targetHandle"));
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
                if (!selectedBranchKeys.contains(label)) {
                    throw invalid("Branch edge label is not selected in branch config: " + label);
                }
                if (!edgeBranchKeys.add(label)) {
                    throw invalid("Duplicate branch edge label: " + label);
                }
            }

            if (outgoingCount < 2) {
                throw invalid("Branch node must have at least two outgoing branches.");
            }
            if (!edgeBranchKeys.equals(selectedBranchKeys)) {
                throw invalid("Branch edge labels must match selected branch config.");
            }
        }
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

            String sourceOutputDataType = textOrNull(sourceNode.get("outputDataType"));
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

            String outputDataType = textOrNull(sourceNode.get("outputDataType"));
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
        if (!processingMethod.branchOptionIds().containsAll(selectedBranchKeys)) {
            throw invalid("Unsupported branch selection.");
        }
        return selectedBranchKeys.stream()
                .sorted()
                .toList();
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
            if (dataType == null
                    || dataTypeConfig == null
                    || dataTypeConfig.getProcessingMethod() == null
                    || dataTypeConfig.getProcessingMethod().getOptions() == null) {
                continue;
            }

            for (Option option : dataTypeConfig.getProcessingMethod().getOptions()) {
                if (option == null
                        || !WorkflowGenerationSupport.SUPPORTED_PROCESSING_METHOD_NODE_TYPES.contains(option.getNodeType())) {
                    continue;
                }

                String optionId = textOrNull(option.getId());
                if (optionId == null) {
                    continue;
                }

                ProcessingMethodSpec methodSpec = new ProcessingMethodSpec(
                        dataType,
                        optionId,
                        textOrNull(option.getLabel()),
                        textOrNull(option.getNodeType()),
                        textOrNull(option.getOutputDataType()),
                        option.getBranchConfig() != null,
                        branchOptionIds(option.getBranchConfig())
                );
                byOptionId.computeIfAbsent(optionId, ignored -> new ArrayList<>()).add(methodSpec);
                byDataTypeAndOptionId
                        .computeIfAbsent(dataType, ignored -> new HashMap<>())
                        .putIfAbsent(optionId, methodSpec);
            }
        }
        return new ProcessingMethodLookup(byOptionId, byDataTypeAndOptionId);
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
