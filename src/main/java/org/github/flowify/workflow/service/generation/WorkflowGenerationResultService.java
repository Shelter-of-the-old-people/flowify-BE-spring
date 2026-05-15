package org.github.flowify.workflow.service.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            putIfPresent(node, "dataType", rawNode.get("dataType"));
            putIfPresent(node, "outputDataType", rawNode.get("outputDataType"));
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

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.LLM_GENERATION_FAILED, message);
    }
}
