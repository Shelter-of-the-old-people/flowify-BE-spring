package org.github.flowify.execution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.choice.BranchRuntimeConfigResolver;
import org.github.flowify.workflow.service.choice.ChoiceNodeTypeResolver;
import org.github.flowify.workflow.service.choice.ChoicePromptResolver;
import org.github.flowify.workflow.state.service.WorkflowNodeStateService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTranslator {

    private static final Set<String> LOOP_TYPES = Set.of("LOOP");
    private static final Set<String> BRANCH_TYPES = Set.of("CONDITION_BRANCH");
    private static final Set<String> CONTENT_EXTRACTOR_TYPES = Set.of("CONTENT_EXTRACTOR");
    private static final Set<String> LLM_TYPES = Set.of("AI", "DATA_FILTER", "AI_FILTER");
    private static final Set<String> PROMPT_NODE_TYPES = Set.of("AI", "AI_FILTER");
    private static final Set<String> CONTENT_ACTIONS = Set.of(
            "summarize",
            "extract_info",
            "translate",
            "classify_by_content",
            "describe_image",
            "ocr",
            "ai_summarize",
            "ai_analyze"
    );
    private static final Set<String> CONTENT_CARRIER_TYPES = Set.of(
            "SINGLE_FILE",
            "FILE_LIST",
            "SINGLE_EMAIL",
            "EMAIL_LIST"
    );
    private static final Set<String> GENERATED_OUTPUT_TYPES = Set.of("TEXT", "SPREADSHEET_DATA");

    private final ChoicePromptResolver choicePromptResolver;
    private final ChoiceNodeTypeResolver choiceNodeTypeResolver;
    private final BranchRuntimeConfigResolver branchRuntimeConfigResolver;
    private final WorkflowNodeStateService workflowNodeStateService;

    public Map<String, Object> toRuntimeModel(Workflow workflow) {
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("id", workflow.getId());
        runtime.put("name", workflow.getName());
        runtime.put("userId", workflow.getUserId());

        Map<String, Map<String, Object>> nodeStateMap = workflowNodeStateService.getStateMap(workflow.getId());

        List<Map<String, Object>> runtimeNodes = new ArrayList<>();
        for (NodeDefinition node : workflow.getNodes()) {
            runtimeNodes.add(translateNode(node, nodeStateMap.get(node.getId())));
        }
        runtime.put("nodes", runtimeNodes);

        List<Map<String, Object>> runtimeEdges = new ArrayList<>();
        for (EdgeDefinition edge : workflow.getEdges()) {
            Map<String, Object> e = new HashMap<>();
            e.put("id", edge.getId());
            e.put("source", edge.getSource());
            e.put("target", edge.getTarget());
            putIfHasText(e, "label", edge.getLabel());
            putIfHasText(e, "sourceHandle", edge.getSourceHandle());
            putIfHasText(e, "targetHandle", edge.getTargetHandle());
            runtimeEdges.add(e);
        }
        runtime.put("edges", runtimeEdges);

        TriggerConfig normalizedTrigger = WorkflowTriggerSupport.normalizeTrigger(workflow.getTrigger());
        if (normalizedTrigger != null) {
            Map<String, Object> trigger = new HashMap<>();
            trigger.put("type", normalizedTrigger.getType());
            trigger.put("config", normalizedTrigger.getConfig());
            runtime.put("trigger", trigger);
        }

        log.debug("Workflow translated to runtime model: {} nodes, {} edges",
                runtimeNodes.size(), runtimeEdges.size());
        return runtime;
    }

    private Map<String, Object> translateNode(NodeDefinition node, Map<String, Object> nodeState) {
        Map<String, Object> runtime = new HashMap<>();

        runtime.put("id", node.getId());
        runtime.put("category", node.getCategory());
        runtime.put("type", node.getType());
        runtime.put("label", node.getLabel());
        runtime.put("config", node.getConfig());
        runtime.put("dataType", node.getDataType());
        runtime.put("outputDataType", node.getOutputDataType());
        runtime.put("role", node.getRole());

        String semanticNodeType = choiceNodeTypeResolver.resolve(node);
        String runtimeType = resolveRuntimeType(node, semanticNodeType);
        runtime.put("runtime_type", runtimeType);

        if ("input".equals(runtimeType)) {
            Map<String, Object> source = new HashMap<>();
            source.put("service", nullSafe(node.getType()));
            source.put("canonical_input_type", nullSafe(node.getOutputDataType()));
            if (node.getConfig() != null) {
                source.put("mode", node.getConfig().getOrDefault("source_mode", ""));
                source.put("target", resolveSourceTarget(node));
                source.put("config", node.getConfig());
            } else {
                source.put("mode", "");
                source.put("target", "");
                source.put("config", Map.of());
            }
            if (nodeState != null && !nodeState.isEmpty()) {
                source.put("state", nodeState);
            }
            runtime.put("runtime_source", source);
        }

        if ("output".equals(runtimeType)) {
            Map<String, Object> sink = new HashMap<>();
            sink.put("service", nullSafe(node.getType()));
            sink.put("config", node.getConfig() != null ? node.getConfig() : Map.of());
            runtime.put("runtime_sink", sink);
        }

        if ("integration".equals(runtimeType)) {
            Map<String, Object> action = new HashMap<>();
            action.put("service", resolveIntegrationService(node));
            action.put("action", node.getConfig() != null ? node.getConfig().getOrDefault("action", "") : "");
            action.put("config", node.getConfig() != null ? node.getConfig() : Map.of());
            if (nodeState != null && !nodeState.isEmpty()) {
                action.put("state", nodeState);
            }
            runtime.put("runtime_action", action);
        }

        if ("llm".equals(runtimeType) || "loop".equals(runtimeType) || "if_else".equals(runtimeType)
                || "content_extractor".equals(runtimeType)) {
            Map<String, Object> runtimeConfig = new HashMap<>();
            if (node.getConfig() != null) {
                runtimeConfig.putAll(node.getConfig());
            }

            if (!"content_extractor".equals(runtimeType)) {
                Map<String, Object> resolvedPromptConfig = choicePromptResolver.resolve(node, semanticNodeType);
                if (resolvedPromptConfig != null) {
                    runtimeConfig.putAll(resolvedPromptConfig);
                }

                Map<String, Object> resolvedBranchConfig = branchRuntimeConfigResolver.resolve(node, semanticNodeType);
                if (resolvedBranchConfig != null) {
                    runtimeConfig.putAll(resolvedBranchConfig);
                }
            }

            runtimeConfig.put("node_type", nullSafe(semanticNodeType));
            runtimeConfig.put("output_data_type", nullSafe(node.getOutputDataType()));
            if ("content_extractor".equals(runtimeType)) {
                runtimeConfig.put("action", firstText(
                        runtimeConfig.get("action"),
                        configValue(node, "choiceActionId"),
                        configValue(node, "choice_action_id"),
                        "extract_text"
                ));
                runtimeConfig.put("requires_content", true);
            } else {
                runtimeConfig.put("requires_content", requiresContent(node, semanticNodeType, runtimeConfig));
            }
            runtime.put("runtime_config", runtimeConfig);
        }

        return runtime;
    }

    private boolean requiresContent(NodeDefinition node, String semanticNodeType, Map<String, Object> runtimeConfig) {
        Object explicit = firstPresent(
                configValue(node, "requires_content"),
                configValue(node, "requiresContent"),
                runtimeConfig.get("requires_content"),
                runtimeConfig.get("requiresContent")
        );
        if (explicit != null) {
            return Boolean.parseBoolean(String.valueOf(explicit));
        }

        String choiceActionId = firstText(
                configValue(node, "choiceActionId"),
                configValue(node, "choice_action_id")
        );
        if (CONTENT_ACTIONS.contains(choiceActionId)) {
            return true;
        }

        String action = firstText(configValue(node, "action"), runtimeConfig.get("action"));
        if (!"process".equals(action) && CONTENT_ACTIONS.contains(action)) {
            return true;
        }

        if (containsContentActionKey(configValue(node, "choiceSelections"))) {
            return true;
        }

        String dataType = nullSafe(node.getDataType());
        String outputDataType = nullSafe(node.getOutputDataType());
        String upperSemanticType = semanticNodeType != null ? semanticNodeType.toUpperCase() : "";
        return PROMPT_NODE_TYPES.contains(upperSemanticType)
                && CONTENT_CARRIER_TYPES.contains(dataType)
                && GENERATED_OUTPUT_TYPES.contains(outputDataType);
    }

    private Object configValue(NodeDefinition node, String key) {
        if (node == null || node.getConfig() == null) {
            return null;
        }
        return node.getConfig().get(key);
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = value != null ? String.valueOf(value).trim() : "";
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private boolean containsContentActionKey(Object choiceSelectionsValue) {
        if (!(choiceSelectionsValue instanceof Map<?, ?> choiceSelections)) {
            return false;
        }
        return choiceSelections.keySet().stream()
                .map(this::firstText)
                .anyMatch(CONTENT_ACTIONS::contains);
    }

    private String resolveRuntimeType(NodeDefinition node, String semanticNodeType) {
        if ("start".equals(node.getRole())) {
            return "input";
        }
        if ("end".equals(node.getRole())) {
            return "output";
        }

        String upperType = semanticNodeType != null ? semanticNodeType.toUpperCase() : "";
        if (LOOP_TYPES.contains(upperType)) {
            return "loop";
        }
        if (BRANCH_TYPES.contains(upperType)) {
            return "if_else";
        }
        if (CONTENT_EXTRACTOR_TYPES.contains(upperType)) {
            return "content_extractor";
        }
        if (isGoogleSheetsIntegrationNode(node)) {
            return "integration";
        }
        if (LLM_TYPES.contains(upperType)) {
            return "llm";
        }

        return "llm";
    }

    private Object resolveSourceTarget(NodeDefinition node) {
        if (node.getConfig() == null) {
            return "";
        }

        if ("google_sheets".equals(node.getType())) {
            Object spreadsheetId = node.getConfig().get("spreadsheet_id");
            if (spreadsheetId instanceof String value && !value.isBlank()) {
                return value;
            }
        }

        return node.getConfig().getOrDefault("target", "");
    }

    private boolean isGoogleSheetsIntegrationNode(NodeDefinition node) {
        if (!"middle".equals(node.getRole())) {
            return false;
        }
        if ("google_sheets".equals(node.getType())) {
            return true;
        }
        if (node.getConfig() == null) {
            return false;
        }
        Object service = node.getConfig().get("service");
        return service instanceof String value && "google_sheets".equals(value);
    }

    private String resolveIntegrationService(NodeDefinition node) {
        if (node.getConfig() != null) {
            Object service = node.getConfig().get("service");
            if (service instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return nullSafe(node.getType());
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
