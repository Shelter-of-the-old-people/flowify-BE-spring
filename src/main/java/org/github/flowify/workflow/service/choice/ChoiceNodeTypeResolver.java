package org.github.flowify.workflow.service.choice;

import lombok.RequiredArgsConstructor;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChoiceNodeTypeResolver {

    private static final Set<String> CHOICE_NODE_TYPES = Set.of(
            "LOOP",
            "CONDITION_BRANCH",
            "AI",
            "DATA_FILTER",
            "AI_FILTER",
            "PASSTHROUGH"
    );

    private static final Map<String, String> VISUAL_NODE_TYPE_FALLBACKS = Map.of(
            "loop", "LOOP"
    );

    private final ChoiceMappingService choiceMappingService;

    public String resolve(NodeDefinition node) {
        if (node == null) {
            return "";
        }

        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();

        String configNodeType = normalizeChoiceNodeType(firstText(
                config.get("choiceNodeType"),
                config.get("choice_node_type"),
                config.get("mappingNodeType"),
                config.get("runtimeNodeType")
        ));
        if (hasText(configNodeType)) {
            return configNodeType;
        }

        String persistedNodeType = normalizeChoiceNodeType(node.getType());
        if (hasText(persistedNodeType)) {
            return persistedNodeType;
        }

        String inferredNodeType = inferFromChoiceAction(node, config);
        if (hasText(inferredNodeType)) {
            return inferredNodeType;
        }

        String visualFallback = VISUAL_NODE_TYPE_FALLBACKS.get(asText(node.getType()));
        if (hasText(visualFallback)) {
            return visualFallback;
        }

        return asText(node.getType());
    }

    private String inferFromChoiceAction(NodeDefinition node, Map<String, Object> config) {
        String choiceActionId = asText(config.get("choiceActionId"));
        String dataType = firstText(node.getDataType(), config.get("dataType"), config.get("data_type"));
        if (!hasText(choiceActionId) || !hasText(dataType)) {
            return "";
        }

        MappingRules mappingRules = choiceMappingService.getMappingRules();
        if (mappingRules == null || mappingRules.getDataTypes() == null) {
            return "";
        }

        DataTypeConfig dataTypeConfig = mappingRules.getDataTypes().get(dataType);
        if (dataTypeConfig == null || dataTypeConfig.getActions() == null) {
            return "";
        }

        return dataTypeConfig.getActions().stream()
                .filter(action -> choiceActionId.equals(action.getId()))
                .map(Action::getNodeType)
                .map(this::normalizeChoiceNodeType)
                .filter(this::hasText)
                .findFirst()
                .orElse("");
    }

    private String normalizeChoiceNodeType(String value) {
        String normalized = asText(value).toUpperCase();
        return CHOICE_NODE_TYPES.contains(normalized) ? normalized : "";
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = asText(value);
            if (hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String asText(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
