package org.github.flowify.workflow.service.generation;

import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Position;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class WorkflowGenerationSnapshotService {

    private static final String CONDITION_BRANCH_NODE_TYPE = "CONDITION_BRANCH";
    private static final String BRANCH_BY_FILE_TYPE_ACTION_ID = "branch_by_file_type";
    private static final String BRANCH_BY_FILENAME_ACTION_ID = "branch_by_filename";
    private static final String CLASSIFY_BY_CONTENT_ACTION_ID = "classify_by_content";
    private static final String CLASSIFY_BY_FIELD_ACTION_ID = "classify_by_field";
    private static final String SPLIT_EMAIL_PARTS_ACTION_ID = "split_email_parts";
    private static final String SPLIT_ANNOUNCEMENT_PARTS_ACTION_ID = "split_announcement_parts";
    private static final String CHOICE_SELECTIONS_KEY = "choiceSelections";
    private static final String BRANCH_CONFIG_KEY = "branch_config";
    private static final String FILENAME_RULES_KEY = "filenameRules";
    private static final String FIELD_VALUE_RULES_KEY = "fieldValueRules";
    private static final String BRANCHES_KEY = "branches";
    private static final String OTHER_BRANCH_KEY = "other";
    private static final Pattern FILENAME_BRANCH_KEY_PATTERN = Pattern.compile("filename_\\d+");
    private static final Pattern FIELD_VALUE_BRANCH_KEY_PATTERN = Pattern.compile("field_value_\\d+");
    private static final Set<String> FILE_TYPE_BRANCH_KEYS = Set.of(
            "pdf",
            "archive",
            "image",
            "spreadsheet",
            "document",
            "presentation",
            "other"
    );
    private static final Set<String> BODY_ATTACHMENT_BRANCH_KEYS = Set.of("body", "attachments");
    private static final Map<String, Set<String>> SUPPORTED_BRANCH_KEYS_BY_ACTION = Map.of(
            BRANCH_BY_FILE_TYPE_ACTION_ID, FILE_TYPE_BRANCH_KEYS,
            SPLIT_EMAIL_PARTS_ACTION_ID, BODY_ATTACHMENT_BRANCH_KEYS,
            SPLIT_ANNOUNCEMENT_PARTS_ACTION_ID, BODY_ATTACHMENT_BRANCH_KEYS
    );
    private static final Map<String, Set<String>> CONTENT_BRANCH_PRESETS_BY_DATA_TYPE = Map.of(
            "TEXT", Set.of("positive_negative", "important_ref", "important_check_ref"),
            "SINGLE_EMAIL", Set.of("important_ref", "important_inquiry_ref")
    );

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
        snapshot.put("configSummary", snapshotConfig(node));
        return snapshot;
    }

    private Map<String, Object> snapshotConfig(NodeDefinition node) {
        Map<String, Object> config = node.getConfig();
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
        appendFilenameBranchRules(safeConfig, node, config);
        appendFieldValueBranchRules(safeConfig, node, config);
        appendBranchChoiceSelections(safeConfig, node, config);
        return safeConfig;
    }

    private void appendBranchChoiceSelections(
            Map<String, Object> safeConfig,
            NodeDefinition node,
            Map<String, Object> config
    ) {
        String actionId = asText(config.get("choiceActionId"));
        if (!CONDITION_BRANCH_NODE_TYPE.equals(node.getType())) {
            return;
        }
        if (BRANCH_BY_FILENAME_ACTION_ID.equals(actionId)) {
            appendFilenameBranchChoiceSelections(safeConfig, config);
            return;
        }
        if (CLASSIFY_BY_CONTENT_ACTION_ID.equals(actionId)) {
            appendContentBranchChoiceSelections(safeConfig, node, config);
            return;
        }
        if (CLASSIFY_BY_FIELD_ACTION_ID.equals(actionId)) {
            appendFieldValueBranchChoiceSelections(safeConfig, config);
            return;
        }

        Set<String> supportedBranchKeys = SUPPORTED_BRANCH_KEYS_BY_ACTION.get(actionId);
        if (supportedBranchKeys == null) {
            return;
        }

        Object rawSelections = config.get(CHOICE_SELECTIONS_KEY);
        if (!(rawSelections instanceof Map<?, ?> selections)) {
            return;
        }

        LinkedHashSet<String> branchKeys = new LinkedHashSet<>();
        appendSelection(branchKeys, selections.get(BRANCH_CONFIG_KEY), supportedBranchKeys);
        appendSelection(branchKeys, selections.get(actionId), supportedBranchKeys);
        if (!branchKeys.isEmpty()) {
            safeConfig.put(CHOICE_SELECTIONS_KEY, Map.of(BRANCH_CONFIG_KEY, List.copyOf(branchKeys)));
        }
    }

    private void appendContentBranchChoiceSelections(
            Map<String, Object> safeConfig,
            NodeDefinition node,
            Map<String, Object> config
    ) {
        Set<String> supportedPresets = CONTENT_BRANCH_PRESETS_BY_DATA_TYPE.get(asText(node.getDataType()));
        if (supportedPresets == null) {
            return;
        }

        Object rawSelections = config.get(CHOICE_SELECTIONS_KEY);
        if (!(rawSelections instanceof Map<?, ?> selections)) {
            return;
        }

        LinkedHashSet<String> presets = new LinkedHashSet<>();
        appendSelection(presets, selections.get(BRANCH_CONFIG_KEY), supportedPresets);
        appendSelection(presets, selections.get(CLASSIFY_BY_CONTENT_ACTION_ID), supportedPresets);
        if (presets.size() == 1) {
            safeConfig.put(CHOICE_SELECTIONS_KEY, Map.of(BRANCH_CONFIG_KEY, List.copyOf(presets)));
        }
    }

    private void appendFilenameBranchRules(
            Map<String, Object> safeConfig,
            NodeDefinition node,
            Map<String, Object> config
    ) {
        String actionId = asText(config.get("choiceActionId"));
        if (!CONDITION_BRANCH_NODE_TYPE.equals(node.getType())
                || !BRANCH_BY_FILENAME_ACTION_ID.equals(actionId)) {
            return;
        }

        List<Map<String, Object>> rules = sanitizeFilenameRules(config.get(FILENAME_RULES_KEY));
        if (rules.isEmpty()) {
            rules = sanitizeFilenameRules(config.get("filename_rules"));
        }
        if (!rules.isEmpty()) {
            safeConfig.put(FILENAME_RULES_KEY, rules);
        }
    }

    private void appendFieldValueBranchRules(
            Map<String, Object> safeConfig,
            NodeDefinition node,
            Map<String, Object> config
    ) {
        String actionId = asText(config.get("choiceActionId"));
        if (!CONDITION_BRANCH_NODE_TYPE.equals(node.getType())
                || !CLASSIFY_BY_FIELD_ACTION_ID.equals(actionId)) {
            return;
        }

        List<Map<String, Object>> rules = sanitizeFieldValueRules(config.get(FIELD_VALUE_RULES_KEY));
        if (rules.isEmpty()) {
            rules = sanitizeFieldValueRules(config.get("field_value_rules"));
        }
        if (!rules.isEmpty()) {
            safeConfig.put(FIELD_VALUE_RULES_KEY, rules);
        }
    }

    private void appendFilenameBranchChoiceSelections(
            Map<String, Object> safeConfig,
            Map<String, Object> config
    ) {
        Object rawRules = safeConfig.get(FILENAME_RULES_KEY);
        if (!(rawRules instanceof List<?> rules) || rules.isEmpty()) {
            return;
        }

        LinkedHashSet<String> supportedBranchKeys = new LinkedHashSet<>();
        for (Object rule : rules) {
            if (rule instanceof Map<?, ?> ruleMap) {
                String key = asText(ruleMap.get("key"));
                if (key != null && isFilenameBranchKey(key)) {
                    supportedBranchKeys.add(key);
                }
            }
        }
        if (supportedBranchKeys.isEmpty()) {
            return;
        }
        supportedBranchKeys.add(OTHER_BRANCH_KEY);

        Object rawSelections = config.get(CHOICE_SELECTIONS_KEY);
        if (!(rawSelections instanceof Map<?, ?> selections)) {
            return;
        }

        LinkedHashSet<String> branchKeys = new LinkedHashSet<>();
        appendSelection(branchKeys, selections.get(BRANCH_CONFIG_KEY), supportedBranchKeys);
        appendSelection(branchKeys, selections.get(BRANCH_BY_FILENAME_ACTION_ID), supportedBranchKeys);
        appendSelection(branchKeys, selections.get(BRANCHES_KEY), supportedBranchKeys);
        if (!branchKeys.isEmpty()) {
            safeConfig.put(CHOICE_SELECTIONS_KEY, Map.of(BRANCH_CONFIG_KEY, List.copyOf(branchKeys)));
        }
    }

    private void appendFieldValueBranchChoiceSelections(
            Map<String, Object> safeConfig,
            Map<String, Object> config
    ) {
        Object rawRules = safeConfig.get(FIELD_VALUE_RULES_KEY);
        if (!(rawRules instanceof List<?> rules) || rules.isEmpty()) {
            return;
        }

        LinkedHashSet<String> supportedBranchKeys = new LinkedHashSet<>();
        for (Object rule : rules) {
            if (rule instanceof Map<?, ?> ruleMap) {
                String key = asText(ruleMap.get("key"));
                if (key != null && isFieldValueBranchKey(key)) {
                    supportedBranchKeys.add(key);
                }
            }
        }
        if (supportedBranchKeys.isEmpty()) {
            return;
        }
        supportedBranchKeys.add(OTHER_BRANCH_KEY);

        Object rawSelections = config.get(CHOICE_SELECTIONS_KEY);
        if (!(rawSelections instanceof Map<?, ?> selections)) {
            return;
        }

        LinkedHashSet<String> branchKeys = new LinkedHashSet<>();
        appendSelection(branchKeys, selections.get(BRANCH_CONFIG_KEY), supportedBranchKeys);
        appendSelection(branchKeys, selections.get(CLASSIFY_BY_FIELD_ACTION_ID), supportedBranchKeys);
        appendSelection(branchKeys, selections.get(BRANCHES_KEY), supportedBranchKeys);
        if (!branchKeys.isEmpty()) {
            safeConfig.put(CHOICE_SELECTIONS_KEY, Map.of(BRANCH_CONFIG_KEY, List.copyOf(branchKeys)));
        }
    }

    private List<Map<String, Object>> sanitizeFilenameRules(Object value) {
        if (value == null) {
            return List.of();
        }

        List<Map<String, Object>> rules = new java.util.ArrayList<>();
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> appendFilenameRule(rules, item));
            return List.copyOf(rules);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                appendFilenameRule(rules, Array.get(value, index));
            }
        }
        return List.copyOf(rules);
    }

    private List<Map<String, Object>> sanitizeFieldValueRules(Object value) {
        if (value == null) {
            return List.of();
        }

        List<Map<String, Object>> rules = new java.util.ArrayList<>();
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> appendFieldValueRule(rules, item));
            return List.copyOf(rules);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                appendFieldValueRule(rules, Array.get(value, index));
            }
        }
        return List.copyOf(rules);
    }

    private void appendFilenameRule(List<Map<String, Object>> rules, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }

        String key = asText(map.get("key"));
        if (key == null) {
            key = asText(map.get("id"));
        }
        if (key == null || !isFilenameBranchKey(key)) {
            return;
        }
        String ruleKey = key;
        if (rules.stream().anyMatch(rule -> ruleKey.equals(rule.get("key")))) {
            return;
        }

        String label = asText(map.get("label"));
        if (label == null) {
            label = ruleKey;
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        appendTextValues(keywords, map.get("keywords"));
        if (keywords.isEmpty()) {
            return;
        }

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("key", ruleKey);
        rule.put("label", label);
        rule.put("keywords", List.copyOf(keywords));
        rules.add(rule);
    }

    private void appendFieldValueRule(List<Map<String, Object>> rules, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }

        String key = asText(map.get("key"));
        if (key == null) {
            key = asText(map.get("id"));
        }
        if (key == null || !isFieldValueBranchKey(key)) {
            return;
        }
        String ruleKey = key;
        if (rules.stream().anyMatch(rule -> ruleKey.equals(rule.get("key")))) {
            return;
        }

        String field = firstText(map.get("field"), map.get("column"));
        String branchValue = firstText(map.get("value"), map.get("equals"));
        String label = firstText(map.get("label"), branchValue);
        if (field == null || branchValue == null || label == null) {
            return;
        }

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("key", ruleKey);
        rule.put("label", label);
        rule.put("field", field);
        rule.put("value", branchValue);
        rules.add(rule);
    }

    private void appendTextValues(Set<String> target, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> appendTextValues(target, item));
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                appendTextValues(target, Array.get(value, index));
            }
            return;
        }

        String text = asText(value);
        if (text != null) {
            target.add(text);
        }
    }

    private boolean isFilenameBranchKey(String value) {
        return FILENAME_BRANCH_KEY_PATTERN.matcher(value).matches();
    }

    private boolean isFieldValueBranchKey(String value) {
        return FIELD_VALUE_BRANCH_KEY_PATTERN.matcher(value).matches();
    }

    private void appendSelection(Set<String> target, Object value, Set<String> supportedBranchKeys) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> appendSelection(target, item, supportedBranchKeys));
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                appendSelection(target, Array.get(value, index), supportedBranchKeys);
            }
            return;
        }

        String text = asText(value);
        if (text != null && supportedBranchKeys.contains(text)) {
            target.add(text);
        }
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = asText(value);
            if (text != null) {
                return text;
            }
        }
        return null;
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
