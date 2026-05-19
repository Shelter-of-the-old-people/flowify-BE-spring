package org.github.flowify.workflow.service.generation;

import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Position;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowGenerationSnapshotService {

    private static final Set<String> SAFE_CONFIG_KEYS = Set.of(
            "service",
            "source_mode",
            "choiceActionId",
            "choiceNodeType",
            "keyword",
            "text_delivery_mode",
            "body_format",
            "action",
            "file_format",
            "write_mode",
            "message_template",
            "username",
            "to_source"
    );

    public Map<String, Object> buildSnapshot(WorkflowResponse workflow) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "id", workflow.getId());
        putIfPresent(snapshot, "name", workflow.getName());
        putIfPresent(snapshot, "description", workflow.getDescription());
        snapshot.put("nodes", snapshotNodes(workflow.getNodes()));
        snapshot.put("edges", snapshotEdges(workflow.getEdges()));
        snapshot.put("trigger", snapshotTrigger(workflow.getTrigger()));
        snapshot.put("nodeStatuses", snapshotNodeStatuses(workflow.getNodeStatuses()));
        return snapshot;
    }

    private List<Map<String, Object>> snapshotNodes(List<NodeDefinition> nodes) {
        if (nodes == null) {
            return List.of();
        }

        return nodes.stream()
                .map(this::snapshotNode)
                .toList();
    }

    private Map<String, Object> snapshotNode(NodeDefinition node) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "id", node.getId());
        putIfPresent(snapshot, "category", node.getCategory());
        putIfPresent(snapshot, "type", node.getType());
        putIfPresent(snapshot, "label", node.getLabel());
        putIfPresent(snapshot, "role", node.getRole());
        putIfPresent(snapshot, "position", snapshotPosition(node.getPosition()));
        putIfPresent(snapshot, "dataType", node.getDataType());
        putIfPresent(snapshot, "outputDataType", node.getOutputDataType());
        snapshot.put("configSummary", snapshotConfig(node.getConfig()));
        return snapshot;
    }

    private Map<String, Object> snapshotConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> safeConfig = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (SAFE_CONFIG_KEYS.contains(key) && value != null) {
                safeConfig.put(key, value);
            }
        }
        return safeConfig;
    }

    private Map<String, Object> snapshotPosition(Position position) {
        if (position == null) {
            return null;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("x", position.getX());
        snapshot.put("y", position.getY());
        return snapshot;
    }

    private List<Map<String, Object>> snapshotEdges(List<EdgeDefinition> edges) {
        if (edges == null) {
            return List.of();
        }

        return edges.stream()
                .map(this::snapshotEdge)
                .toList();
    }

    private Map<String, Object> snapshotEdge(EdgeDefinition edge) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "id", edge.getId());
        putIfPresent(snapshot, "source", edge.getSource());
        putIfPresent(snapshot, "target", edge.getTarget());
        putIfPresent(snapshot, "label", edge.getLabel());
        putIfPresent(snapshot, "sourceHandle", edge.getSourceHandle());
        putIfPresent(snapshot, "targetHandle", edge.getTargetHandle());
        return snapshot;
    }

    private Map<String, Object> snapshotTrigger(TriggerConfig trigger) {
        if (trigger == null) {
            return Map.of("type", "manual", "config", Map.of());
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "type", trigger.getType());
        snapshot.put("config", Map.of());
        return snapshot;
    }

    private List<Map<String, Object>> snapshotNodeStatuses(List<NodeStatusResponse> nodeStatuses) {
        if (nodeStatuses == null) {
            return List.of();
        }

        return nodeStatuses.stream()
                .map(this::snapshotNodeStatus)
                .toList();
    }

    private Map<String, Object> snapshotNodeStatus(NodeStatusResponse status) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "nodeId", status.getNodeId());
        snapshot.put("configured", status.isConfigured());
        snapshot.put("saveable", status.isSaveable());
        snapshot.put("choiceable", status.isChoiceable());
        snapshot.put("executable", status.isExecutable());
        putIfPresent(snapshot, "missingFields", status.getMissingFields());
        return snapshot;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
