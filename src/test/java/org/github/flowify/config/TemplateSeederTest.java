package org.github.flowify.config;

import org.github.flowify.template.entity.Template;
import org.github.flowify.template.repository.TemplateRepository;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateSeederTest {

    @Mock
    private TemplateRepository templateRepository;

    @Captor
    private ArgumentCaptor<Template> templateCaptor;

    @Test
    @DisplayName("요청한 시스템 템플릿을 시드한다")
    void seedsRequestedSystemTemplates() {
        when(templateRepository.findByNameAndIsSystem(anyString(), eq(true)))
                .thenReturn(Optional.empty());
        when(templateRepository.findByIsSystem(true)).thenReturn(List.of());

        new TemplateSeeder(templateRepository).run();

        Map<String, Template> templatesByName = captureSavedTemplatesByName();
        assertThat(templatesByName)
                .containsKeys(
                        "Drive 폴더 전체 파일 요약 Gmail 발송",
                        "Drive 폴더 전체 파일 요약 Notion 저장",
                        "Drive 신규 파일 요약 Gmail 발송",
                        "Drive 신규 파일 Discord 알림",
                        "Drive 폴더 파일 메타데이터 Sheets 기록",
                        "Drive 신규 파일 메타데이터 Sheets 기록",
                        "네이버 뉴스 요약 Discord 알림",
                        "네이버 뉴스 요약 Gmail 발송",
                        "네이버 뉴스 요약 Google Drive 저장",
                        "Gmail 단일 메일 요약 Discord 알림",
                        "Gmail 단일 메일 요약 Gmail 전달",
                        "Gmail 단일 메일 요약 Drive 저장",
                        "특정 발신자 메일 요약 Discord 알림",
                        "특정 발신자 메일 요약 Notion 저장",
                        "특정 발신자 메일 요약 Gmail 발송",
                        "라벨 메일 목록 필드 추출 Sheets 저장",
                        "SE Board 새 글 Discord 알림",
                        "SE Board 새 글 Gmail 발송",
                        "Sheets 전체 데이터 CSV Drive 저장",
                        "Sheets 새 행 Gmail 알림",
                        "Sheets 필드 추출 Drive 저장");
        assertThat(templatesByName)
                .doesNotContainKeys(
                        "Gmail 첨부파일 Drive 백업",
                        "Gmail 첨부파일별 요약 Drive 저장",
                        "Gmail 첨부파일 메타데이터 Sheets 기록",
                        "중요 메일 목록 요약 후 Notion 저장",
                        "중요 메일 목록에서 할 일 추출 후 Notion 저장",
                        "Sheets 새 행 Discord 알림",
                        "Google Sheets 전체 데이터 AI 분석 후 Gmail 발송");

        assertDriveSummaryTemplate(
                templatesByName.get("Drive 폴더 전체 파일 요약 Gmail 발송"),
                "folder_all_files",
                "manual",
                "gmail");
        assertDriveSummaryTemplate(
                templatesByName.get("Drive 폴더 전체 파일 요약 Notion 저장"),
                "folder_all_files",
                "manual",
                "notion");
        assertDriveSummaryTemplate(
                templatesByName.get("Drive 신규 파일 요약 Gmail 발송"),
                "folder_new_file",
                "event",
                "gmail");
        assertDriveSummaryTemplate(
                templatesByName.get("Drive 신규 파일 Discord 알림"),
                "folder_new_file",
                "event",
                "discord");
        assertDriveMetadataTemplate(
                templatesByName.get("Drive 폴더 파일 메타데이터 Sheets 기록"),
                "folder_all_files",
                "manual");
        assertDriveMetadataTemplate(
                templatesByName.get("Drive 신규 파일 메타데이터 Sheets 기록"),
                "folder_new_file",
                "event");

        assertArticleLoopTemplate(
                templatesByName.get("네이버 뉴스 요약 Discord 알림"),
                "naver_news",
                "article_search",
                "manual",
                "discord");
        assertArticleLoopTemplate(
                templatesByName.get("네이버 뉴스 요약 Gmail 발송"),
                "naver_news",
                "article_search",
                "manual",
                "gmail");
        assertArticleLoopTemplate(
                templatesByName.get("네이버 뉴스 요약 Google Drive 저장"),
                "naver_news",
                "article_search",
                "manual",
                "google_drive");
        assertArticleLoopTemplate(
                templatesByName.get("SE Board 새 글 Discord 알림"),
                "web_news",
                "seboard_new_posts",
                "event",
                "discord");
        assertArticleLoopTemplate(
                templatesByName.get("SE Board 새 글 Gmail 발송"),
                "web_news",
                "seboard_new_posts",
                "event",
                "gmail");
        assertGmailSummaryTemplate(
                templatesByName.get("Gmail 단일 메일 요약 Discord 알림"),
                "single_email",
                "manual",
                "discord");
        assertGmailSummaryTemplate(
                templatesByName.get("Gmail 단일 메일 요약 Gmail 전달"),
                "single_email",
                "manual",
                "gmail");
        assertGmailSummaryTemplate(
                templatesByName.get("Gmail 단일 메일 요약 Drive 저장"),
                "single_email",
                "manual",
                "google_drive");
        assertGmailSenderEmailListTemplate(
                templatesByName.get("특정 발신자 메일 요약 Discord 알림"),
                "discord");
        assertGmailSenderEmailListTemplate(
                templatesByName.get("특정 발신자 메일 요약 Notion 저장"),
                "notion");
        assertGmailSenderEmailListTemplate(
                templatesByName.get("특정 발신자 메일 요약 Gmail 발송"),
                "gmail");
        assertGmailEmailFieldsTemplate(
                templatesByName.get("라벨 메일 목록 필드 추출 Sheets 저장"),
                "label_emails",
                "manual");
        assertGithubAiLoopTemplate(
                templatesByName.get("GitHub 새 PR 요약 후 Discord 알림"),
                "discord");
        assertGithubAiLoopTemplate(
                templatesByName.get("GitHub 새 PR 요약 Gmail 발송"),
                "gmail");
        assertGithubAiLoopTemplate(
                templatesByName.get("GitHub 새 PR 요약 Notion 저장"),
                "notion");
    }

    @Test
    @DisplayName("legacy 추천 템플릿 이름을 새 이름으로 갱신한다")
    void renamesLegacyFeaturedTemplates() {
        Template legacyNewsDiscord = Template.builder()
                .id("legacy-news-discord")
                .name("뉴스 검색 결과 요약 후 Discord 알림")
                .useCount(2)
                .isSystem(true)
                .build();
        Template legacyNewsGmail = Template.builder()
                .id("legacy-news-gmail")
                .name("뉴스 검색 결과 요약 후 Gmail 전달")
                .useCount(3)
                .isSystem(true)
                .build();
        Template legacySeBoardDiscord = Template.builder()
                .id("legacy-seboard-discord")
                .name("SE Board 게시글 요약 후 Discord 알림")
                .useCount(4)
                .isSystem(true)
                .build();

        when(templateRepository.findByNameAndIsSystem(anyString(), eq(true)))
                .thenReturn(Optional.empty());
        when(templateRepository.findByNameAndIsSystem(eq("뉴스 검색 결과 요약 후 Discord 알림"), eq(true)))
                .thenReturn(Optional.of(legacyNewsDiscord));
        when(templateRepository.findByNameAndIsSystem(eq("뉴스 검색 결과 요약 후 Gmail 전달"), eq(true)))
                .thenReturn(Optional.of(legacyNewsGmail));
        when(templateRepository.findByNameAndIsSystem(eq("SE Board 게시글 요약 후 Discord 알림"), eq(true)))
                .thenReturn(Optional.of(legacySeBoardDiscord));
        when(templateRepository.findByIsSystem(true)).thenReturn(List.of());

        new TemplateSeeder(templateRepository).run();

        Map<String, Template> templatesByName = captureSavedTemplatesByName();
        assertThat(templatesByName.get("네이버 뉴스 요약 Discord 알림"))
                .extracting(Template::getId, Template::getUseCount)
                .containsExactly("legacy-news-discord", 2);
        assertThat(templatesByName.get("네이버 뉴스 요약 Gmail 발송"))
                .extracting(Template::getId, Template::getUseCount)
                .containsExactly("legacy-news-gmail", 3);
        assertThat(templatesByName.get("SE Board 새 글 Discord 알림"))
                .extracting(Template::getId, Template::getUseCount)
                .containsExactly("legacy-seboard-discord", 4);
    }

    @Test
    @DisplayName("Google Sheets source 템플릿 3개를 SPREADSHEET_DATA 계약으로 시드한다")
    void seedsGoogleSheetsSourceTemplatesWithSpreadsheetContracts() {
        when(templateRepository.findByNameAndIsSystem(anyString(), eq(true)))
                .thenReturn(Optional.empty());
        when(templateRepository.findByIsSystem(true)).thenReturn(List.of());

        new TemplateSeeder(templateRepository).run();

        Map<String, Template> templatesByName = captureSavedTemplatesByName();

        Template csvDrive = templatesByName.get("Sheets 전체 데이터 CSV Drive 저장");
        assertGoogleSheetsTemplateMetadata(csvDrive, List.of("google_sheets", "google_drive"));
        assertThat(csvDrive.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("google_sheets", "google_drive");
        assertGoogleSheetsSourceNode(csvDrive.getNodes().get(0), "sheet_all", "manual");
        assertGoogleSheetsDriveSinkNode(csvDrive.getNodes().get(1), "sheets_export_{{date}}");

        Template newRowGmail = templatesByName.get("Sheets 새 행 Gmail 알림");
        assertGoogleSheetsTemplateMetadata(newRowGmail, List.of("google_sheets", "gmail"));
        assertThat(newRowGmail.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("google_sheets", "llm", "gmail");
        assertGoogleSheetsSourceNode(newRowGmail.getNodes().get(0), "new_row", "event");
        assertGoogleSheetsAnalyzeNode(newRowGmail.getNodes().get(1), "summary");
        assertGoogleSheetsGmailSinkNode(newRowGmail.getNodes().get(2), "Google Sheets 새 행 알림");

        Template fieldExtractDrive = templatesByName.get("Sheets 필드 추출 Drive 저장");
        assertGoogleSheetsTemplateMetadata(fieldExtractDrive, List.of("google_sheets", "google_drive"));
        assertThat(fieldExtractDrive.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("google_sheets", "filter", "google_drive");
        assertGoogleSheetsSourceNode(fieldExtractDrive.getNodes().get(0), "sheet_all", "manual");
        NodeDefinition filter = fieldExtractDrive.getNodes().get(1);
        assertThat(filter.getDataType()).isEqualTo("SPREADSHEET_DATA");
        assertThat(filter.getOutputDataType()).isEqualTo("SPREADSHEET_DATA");
        assertThat(filter.getConfig())
                .containsEntry("isConfigured", false)
                .containsEntry("action", "filter_fields_table")
                .containsEntry("choiceActionId", "filter_fields_table")
                .containsEntry("choiceNodeType", "DATA_FILTER");
        assertThat(filter.getConfig().get("choiceSelections")).isEqualTo(Map.of("follow_up", List.of()));
        assertGoogleSheetsDriveSinkNode(fieldExtractDrive.getNodes().get(2), "sheets_selected_fields_{{date}}");
    }

    private Map<String, Template> captureSavedTemplatesByName() {
        verify(templateRepository, atLeastOnce()).save(templateCaptor.capture());
        return templateCaptor.getAllValues().stream()
                .collect(Collectors.toMap(
                        Template::getName,
                        Function.identity(),
                        (left, right) -> right));
    }

    private void assertArticleLoopTemplate(
            Template template,
            String sourceType,
            String sourceMode,
            String triggerKind,
            String sinkType) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly(sourceType, "loop", "llm", sinkType);

        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition loop = template.getNodes().get(1);
        NodeDefinition llm = template.getNodes().get(2);
        NodeDefinition sink = template.getNodes().get(3);

        assertThat(source.getOutputDataType()).isEqualTo("ARTICLE_LIST");
        assertThat(source.getConfig())
                .containsEntry("source_mode", sourceMode)
                .containsEntry("trigger_kind", triggerKind);
        assertThat(loop.getDataType()).isEqualTo("ARTICLE_LIST");
        assertThat(loop.getOutputDataType()).isEqualTo("TEXT");
        assertThat(llm.getDataType()).isEqualTo("TEXT");
        assertThat(llm.getOutputDataType()).isEqualTo("TEXT");
        assertThat(sink.getDataType()).isEqualTo("TEXT");
    }

    private void assertDriveSummaryTemplate(
            Template template,
            String sourceMode,
            String triggerKind,
            String sinkType) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("google_drive", "loop", "llm", sinkType);

        assertDriveLoopSource(template, sourceMode, triggerKind);

        NodeDefinition llm = template.getNodes().get(2);
        NodeDefinition sink = template.getNodes().get(3);

        assertThat(llm.getDataType()).isEqualTo("SINGLE_FILE");
        assertThat(llm.getOutputDataType()).isEqualTo("TEXT");
        assertThat(llm.getConfig()).containsEntry("requires_content", true);
        assertThat(sink.getDataType()).isEqualTo("TEXT");
    }

    private void assertDriveMetadataTemplate(
            Template template,
            String sourceMode,
            String triggerKind) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("google_drive", "loop", "DATA_FILTER", "google_sheets");

        assertDriveLoopSource(template, sourceMode, triggerKind);

        NodeDefinition filter = template.getNodes().get(2);
        NodeDefinition sheets = template.getNodes().get(3);

        assertThat(filter.getDataType()).isEqualTo("SINGLE_FILE");
        assertThat(filter.getOutputDataType()).isEqualTo("SPREADSHEET_DATA");
        assertThat(filter.getConfig())
                .containsEntry("choiceActionId", "filter_metadata_table")
                .containsEntry("choiceNodeType", "DATA_FILTER");
        assertThat(sheets.getDataType()).isEqualTo("SPREADSHEET_DATA");
    }

    private void assertDriveLoopSource(
            Template template,
            String sourceMode,
            String triggerKind) {
        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition loop = template.getNodes().get(1);

        assertThat(source.getOutputDataType()).isEqualTo("FILE_LIST");
        assertThat(source.getConfig())
                .containsEntry("source_mode", sourceMode)
                .containsEntry("trigger_kind", triggerKind);
        assertThat(loop.getDataType()).isEqualTo("FILE_LIST");
        assertThat(loop.getOutputDataType()).isEqualTo("SINGLE_FILE");
    }

    private void assertGmailSummaryTemplate(
            Template template,
            String sourceMode,
            String triggerKind,
            String sinkType) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("gmail", "llm", sinkType);

        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition llm = template.getNodes().get(1);
        NodeDefinition sink = template.getNodes().get(2);

        assertThat(source.getOutputDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(source.getConfig())
                .containsEntry("source_mode", sourceMode)
                .containsEntry("trigger_kind", triggerKind);
        assertThat(llm.getDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(llm.getOutputDataType()).isEqualTo("TEXT");
        assertThat(llm.getConfig()).containsEntry("requires_content", true);
        assertThat(sink.getDataType()).isEqualTo("TEXT");
    }

    private void assertGmailSenderEmailListTemplate(
            Template template,
            String sinkType) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("gmail", "loop", "llm", sinkType);

        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition loop = template.getNodes().get(1);
        NodeDefinition llm = template.getNodes().get(2);
        NodeDefinition sink = template.getNodes().get(3);

        assertThat(source.getOutputDataType()).isEqualTo("EMAIL_LIST");
        assertThat(source.getConfig())
                .containsEntry("source_mode", "sender_emails")
                .containsEntry("trigger_kind", "manual")
                .containsEntry("isConfigured", false)
                .containsEntry("maxResults", 100);
        assertThat(loop.getDataType()).isEqualTo("EMAIL_LIST");
        assertThat(loop.getOutputDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(llm.getDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(llm.getOutputDataType()).isEqualTo("TEXT");
        assertThat(llm.getConfig()).containsEntry("requires_content", true);
        assertThat(sink.getDataType()).isEqualTo("TEXT");
        if ("gmail".equals(sinkType) || "notion".equals(sinkType)) {
            assertThat(sink.getConfig()).containsEntry("loop_delivery_mode", "per_item");
        }
    }

    private void assertGmailEmailFieldsTemplate(
            Template template,
            String sourceMode,
            String triggerKind) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("gmail", "loop", "DATA_FILTER", "google_sheets");

        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition loop = template.getNodes().get(1);
        NodeDefinition filter = template.getNodes().get(2);
        NodeDefinition sheets = template.getNodes().get(3);

        assertThat(source.getOutputDataType()).isEqualTo("EMAIL_LIST");
        assertThat(source.getConfig())
                .containsEntry("source_mode", sourceMode)
                .containsEntry("trigger_kind", triggerKind);
        assertThat(loop.getDataType()).isEqualTo("EMAIL_LIST");
        assertThat(loop.getOutputDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(filter.getDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(filter.getOutputDataType()).isEqualTo("SPREADSHEET_DATA");
        assertThat(filter.getConfig())
                .containsEntry("choiceActionId", "filter_fields_table")
                .containsEntry("choiceNodeType", "DATA_FILTER");
        assertThat(sheets.getDataType()).isEqualTo("SPREADSHEET_DATA");
    }

    private void assertGithubAiLoopTemplate(Template template, String sinkType) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("github", "loop", "llm", sinkType);

        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition loop = template.getNodes().get(1);
        NodeDefinition llm = template.getNodes().get(2);
        NodeDefinition sink = template.getNodes().get(3);

        assertThat(source.getOutputDataType()).isEqualTo("API_RESPONSE");
        assertThat(source.getConfig())
                .containsEntry("service", "github")
                .containsEntry("source_mode", "new_pr")
                .containsEntry("trigger_kind", "event");
        assertThat(loop.getDataType()).isEqualTo("API_RESPONSE");
        assertThat(loop.getOutputDataType()).isEqualTo("API_RESPONSE");
        assertThat(loop.getConfig())
                .containsEntry("isConfigured", true)
                .containsEntry("items_field", "items")
                .containsEntry("choiceActionId", "loop")
                .containsEntry("choiceNodeType", "LOOP");
        assertThat(llm.getDataType()).isEqualTo("API_RESPONSE");
        assertThat(llm.getOutputDataType()).isEqualTo("TEXT");
        assertThat(llm.getConfig())
                .containsEntry("choiceActionId", "ai_analyze")
                .containsEntry("choiceNodeType", "AI");
        assertThat(llm.getConfig().get("choiceSelections"))
                .isEqualTo(Map.of("follow_up", "one_paragraph"));
        assertThat(sink.getDataType()).isEqualTo("TEXT");

        assertThat(template.getEdges())
                .extracting(edge -> edge.getSource() + "->" + edge.getTarget())
                .containsExactly(
                        "node_github_start->node_loop_prs",
                        "node_loop_prs->node_llm_analyze",
                        "node_llm_analyze->node_" + sinkType + "_end");
    }

    private void assertGoogleSheetsTemplateMetadata(Template template, List<String> requiredServices) {
        assertThat(template.getFolderKey()).isEqualTo("google_sheets");
        assertThat(template.getCategory()).isEqualTo("spreadsheet");
        assertThat(template.getIcon()).isEqualTo("google_sheets");
        assertThat(template.getRequiredServices()).containsExactlyElementsOf(requiredServices);
    }

    private void assertGoogleSheetsSourceNode(NodeDefinition node, String sourceMode, String triggerKind) {
        assertThat(node.getOutputDataType()).isEqualTo("SPREADSHEET_DATA");
        assertThat(node.getConfig())
                .containsEntry("isConfigured", false)
                .containsEntry("service", "google_sheets")
                .containsEntry("source_mode", sourceMode)
                .containsEntry("target", "")
                .containsEntry("target_label", "")
                .containsEntry("sheet_name", "Sheet1")
                .containsEntry("range_a1", "")
                .containsEntry("header_row", 1)
                .containsEntry("data_start_row", 2)
                .containsEntry("trigger_kind", triggerKind)
                .doesNotContainKeys("token", "access_token", "baseUrl", "base_url", "spreadsheet_id");
        assertThat(node.getConfig().get("target_meta")).isEqualTo(Map.of("pickerType", "spreadsheet"));
        if ("new_row".equals(sourceMode)) {
            assertThat(node.getConfig()).containsEntry("initial_sync_mode", "skip_existing");
        }
    }

    private void assertGoogleSheetsAnalyzeNode(NodeDefinition node, String followUp) {
        assertThat(node.getDataType()).isEqualTo("SPREADSHEET_DATA");
        assertThat(node.getOutputDataType()).isEqualTo("TEXT");
        assertThat(node.getConfig())
                .containsEntry("isConfigured", true)
                .containsEntry("prompt", "")
                .containsEntry("model", "gpt-4.1-mini")
                .containsEntry("action", "ai_analyze")
                .containsEntry("outputFormat", "text")
                .containsEntry("temperature", 0.2)
                .containsEntry("choiceActionId", "ai_analyze")
                .containsEntry("choiceNodeType", "AI");
        assertThat(node.getConfig().get("choiceSelections")).isEqualTo(Map.of("follow_up", followUp));
    }

    private void assertGoogleSheetsDriveSinkNode(NodeDefinition node, String filenameTemplate) {
        assertThat(node.getDataType()).isEqualTo("SPREADSHEET_DATA");
        assertThat(node.getConfig())
                .containsEntry("isConfigured", false)
                .containsEntry("service", "google_drive")
                .containsEntry("folder_id", "")
                .containsEntry("drive_action", "copy")
                .containsEntry("filename_template", filenameTemplate)
                .doesNotContainKeys("token", "access_token", "baseUrl", "base_url");
    }

    private void assertGoogleSheetsGmailSinkNode(NodeDefinition node, String subject) {
        assertThat(node.getDataType()).isEqualTo("TEXT");
        assertThat(node.getConfig())
                .containsEntry("isConfigured", false)
                .containsEntry("service", "gmail")
                .containsEntry("to", "")
                .containsEntry("subject", subject)
                .containsEntry("body", "")
                .containsEntry("action", "send")
                .containsEntry("body_format", "plain")
                .doesNotContainKeys("token", "access_token", "baseUrl", "base_url", "spreadsheet_id");
    }
}
