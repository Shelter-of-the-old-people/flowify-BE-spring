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
                        "Gmail 첨부파일 Drive 백업",
                        "Gmail 첨부파일별 요약 Drive 저장",
                        "Gmail 첨부파일 메타데이터 Sheets 기록",
                        "새 메일 목록 요약 Discord 알림",
                        "새 메일 목록 요약 Gmail 발송",
                        "라벨 메일 목록 필드 추출 Sheets 저장",
                        "새 메일 목록 필드 추출 Sheets 저장",
                        "SE Board 새 글 Discord 알림",
                        "SE Board 새 글 Gmail 발송");

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
        assertGmailSummaryTemplate(
                templatesByName.get("특정 발신자 메일 요약 Discord 알림"),
                "sender_email",
                "event",
                "discord");
        assertGmailSummaryTemplate(
                templatesByName.get("특정 발신자 메일 요약 Notion 저장"),
                "sender_email",
                "event",
                "notion");
        assertGmailSummaryTemplate(
                templatesByName.get("특정 발신자 메일 요약 Gmail 발송"),
                "sender_email",
                "event",
                "gmail");
        assertGmailAttachmentBackupTemplate(
                templatesByName.get("Gmail 첨부파일 Drive 백업"));
        assertGmailAttachmentSummaryTemplate(
                templatesByName.get("Gmail 첨부파일별 요약 Drive 저장"));
        assertGmailAttachmentMetadataTemplate(
                templatesByName.get("Gmail 첨부파일 메타데이터 Sheets 기록"));
        assertGmailEmailListSummaryTemplate(
                templatesByName.get("새 메일 목록 요약 Discord 알림"),
                "new_email",
                "event",
                "discord");
        assertGmailEmailListSummaryTemplate(
                templatesByName.get("새 메일 목록 요약 Gmail 발송"),
                "new_email",
                "event",
                "gmail");
        assertGmailEmailFieldsTemplate(
                templatesByName.get("라벨 메일 목록 필드 추출 Sheets 저장"),
                "label_emails",
                "manual");
        assertGmailEmailFieldsTemplate(
                templatesByName.get("새 메일 목록 필드 추출 Sheets 저장"),
                "new_email",
                "event");
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

    private void assertGmailAttachmentBackupTemplate(Template template) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("gmail", "google_drive");

        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition sink = template.getNodes().get(1);

        assertThat(source.getOutputDataType()).isEqualTo("FILE_LIST");
        assertThat(source.getConfig())
                .containsEntry("source_mode", "attachment_email")
                .containsEntry("trigger_kind", "event");
        assertThat(sink.getDataType()).isEqualTo("FILE_LIST");
    }

    private void assertGmailAttachmentSummaryTemplate(Template template) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("gmail", "loop", "llm", "google_drive");

        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition loop = template.getNodes().get(1);
        NodeDefinition llm = template.getNodes().get(2);
        NodeDefinition sink = template.getNodes().get(3);

        assertThat(source.getOutputDataType()).isEqualTo("FILE_LIST");
        assertThat(source.getConfig())
                .containsEntry("source_mode", "attachment_email")
                .containsEntry("trigger_kind", "event");
        assertThat(loop.getDataType()).isEqualTo("FILE_LIST");
        assertThat(loop.getOutputDataType()).isEqualTo("SINGLE_FILE");
        assertThat(llm.getDataType()).isEqualTo("SINGLE_FILE");
        assertThat(llm.getOutputDataType()).isEqualTo("TEXT");
        assertThat(llm.getConfig()).containsEntry("requires_content", true);
        assertThat(sink.getDataType()).isEqualTo("TEXT");
    }

    private void assertGmailAttachmentMetadataTemplate(Template template) {
        assertThat(template.getNodes())
                .extracting(NodeDefinition::getType)
                .containsExactly("gmail", "loop", "DATA_FILTER", "google_sheets");

        NodeDefinition source = template.getNodes().get(0);
        NodeDefinition loop = template.getNodes().get(1);
        NodeDefinition filter = template.getNodes().get(2);
        NodeDefinition sheets = template.getNodes().get(3);

        assertThat(source.getOutputDataType()).isEqualTo("FILE_LIST");
        assertThat(source.getConfig())
                .containsEntry("source_mode", "attachment_email")
                .containsEntry("trigger_kind", "event");
        assertThat(loop.getDataType()).isEqualTo("FILE_LIST");
        assertThat(loop.getOutputDataType()).isEqualTo("SINGLE_FILE");
        assertThat(filter.getDataType()).isEqualTo("SINGLE_FILE");
        assertThat(filter.getOutputDataType()).isEqualTo("SPREADSHEET_DATA");
        assertThat(filter.getConfig())
                .containsEntry("choiceActionId", "filter_metadata_table")
                .containsEntry("choiceNodeType", "DATA_FILTER");
        assertThat(sheets.getDataType()).isEqualTo("SPREADSHEET_DATA");
    }

    private void assertGmailEmailListSummaryTemplate(
            Template template,
            String sourceMode,
            String triggerKind,
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
                .containsEntry("source_mode", sourceMode)
                .containsEntry("trigger_kind", triggerKind);
        assertThat(loop.getDataType()).isEqualTo("EMAIL_LIST");
        assertThat(loop.getOutputDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(llm.getDataType()).isEqualTo("SINGLE_EMAIL");
        assertThat(llm.getOutputDataType()).isEqualTo("TEXT");
        assertThat(llm.getConfig()).containsEntry("requires_content", true);
        assertThat(sink.getDataType()).isEqualTo("TEXT");
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
}
