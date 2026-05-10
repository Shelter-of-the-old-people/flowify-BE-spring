package org.github.flowify.workflow.state.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.execution.dto.NodeStateUpdateRequest;
import org.github.flowify.workflow.state.entity.WorkflowNodeState;
import org.github.flowify.workflow.state.repository.WorkflowNodeStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowNodeStateService {

    private static final String ESCAPED_PERCENT = "%25";
    private static final String ESCAPED_DOT = "%2E";
    private static final String ESCAPED_DOLLAR = "%24";

    private final WorkflowNodeStateRepository workflowNodeStateRepository;

    public Map<String, Map<String, Object>> getStateMap(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return Map.of();
        }

        return workflowNodeStateRepository.findByWorkflowId(workflowId).stream()
                .filter(state -> state.getNodeId() != null && !state.getNodeId().isBlank())
                .collect(Collectors.toMap(
                        WorkflowNodeState::getNodeId,
                        state -> decodeStateMap(state.getState()),
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    public void applyUpdates(String workflowId, List<NodeStateUpdateRequest> updates) {
        if (workflowId == null || workflowId.isBlank() || updates == null || updates.isEmpty()) {
            return;
        }

        for (NodeStateUpdateRequest update : updates) {
            if (update == null || update.getNodeId() == null || update.getNodeId().isBlank()) {
                continue;
            }

            Map<String, Object> nextState = update.getState() != null
                    ? encodeStateMap(update.getState())
                    : new LinkedHashMap<>();

            Optional<WorkflowNodeState> existing = workflowNodeStateRepository
                    .findByWorkflowIdAndNodeId(workflowId, update.getNodeId());

            WorkflowNodeState entity = existing.orElseGet(WorkflowNodeState::new);
            entity.setWorkflowId(workflowId);
            entity.setNodeId(update.getNodeId());
            entity.setService(update.getService());
            entity.setState(nextState);
            workflowNodeStateRepository.save(entity);
        }
    }

    private Map<String, Object> encodeStateMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }

        LinkedHashMap<String, Object> encoded = new LinkedHashMap<>();
        source.forEach((key, value) -> encoded.put(encodeMapKey(key), encodeStateValue(value)));
        return encoded;
    }

    private Map<String, Object> decodeStateMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Object> decoded = new LinkedHashMap<>();
        source.forEach((key, value) -> decoded.put(decodeMapKey(key), decodeStateValue(value)));
        return Map.copyOf(decoded);
    }

    private Object encodeStateValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return encodeUntypedMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> encoded = new ArrayList<>(list.size());
            for (Object item : list) {
                encoded.add(encodeStateValue(item));
            }
            return encoded;
        }
        return value;
    }

    private Object decodeStateValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return decodeUntypedMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> decoded = new ArrayList<>(list.size());
            for (Object item : list) {
                decoded.add(decodeStateValue(item));
            }
            return decoded;
        }
        return value;
    }

    private Map<String, Object> encodeUntypedMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> encoded = new LinkedHashMap<>();
        source.forEach((key, value) -> encoded.put(encodeMapKey(String.valueOf(key)), encodeStateValue(value)));
        return encoded;
    }

    private Map<String, Object> decodeUntypedMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> decoded = new LinkedHashMap<>();
        source.forEach((key, value) -> decoded.put(decodeMapKey(String.valueOf(key)), decodeStateValue(value)));
        return decoded;
    }

    private String encodeMapKey(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        return key
                .replace("%", ESCAPED_PERCENT)
                .replace(".", ESCAPED_DOT)
                .replace("$", ESCAPED_DOLLAR);
    }

    private String decodeMapKey(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        return key
                .replace(ESCAPED_DOLLAR, "$")
                .replace(ESCAPED_DOT, ".")
                .replace(ESCAPED_PERCENT, "%");
    }
}
