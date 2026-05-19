package org.github.flowify.workflow.service.generation;

import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.catalog.service.picker.WebFeedSourceRegistry;
import org.github.flowify.catalog.service.picker.WebFeedSourceRegistry.WebFeedSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WorkflowGenerationConfigPolicy {

    private static final Pattern GITHUB_REPOSITORY_URL_PATTERN =
            Pattern.compile("^https?://(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/.*)?$");
    private static final Pattern EMAIL_ADDRESS_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern HTTPS_URL_PATTERN =
            Pattern.compile("https://[^\\s\"'<>]+");
    private static final Set<String> SOURCE_PICKER_SCHEMA_TYPES = Set.of(
            "file_picker",
            "folder_picker",
            "label_picker",
            "sheet_picker",
            "course_picker",
            "term_picker",
            "category_picker",
            "page_picker",
            "email_picker",
            "feed_source_picker"
    );
    private static final Set<String> SOURCE_TARGET_SUMMARY_FIELDS = Set.of(
            "target_label",
            "target_meta",
            "targets"
    );
    private static final Set<String> SINK_REMOTE_FIELD_TYPES = Set.of(
            "folder_picker",
            "sheet_picker",
            "page_picker",
            "calendar_picker",
            "channel_picker",
            "secret_text",
            "email_input"
    );
    private static final Set<String> SINK_REMOTE_FIELD_KEYS = Set.of(
            "webhook_url",
            "to",
            "recipient",
            "channel",
            "channel_id",
            "folder_id",
            "spreadsheet_id",
            "target_id",
            "calendar_id",
            "page_id",
            "database_id"
    );
    private static final Set<String> SINK_AI_WRITABLE_FIELD_TYPES = Set.of(
            "select",
            "textarea",
            "text",
            "number"
    );
    private static final Set<String> GOOGLE_SHEETS_DEPENDENT_SINK_FIELDS = Set.of(
            "sheet_name",
            "range_a1",
            "key_column"
    );
    private static final String CURRENT_USER_EMAIL_RECIPIENT_SOURCE = "current_user_email";
    private static final String TARGET_PRESET_IDS_FIELD = "target_preset_ids";
    private static final String CUSTOM_TARGET_URLS_FIELD = "custom_target_urls";

    private WorkflowGenerationConfigPolicy() {
    }

    static List<Map<String, Object>> buildSourceConfigPolicies(CatalogService catalogService) {
        return buildSourceConfigPolicies(catalogService, null);
    }

    static List<Map<String, Object>> buildSourceConfigPolicies(
            CatalogService catalogService,
            WebFeedSourceRegistry webFeedSourceRegistry
    ) {
        return catalogService.getSourceCatalog().getServices().stream()
                .filter(service -> WorkflowGenerationSupport.SUPPORTED_SOURCE_MODES.containsKey(service.getKey()))
                .flatMap(service -> service.getSourceModes().stream()
                        .filter(mode -> WorkflowGenerationSupport.SUPPORTED_SOURCE_MODES
                                .get(service.getKey())
                                .contains(mode.getKey()))
                        .map(mode -> toSourceConfigPolicy(service, mode, webFeedSourceRegistry)))
                .toList();
    }

    static List<Map<String, Object>> buildSinkConfigPolicies(CatalogService catalogService) {
        return catalogService.getSinkCatalog().getServices().stream()
                .filter(service -> WorkflowGenerationSupport.SUPPORTED_SINKS.contains(service.getKey()))
                .map(WorkflowGenerationConfigPolicy::toSinkConfigPolicy)
                .toList();
    }

    static void sanitizeStartNodeConfig(CatalogService catalogService, String serviceKey, Map<String, Object> config) {
        sanitizeStartNodeConfig(catalogService, null, serviceKey, config, "");
    }

    static void sanitizeStartNodeConfig(
            CatalogService catalogService,
            WebFeedSourceRegistry webFeedSourceRegistry,
            String serviceKey,
            Map<String, Object> config,
            String prompt
    ) {
        if (config == null) {
            return;
        }

        String sourceModeKey = textOrNull(config.get("source_mode"));
        if (sourceModeKey == null) {
            config.put("service", serviceKey);
            config.put("isConfigured", false);
            return;
        }

        SourceMode sourceMode = catalogService.findSourceMode(serviceKey, sourceModeKey);
        Map<String, Object> targetSchema = sourceMode != null && sourceMode.getTargetSchema() != null
                ? sourceMode.getTargetSchema()
                : Map.of();
        Set<String> writableFields = sourceWritableFields(serviceKey, sourceModeKey, targetSchema);

        config.keySet().removeIf(key -> !writableFields.contains(key));
        applySourceTargetValuePolicy(serviceKey, sourceModeKey, config, webFeedSourceRegistry, prompt);
        config.put("service", serviceKey);
        config.put("source_mode", sourceModeKey);
        config.put("isConfigured", !hasMissingRequiredStartConfig(serviceKey, sourceModeKey, targetSchema, config));
    }

    static void sanitizeEndNodeConfig(CatalogService catalogService, String serviceKey, Map<String, Object> config) {
        if (config == null) {
            return;
        }

        SinkService sinkService = catalogService.findSinkService(serviceKey);
        List<Map<String, Object>> fields = configFields(sinkService);
        Set<String> writableFields = sinkWritableFields(serviceKey, fields);

        config.keySet().removeIf(key -> !writableFields.contains(key));
        removeInvalidSelectValues(config, fields);
        applySinkFieldValuePolicies(serviceKey, config);
        config.put("service", serviceKey);
        config.put("isConfigured", !hasMissingRequiredEndConfig(serviceKey, fields, config));
    }

    private static Map<String, Object> toSourceConfigPolicy(
            SourceService service,
            SourceMode mode,
            WebFeedSourceRegistry webFeedSourceRegistry
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        Map<String, Object> targetSchema = mode.getTargetSchema() != null ? mode.getTargetSchema() : Map.of();
        row.put("service", service.getKey());
        row.put("serviceLabel", service.getLabel());
        row.put("sourceMode", mode.getKey());
        row.put("sourceModeLabel", mode.getLabel());
        row.put("targetSchemaType", textOrNull(targetSchema.get("type")));
        String targetValuePolicy = WorkflowGenerationSupport.sourceTargetValuePolicy(service.getKey(), mode.getKey());
        if (targetValuePolicy != null) {
            row.put("targetValuePolicy", targetValuePolicy);
        }
        row.put("aiWritableFields", new ArrayList<>(sourceWritableFields(service.getKey(), mode.getKey(), targetSchema)));
        row.put("aiForbiddenFields", new ArrayList<>(sourceForbiddenFields(targetSchema)));
        row.put("requiredConfigFields", sourceRequiredFieldsForGenerationContext(
                service.getKey(),
                mode.getKey(),
                targetSchema
        ));
        if (isFeedSourcePolicy(service.getKey(), mode.getKey())) {
            row.put("requiredAnyConfigFields", List.of(TARGET_PRESET_IDS_FIELD, CUSTOM_TARGET_URLS_FIELD));
            row.put("presetTargets", feedSourcePresetTargets(webFeedSourceRegistry));
        }
        return row;
    }

    private static Map<String, Object> toSinkConfigPolicy(SinkService service) {
        List<Map<String, Object>> fields = configFields(service);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("service", service.getKey());
        row.put("serviceLabel", service.getLabel());
        row.put("aiWritableFields", new ArrayList<>(sinkWritableFields(service.getKey(), fields)));
        row.put("aiForbiddenFields", new ArrayList<>(sinkForbiddenFields(service.getKey(), fields)));
        row.put("requiredConfigFields", sinkRequiredFields(service.getKey(), fields));
        Map<String, String> fieldValuePolicies = sinkFieldValuePolicies(service.getKey(), fields);
        if (!fieldValuePolicies.isEmpty()) {
            row.put("fieldValuePolicies", fieldValuePolicies);
        }
        return row;
    }

    private static Set<String> sourceWritableFields(
            String serviceKey,
            String sourceModeKey,
            Map<String, Object> targetSchema
    ) {
        Set<String> fields = new LinkedHashSet<>(List.of("service", "source_mode", "isConfigured"));
        if (isFeedSourcePolicy(serviceKey, sourceModeKey)) {
            fields.add(TARGET_PRESET_IDS_FIELD);
            fields.add(CUSTOM_TARGET_URLS_FIELD);
        }
        String schemaType = textOrNull(targetSchema.get("type"));
        if (schemaType != null && !SOURCE_PICKER_SCHEMA_TYPES.contains(schemaType)) {
            fields.add("target");
        }
        if (Boolean.TRUE.equals(targetSchema.get("keyword_supported"))) {
            fields.add("keyword");
        }
        return fields;
    }

    private static List<String> sourceRequiredFieldsForGenerationContext(
            String serviceKey,
            String sourceModeKey,
            Map<String, Object> targetSchema
    ) {
        if (isFeedSourcePolicy(serviceKey, sourceModeKey)) {
            return List.of();
        }
        return sourceRequiredFields(serviceKey, sourceModeKey, targetSchema);
    }

    private static Set<String> sourceForbiddenFields(Map<String, Object> targetSchema) {
        Set<String> fields = new LinkedHashSet<>();
        String schemaType = textOrNull(targetSchema.get("type"));
        if (schemaType != null && SOURCE_PICKER_SCHEMA_TYPES.contains(schemaType)) {
            fields.add("target");
        }
        fields.addAll(SOURCE_TARGET_SUMMARY_FIELDS);
        return fields;
    }

    private static List<String> sourceRequiredFields(
            String serviceKey,
            String sourceModeKey,
            Map<String, Object> targetSchema
    ) {
        List<String> fields = new ArrayList<>();
        if (targetSchema != null && !targetSchema.isEmpty()) {
            String schemaType = textOrNull(targetSchema.get("type"));
            if (schemaType != null && SOURCE_PICKER_SCHEMA_TYPES.contains(schemaType)) {
                fields.add("google_sheets".equals(serviceKey) ? "spreadsheet_id" : "target");
            } else {
                fields.add("target");
            }
        }
        if ("google_sheets".equals(serviceKey)) {
            fields.add("sheet_name");
            if ("row_updated".equals(sourceModeKey)) {
                fields.add("key_column");
            }
        }
        return fields;
    }

    private static boolean hasMissingRequiredStartConfig(
            String serviceKey,
            String sourceModeKey,
            Map<String, Object> targetSchema,
            Map<String, Object> config
    ) {
        if (sourceRequiredFields(serviceKey, sourceModeKey, targetSchema).stream()
                .anyMatch(field -> isMissing(config.get(field)))) {
            return true;
        }
        return WorkflowGenerationSupport.TARGET_VALUE_POLICY_GITHUB_REPO.equals(
                WorkflowGenerationSupport.sourceTargetValuePolicy(serviceKey, sourceModeKey)
        ) && !NodeLifecycleService.isValidGitHubRepoTarget(config.get("target"));
    }

    private static void applySourceTargetValuePolicy(
            String serviceKey,
            String sourceModeKey,
            Map<String, Object> config,
            WebFeedSourceRegistry webFeedSourceRegistry,
            String prompt
    ) {
        String targetValuePolicy = WorkflowGenerationSupport.sourceTargetValuePolicy(serviceKey, sourceModeKey);
        if (WorkflowGenerationSupport.TARGET_VALUE_POLICY_FEED_SOURCE.equals(targetValuePolicy)) {
            applyFeedSourceValuePolicy(config, webFeedSourceRegistry, prompt);
            return;
        }
        if (!WorkflowGenerationSupport.TARGET_VALUE_POLICY_GITHUB_REPO.equals(targetValuePolicy)) {
            return;
        }

        String normalizedTarget = normalizeGithubRepositoryTarget(config.get("target"));
        if (normalizedTarget != null) {
            config.put("target", normalizedTarget);
        }
    }

    private static void applyFeedSourceValuePolicy(
            Map<String, Object> config,
            WebFeedSourceRegistry webFeedSourceRegistry,
            String prompt
    ) {
        List<FeedSourceSelection> selections = new ArrayList<>();
        Set<String> selectedUrls = new LinkedHashSet<>();

        if (webFeedSourceRegistry != null) {
            Map<String, WebFeedSource> sources = webFeedSourceRegistry.findByIds(
                    stringList(config.get(TARGET_PRESET_IDS_FIELD))
            );
            for (WebFeedSource source : sources.values()) {
                String url = normalizeHttpsUrl(source.url());
                if (url == null || !selectedUrls.add(url)) {
                    continue;
                }
                selections.add(new FeedSourceSelection(url, source.label(), toFeedSourceMeta(source), false));
            }
        }

        Set<String> promptUrls = extractPromptHttpsUrls(prompt);
        for (String rawUrl : stringList(config.get(CUSTOM_TARGET_URLS_FIELD))) {
            String url = normalizeHttpsUrl(rawUrl);
            if (url == null || !promptUrls.contains(url) || !selectedUrls.add(url)) {
                continue;
            }
            selections.add(new FeedSourceSelection(url, url, Map.of("label", url, "url", url), true));
        }

        config.remove(TARGET_PRESET_IDS_FIELD);
        config.remove(CUSTOM_TARGET_URLS_FIELD);

        if (selections.isEmpty()) {
            config.remove("target");
            config.remove("targets");
            config.remove("target_label");
            config.remove("target_meta");
            return;
        }

        config.put("target", selections.getFirst().url());
        config.put("targets", selections.stream()
                .map(FeedSourceSelection::url)
                .toList());
        config.put("target_label", targetLabel(selections));
        config.put("target_meta", targetMeta(selections));
    }

    private static Map<String, Object> toFeedSourceMeta(WebFeedSource source) {
        Map<String, Object> meta = new LinkedHashMap<>();
        putIfPresent(meta, "category", source.category());
        putIfPresent(meta, "homepage", source.homepage());
        putIfPresent(meta, "label", source.label());
        putIfPresent(meta, "language", source.language());
        putIfPresent(meta, "presetId", source.id());
        putIfPresent(meta, "region", source.region());
        putIfPresent(meta, "sourceType", source.sourceType());
        meta.put("tags", source.tags() != null ? source.tags() : List.of());
        putIfPresent(meta, "url", source.url());
        return meta;
    }

    private static String targetLabel(List<FeedSourceSelection> selections) {
        String firstLabel = textOrNull(selections.getFirst().label());
        if (firstLabel == null) {
            firstLabel = selections.getFirst().url();
        }
        if (selections.size() == 1) {
            return firstLabel;
        }
        return firstLabel + " 외 " + (selections.size() - 1) + "개";
    }

    private static Map<String, Object> targetMeta(List<FeedSourceSelection> selections) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("pickerType", "feed_source");
        meta.put("selectedSources", selections.stream()
                .filter(selection -> !selection.custom())
                .map(FeedSourceSelection::meta)
                .toList());
        meta.put("customSources", selections.stream()
                .filter(FeedSourceSelection::custom)
                .map(FeedSourceSelection::meta)
                .toList());
        return meta;
    }

    private static List<Map<String, Object>> feedSourcePresetTargets(WebFeedSourceRegistry webFeedSourceRegistry) {
        if (webFeedSourceRegistry == null) {
            return List.of();
        }
        return webFeedSourceRegistry.all().stream()
                .map(WorkflowGenerationConfigPolicy::toFeedSourcePresetTarget)
                .toList();
    }

    private static Map<String, Object> toFeedSourcePresetTarget(WebFeedSource source) {
        Map<String, Object> target = new LinkedHashMap<>();
        putIfPresent(target, "id", source.id());
        putIfPresent(target, "label", source.label());
        putIfPresent(target, "description", source.description());
        putIfPresent(target, "category", source.category());
        putIfPresent(target, "language", source.language());
        putIfPresent(target, "region", source.region());
        putIfPresent(target, "sourceType", source.sourceType());
        target.put("tags", source.tags() != null ? source.tags() : List.of());
        return target;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            map.put(key, text);
        }
    }

    private static Set<String> extractPromptHttpsUrls(String prompt) {
        String text = textOrNull(prompt);
        if (text == null) {
            return Set.of();
        }
        Matcher matcher = HTTPS_URL_PATTERN.matcher(text);
        Set<String> urls = new LinkedHashSet<>();
        while (matcher.find()) {
            String url = normalizeHttpsUrl(matcher.group());
            if (url != null) {
                urls.add(url);
            }
        }
        return urls;
    }

    private static String normalizeHttpsUrl(Object value) {
        String url = textOrNull(value);
        if (url == null || !url.startsWith("https://")) {
            return null;
        }
        while (!url.isEmpty() && ".,;:)]}".indexOf(url.charAt(url.length() - 1)) >= 0) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isBlank() ? null : url;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(WorkflowGenerationConfigPolicy::textOrNull)
                    .filter(text -> text != null)
                    .toList();
        }
        String text = textOrNull(value);
        return text != null ? List.of(text) : Collections.emptyList();
    }

    private static boolean isFeedSourcePolicy(String serviceKey, String sourceModeKey) {
        return WorkflowGenerationSupport.TARGET_VALUE_POLICY_FEED_SOURCE.equals(
                WorkflowGenerationSupport.sourceTargetValuePolicy(serviceKey, sourceModeKey)
        );
    }

    private static String normalizeGithubRepositoryTarget(Object value) {
        String target = textOrNull(value);
        if (target == null) {
            return null;
        }
        if (NodeLifecycleService.isValidGitHubRepoTarget(target)) {
            return target;
        }

        Matcher matcher = GITHUB_REPOSITORY_URL_PATTERN.matcher(target);
        if (matcher.matches()) {
            return matcher.group(1) + "/" + matcher.group(2);
        }
        return null;
    }

    private static void applySinkFieldValuePolicies(String serviceKey, Map<String, Object> config) {
        List<String> keys = new ArrayList<>(config.keySet());
        for (String key : keys) {
            if (isExplicitEmailSinkField(serviceKey, key)) {
                String normalizedEmail = normalizeEmailAddress(config.get(key));
                if (normalizedEmail == null) {
                    config.remove(key);
                } else {
                    config.put(key, normalizedEmail);
                }
            } else if (isCurrentUserEmailSinkField(serviceKey, key)
                    && !CURRENT_USER_EMAIL_RECIPIENT_SOURCE.equals(textOrNull(config.get(key)))) {
                config.remove(key);
            }
        }

        if (!isMissing(config.get("to"))) {
            config.remove("to_source");
        }
    }

    private static String normalizeEmailAddress(Object value) {
        String email = textOrNull(value);
        if (email == null || !EMAIL_ADDRESS_PATTERN.matcher(email).matches()) {
            return null;
        }
        return email;
    }

    private static Set<String> sinkWritableFields(String serviceKey, List<Map<String, Object>> fields) {
        Set<String> writableFields = new LinkedHashSet<>(List.of("service", "isConfigured"));
        for (Map<String, Object> field : fields) {
            String key = textOrNull(field.get("key"));
            if (key != null && isAiWritableSinkField(serviceKey, field)) {
                writableFields.add(key);
            }
        }
        writableFields.addAll(nonCatalogSinkWritableFields(serviceKey));
        return writableFields;
    }

    private static Map<String, String> sinkFieldValuePolicies(String serviceKey, List<Map<String, Object>> fields) {
        Map<String, String> policies = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) {
            String key = textOrNull(field.get("key"));
            String policy = key != null ? WorkflowGenerationSupport.sinkFieldValuePolicy(serviceKey, key) : null;
            if (policy != null) {
                policies.put(key, policy);
            }
        }
        for (String key : nonCatalogSinkWritableFields(serviceKey)) {
            String policy = WorkflowGenerationSupport.sinkFieldValuePolicy(serviceKey, key);
            if (policy != null) {
                policies.put(key, policy);
            }
        }
        return policies;
    }

    private static Set<String> sinkForbiddenFields(String serviceKey, List<Map<String, Object>> fields) {
        Set<String> forbiddenFields = new LinkedHashSet<>();
        for (Map<String, Object> field : fields) {
            String key = textOrNull(field.get("key"));
            if (key == null || isAiWritableSinkField(serviceKey, field)) {
                continue;
            }
            forbiddenFields.add(key);
            forbiddenFields.add(key + "_label");
            forbiddenFields.add(key + "_meta");
        }
        return forbiddenFields;
    }

    private static boolean isAiWritableSinkField(String serviceKey, Map<String, Object> field) {
        String key = textOrNull(field.get("key"));
        String type = textOrNull(field.get("type"));
        if (key == null) {
            return false;
        }
        if ("google_sheets".equals(serviceKey) && GOOGLE_SHEETS_DEPENDENT_SINK_FIELDS.contains(key)) {
            return false;
        }
        if (isExplicitEmailSinkField(serviceKey, key)) {
            return true;
        }
        if (isCurrentUserEmailSinkField(serviceKey, key)) {
            return true;
        }
        if (SINK_REMOTE_FIELD_KEYS.contains(key)
                || key.endsWith("_id")
                || key.endsWith("_url")
                || SINK_REMOTE_FIELD_TYPES.contains(type)) {
            return false;
        }
        return type == null || SINK_AI_WRITABLE_FIELD_TYPES.contains(type);
    }

    private static boolean isExplicitEmailSinkField(String serviceKey, String fieldKey) {
        return WorkflowGenerationSupport.SINK_FIELD_VALUE_POLICY_EXPLICIT_EMAIL.equals(
                WorkflowGenerationSupport.sinkFieldValuePolicy(serviceKey, fieldKey)
        );
    }

    private static boolean isCurrentUserEmailSinkField(String serviceKey, String fieldKey) {
        return WorkflowGenerationSupport.SINK_FIELD_VALUE_POLICY_CURRENT_USER_EMAIL.equals(
                WorkflowGenerationSupport.sinkFieldValuePolicy(serviceKey, fieldKey)
        );
    }

    private static Set<String> nonCatalogSinkWritableFields(String serviceKey) {
        if (!"gmail".equals(serviceKey)) {
            return Set.of();
        }
        // TODO: Temporary AI generation bridge until the FE Gmail settings panel supports recipient source UX.
        return Set.of("to_source");
    }

    private static List<String> sinkRequiredFields(String serviceKey, List<Map<String, Object>> fields) {
        List<String> requiredFields = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            String key = textOrNull(field.get("key"));
            if (key != null && Boolean.TRUE.equals(field.get("required"))) {
                requiredFields.add(key);
            }
        }
        if ("google_sheets".equals(serviceKey) && !requiredFields.contains("sheet_name")) {
            requiredFields.add("sheet_name");
        }
        return requiredFields;
    }

    private static boolean hasMissingRequiredEndConfig(
            String serviceKey,
            List<Map<String, Object>> fields,
            Map<String, Object> config
    ) {
        if (sinkRequiredFields(serviceKey, fields).stream()
                .anyMatch(field -> isMissing(config.get(field))
                        && !isSatisfiedByCurrentUserEmailRecipient(serviceKey, field, config))) {
            return true;
        }
        String writeMode = textOrNull(config.get("write_mode"));
        return ("update_row_by_key".equals(writeMode) || "upsert_row_by_key".equals(writeMode))
                && isMissing(config.get("key_column"));
    }

    private static boolean isSatisfiedByCurrentUserEmailRecipient(
            String serviceKey,
            String field,
            Map<String, Object> config
    ) {
        return "gmail".equals(serviceKey)
                && "to".equals(field)
                && CURRENT_USER_EMAIL_RECIPIENT_SOURCE.equals(textOrNull(config.get("to_source")));
    }

    private static void removeInvalidSelectValues(Map<String, Object> config, List<Map<String, Object>> fields) {
        for (Map<String, Object> field : fields) {
            if (!"select".equals(textOrNull(field.get("type")))) {
                continue;
            }
            String key = textOrNull(field.get("key"));
            if (key == null || !config.containsKey(key)) {
                continue;
            }
            Object options = field.get("options");
            if (options instanceof Collection<?> values
                    && values.stream().noneMatch(option -> String.valueOf(option).equals(String.valueOf(config.get(key))))) {
                config.remove(key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> configFields(SinkService service) {
        if (service == null || service.getConfigSchema() == null) {
            return List.of();
        }
        Object fields = service.getConfigSchema().get("fields");
        if (!(fields instanceof List<?> rawFields)) {
            return List.of();
        }
        return rawFields.stream()
                .filter(Map.class::isInstance)
                .map(field -> (Map<String, Object>) field)
                .toList();
    }

    private static boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private static String textOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private record FeedSourceSelection(
            String url,
            String label,
            Map<String, Object> meta,
            boolean custom
    ) {
    }
}
