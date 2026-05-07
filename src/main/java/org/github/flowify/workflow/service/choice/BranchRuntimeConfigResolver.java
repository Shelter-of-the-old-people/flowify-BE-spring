package org.github.flowify.workflow.service.choice;

import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BranchRuntimeConfigResolver {

    private static final String CONDITION_BRANCH = "CONDITION_BRANCH";
    private static final String BRANCH_TYPE_FILE_TYPE = "file_type";
    private static final String FALLBACK_KEY = "other";
    private static final Set<String> FILE_TYPE_ACTION_IDS = Set.of(
            "branch_by_file_type",
            "classify_by_type"
    );
    private static final List<FileTypeBranchRule> FILE_TYPE_RULES = List.of(
            new FileTypeBranchRule("pdf", "PDF",
                    List.of("pdf"),
                    List.of("application/pdf"),
                    List.of()),
            new FileTypeBranchRule("image", "이미지",
                    List.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"),
                    List.of(),
                    List.of("image/")),
            new FileTypeBranchRule("spreadsheet", "스프레드시트",
                    List.of("xls", "xlsx", "csv"),
                    List.of(
                            "application/vnd.google-apps.spreadsheet",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "text/csv"),
                    List.of()),
            new FileTypeBranchRule("document", "문서",
                    List.of("doc", "docx", "txt", "md", "hwp"),
                    List.of(
                            "application/vnd.google-apps.document",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain",
                            "text/markdown"),
                    List.of()),
            new FileTypeBranchRule("presentation", "프레젠테이션",
                    List.of("ppt", "pptx"),
                    List.of(
                            "application/vnd.google-apps.presentation",
                            "application/vnd.ms-powerpoint",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                    List.of())
    );

    public Map<String, Object> resolve(NodeDefinition node, String semanticNodeType) {
        if (node == null || !CONDITION_BRANCH.equalsIgnoreCase(asText(semanticNodeType))) {
            return Map.of();
        }

        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String choiceActionId = firstText(
                config.get("choiceActionId"),
                config.get("choice_action_id"),
                config.get("actionId"),
                config.get("action_id")
        );
        if (!FILE_TYPE_ACTION_IDS.contains(choiceActionId)) {
            return Map.of();
        }

        Set<String> selectedKeys = resolveSelectedBranchKeys(config, choiceActionId);
        List<Map<String, Object>> branchRules = FILE_TYPE_RULES.stream()
                .filter(rule -> selectedKeys.isEmpty() || selectedKeys.contains(rule.key()))
                .map(this::toRuntimeRule)
                .toList();

        Map<String, Object> runtimeConfig = new LinkedHashMap<>();
        runtimeConfig.put("branch_type", BRANCH_TYPE_FILE_TYPE);
        runtimeConfig.put("branch_rules", branchRules);
        runtimeConfig.put("fallback_branch", Map.of(
                "key", FALLBACK_KEY,
                "label", "기타"
        ));
        return runtimeConfig;
    }

    private Map<String, Object> toRuntimeRule(FileTypeBranchRule rule) {
        Map<String, Object> matcher = new LinkedHashMap<>();
        matcher.put("type", BRANCH_TYPE_FILE_TYPE);
        matcher.put("extensions", rule.extensions());
        matcher.put("mime_types", rule.mimeTypes());
        matcher.put("mime_prefixes", rule.mimePrefixes());

        Map<String, Object> runtimeRule = new LinkedHashMap<>();
        runtimeRule.put("key", rule.key());
        runtimeRule.put("label", rule.label());
        runtimeRule.put("matcher", matcher);
        return runtimeRule;
    }

    private Set<String> resolveSelectedBranchKeys(Map<String, Object> config, String choiceActionId) {
        Set<String> selectedKeys = new LinkedHashSet<>();
        appendSelection(selectedKeys, config.get("branchKeys"));
        appendSelection(selectedKeys, config.get("branch_keys"));
        appendSelection(selectedKeys, config.get("selectedBranches"));
        appendSelection(selectedKeys, config.get("selected_branches"));
        appendSelection(selectedKeys, config.get("branchTypes"));
        appendSelection(selectedKeys, config.get("branch_types"));

        Object choiceSelections = config.get("choiceSelections");
        if (choiceSelections instanceof Map<?, ?> selections) {
            appendSelection(selectedKeys, selections.get(choiceActionId));
            appendSelection(selectedKeys, selections.get("branch_by_file_type"));
            appendSelection(selectedKeys, selections.get("classify_by_type"));
            appendSelection(selectedKeys, selections.get("file_type"));
            appendSelection(selectedKeys, selections.get("branches"));
        }

        selectedKeys.remove(FALLBACK_KEY);
        return selectedKeys;
    }

    private void appendSelection(Set<String> selectedKeys, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                appendSelection(selectedKeys, item);
            }
            return;
        }
        if (value.getClass().isArray()) {
            Arrays.stream((Object[]) value).forEach(item -> appendSelection(selectedKeys, item));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    addIfFileTypeBranch(selectedKeys, entry.getKey());
                } else {
                    appendSelection(selectedKeys, entry.getValue());
                }
            }
            return;
        }

        addIfFileTypeBranch(selectedKeys, value);
    }

    private void addIfFileTypeBranch(Set<String> selectedKeys, Object value) {
        String key = asText(value);
        if (hasText(key) && isKnownFileTypeBranch(key)) {
            selectedKeys.add(key);
        }
    }

    private boolean isKnownFileTypeBranch(String key) {
        if (FALLBACK_KEY.equals(key)) {
            return true;
        }
        return FILE_TYPE_RULES.stream().anyMatch(rule -> rule.key().equals(key));
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

    private record FileTypeBranchRule(
            String key,
            String label,
            List<String> extensions,
            List<String> mimeTypes,
            List<String> mimePrefixes
    ) {
    }
}
