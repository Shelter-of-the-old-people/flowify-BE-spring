package org.github.flowify.workflow.service.generation;

import java.util.Map;
import java.util.Set;

final class WorkflowGenerationSupport {

    static final Map<String, Set<String>> SUPPORTED_SOURCE_MODES = Map.of(
            "google_drive", Set.of("single_file", "file_changed", "new_file", "folder_new_file", "folder_all_files"),
            "gmail", Set.of("single_email", "new_email", "sender_email", "starred_email", "label_emails", "attachment_email"),
            "google_sheets", Set.of("sheet_all", "new_row", "row_updated"),
            "slack", Set.of("channel_messages"),
            "canvas_lms", Set.of("course_files", "course_new_file", "term_all_files"),
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
    static final Set<String> SUPPORTED_PROCESSING_METHOD_NODE_TYPES = Set.of("LOOP");
    static final Set<String> SUPPORTED_MIDDLE_NODE_TYPES = Set.of("AI", "DATA_FILTER", "AI_FILTER", "LOOP");

    private WorkflowGenerationSupport() {
    }
}
