package org.github.flowify.workflow.service.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
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

    private final ObjectMapper objectMapper;
    private final WorkflowValidator workflowValidator;
    private final ChoiceMappingService choiceMappingService;

    public WorkflowCreateRequest toCreateRequest(Map<String, Object> generated) {
        if (generated == null) {
            throw invalid("Generated workflow is empty.");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("name", normalizedName(generated.get("name")));
        normalized.put("description", generated.get("description"));

        List<Map<String, Object>> nodes = normalizeNodes(generated.get("nodes"));
        List<Map<String, Object>> edges = normalizeEdges(generated.get("edges"), nodes);
        validateTopology(nodes, edges);

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
        ProcessorActionLookup processorActionLookup = buildProcessorActionLookup();
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
            ProcessorActionSpec processorAction = normalizeProcessorNodeConfig(
                    role,
                    type,
                    config,
                    rawNode.get("dataType"),
                    processorActionLookup
            );
            validateNodeType(role, type, config);

            node.put("id", id);
            node.put("category", requiredText(rawNode.get("category"), "Node category is required."));
            node.put("type", type);
            node.put("label", normalizeNodeLabel(role, rawNode.get("label"), processorAction));
            node.put("role", role);
            node.put("position", normalizePosition(rawNode.get("position"), index));
            node.put("config", config);
            putIfPresent(node, "dataType", rawNode.get("dataType"));
            putIfPresent(node, "outputDataType", normalizeOutputDataType(rawNode.get("outputDataType"), processorAction));
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
        if (startCount != 1 || middleCount > 1 || endCount != 1) {
            throw invalid("Generated workflow must have exactly one start, up to one middle, and one end node.");
        }

        if (nodes.size() > 1 && edges.size() != nodes.size() - 1) {
            throw invalid("Generated workflow must be a single path.");
        }

        Map<String, Integer> incoming = new HashMap<>();
        Map<String, Integer> outgoing = new HashMap<>();
        Map<String, String> nextBySource = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            incoming.put(String.valueOf(node.get("id")), 0);
            outgoing.put(String.valueOf(node.get("id")), 0);
        }

        for (Map<String, Object> edge : edges) {
            String source = String.valueOf(edge.get("source"));
            String target = String.valueOf(edge.get("target"));
            outgoing.compute(source, (key, value) -> value == null ? 1 : value + 1);
            incoming.compute(target, (key, value) -> value == null ? 1 : value + 1);
            if (nextBySource.put(source, target) != null) {
                throw invalid("Generated workflow cannot branch.");
            }
        }

        String startId = null;
        for (Map<String, Object> node : nodes) {
            String id = String.valueOf(node.get("id"));
            String role = String.valueOf(node.get("role"));
            if (ROLE_START.equals(role)) {
                startId = id;
            }
            if (incoming.get(id) > 1 || outgoing.get(id) > 1) {
                throw invalid("Generated workflow cannot branch or merge.");
            }
            validateNodeDegree(role, incoming.get(id), outgoing.get(id), nodes.size());
        }

        Set<String> visited = new HashSet<>();
        String current = startId;
        while (current != null) {
            if (!visited.add(current)) {
                throw invalid("Generated workflow cannot contain a cycle.");
            }
            current = nextBySource.get(current);
        }
        if (visited.size() != nodes.size()) {
            throw invalid("Generated workflow must connect every node from start to end.");
        }
    }

    private void validateNodeDegree(String role, int incoming, int outgoing, int nodeCount) {
        if (nodeCount == 1) {
            throw invalid("Generated workflow must include start and end nodes.");
        }
        if (ROLE_START.equals(role) && (incoming != 0 || outgoing != 1)) {
            throw invalid("Start node must be the first node in the path.");
        }
        if (ROLE_MIDDLE.equals(role) && (incoming != 1 || outgoing != 1)) {
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
        if (ROLE_MIDDLE.equals(role) && !WorkflowGenerationSupport.SUPPORTED_PROCESSORS.contains(type)) {
            throw invalid("Unsupported processor: " + type);
        }
        if (ROLE_END.equals(role) && !WorkflowGenerationSupport.SUPPORTED_SINKS.contains(type)) {
            throw invalid("Unsupported sink service: " + type);
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

    private ProcessorActionSpec normalizeProcessorNodeConfig(
            String role,
            String type,
            Map<String, Object> config,
            Object dataType,
            ProcessorActionLookup processorActionLookup
    ) {
        if (!ROLE_MIDDLE.equals(role)) {
            return null;
        }

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
        return processorAction;
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

    private String normalizeNodeLabel(String role, Object rawLabel, ProcessorActionSpec processorAction) {
        if (ROLE_MIDDLE.equals(role) && processorAction != null) {
            return processorAction.label();
        }
        return textOrNull(rawLabel);
    }

    private Object normalizeOutputDataType(Object rawOutputDataType, ProcessorActionSpec processorAction) {
        if (processorAction == null || processorAction.outputDataType() == null) {
            return rawOutputDataType;
        }

        String outputDataType = textOrNull(rawOutputDataType);
        if (outputDataType != null && !processorAction.outputDataType().equals(outputDataType)) {
            throw invalid("Middle node outputDataType must match selected processor action.");
        }
        return processorAction.outputDataType();
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
                if (action == null || !WorkflowGenerationSupport.SUPPORTED_PROCESSORS.contains(action.getNodeType())) {
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
                        textOrNull(action.getOutputDataType())
                );
                byActionId.computeIfAbsent(actionId, ignored -> new ArrayList<>()).add(actionSpec);
                byDataTypeAndActionId
                        .computeIfAbsent(dataType, ignored -> new HashMap<>())
                        .putIfAbsent(actionId, actionSpec);
            }
        }
        return new ProcessorActionLookup(byActionId, byDataTypeAndActionId);
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

    private boolean hasSameProcessorPresentation(List<ProcessorActionSpec> candidates) {
        ProcessorActionSpec first = candidates.getFirst();
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
            String outputDataType
    ) {
    }
}
