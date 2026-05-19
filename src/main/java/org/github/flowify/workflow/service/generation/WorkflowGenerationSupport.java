package org.github.flowify.workflow.service.generation;

import org.github.flowify.workflow.service.choice.dto.Action;

import java.util.Map;
import java.util.Set;

final class WorkflowGenerationSupport {

    static final String TARGET_VALUE_POLICY_PROMPT_KEYWORD = "prompt_keyword";
    static final String TARGET_VALUE_POLICY_GITHUB_REPO = "github_repo";
    static final String TARGET_VALUE_POLICY_FEED_SOURCE = "feed_source";
    static final String SINK_FIELD_VALUE_POLICY_EXPLICIT_EMAIL = "explicit_email";
    // TODO: Temporary AI generation bridge until the FE Gmail settings panel supports recipient source UX.
    static final String SINK_FIELD_VALUE_POLICY_CURRENT_USER_EMAIL = "current_user_email";
    static final Map<String, Set<String>> SUPPORTED_SOURCE_MODES = Map.of(
            "google_drive", Set.of("single_file", "file_changed", "new_file", "folder_new_file", "folder_all_files"),
            "gmail", Set.of("single_email", "new_email", "sender_email", "starred_email", "label_emails", "attachment_email"),
            "google_sheets", Set.of("sheet_all", "new_row", "row_updated"),
            "slack", Set.of("channel_messages"),
            "canvas_lms", Set.of("course_files", "course_new_file", "term_all_files"),
            "github", Set.of("new_pr"),
            "naver_news", Set.of("article_search", "new_articles"),
            "web_news", Set.of("seboard_posts", "seboard_new_posts", "website_feed")
    );
    static final Set<String> SUPPORTED_SINKS = Set.of(
            "slack",
            "discord",
            "gmail",
            "notion",
            "google_drive",
            "google_sheets",
            "google_calendar"
    );
    static final Set<String> SUPPORTED_ACTION_NODE_TYPES = Set.of("AI", "DATA_FILTER", "AI_FILTER");
    static final Set<String> SUPPORTED_DATA_FILTER_ACTIONS = Set.of(
            "filter_fields",
            "filter_fields_table",
            "filter_metadata",
            "filter_metadata_table"
    );
    static final Set<String> SUPPORTED_PROCESSING_METHOD_NODE_TYPES = Set.of("LOOP");
    static final Set<String> SUPPORTED_MIDDLE_NODE_TYPES = Set.of("AI", "DATA_FILTER", "AI_FILTER", "LOOP");
    static final Set<String> DIRECT_ACTION_BLOCKED_DATA_TYPES = Set.of("ARTICLE_LIST");
    private static final Map<String, Map<String, String>> SOURCE_TARGET_VALUE_POLICIES = Map.of(
            "github", Map.of(
                    "new_pr", TARGET_VALUE_POLICY_GITHUB_REPO
            ),
            "naver_news", Map.of(
                    "article_search", TARGET_VALUE_POLICY_PROMPT_KEYWORD,
                    "new_articles", TARGET_VALUE_POLICY_PROMPT_KEYWORD
            ),
            "web_news", Map.of(
                    "website_feed", TARGET_VALUE_POLICY_FEED_SOURCE
            )
    );
    private static final Map<String, Map<String, String>> SINK_FIELD_VALUE_POLICIES = Map.of(
            "gmail", Map.of(
                    "to", SINK_FIELD_VALUE_POLICY_EXPLICIT_EMAIL,
                    "to_source", SINK_FIELD_VALUE_POLICY_CURRENT_USER_EMAIL
            )
    );

    private WorkflowGenerationSupport() {
    }

    static String sourceTargetValuePolicy(String serviceKey, String sourceModeKey) {
        return SOURCE_TARGET_VALUE_POLICIES
                .getOrDefault(serviceKey, Map.of())
                .get(sourceModeKey);
    }

    static String sinkFieldValuePolicy(String serviceKey, String fieldKey) {
        return SINK_FIELD_VALUE_POLICIES
                .getOrDefault(serviceKey, Map.of())
                .get(fieldKey);
    }

    static boolean isSupportedProcessorAction(Action action) {
        if (action == null || action.getNodeType() == null || action.getId() == null) {
            return false;
        }
        if (!SUPPORTED_ACTION_NODE_TYPES.contains(action.getNodeType())) {
            return false;
        }
        if ("DATA_FILTER".equals(action.getNodeType())) {
            return SUPPORTED_DATA_FILTER_ACTIONS.contains(action.getId());
        }
        return true;
    }

    static boolean isSupportedGeneratedProcessorAction(String dataType, Action action) {
        if (!isSupportedProcessorAction(action)) {
            return false;
        }
        return !DIRECT_ACTION_BLOCKED_DATA_TYPES.contains(dataType);
    }
}
