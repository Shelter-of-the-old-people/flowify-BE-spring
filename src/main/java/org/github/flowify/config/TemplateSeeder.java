package org.github.flowify.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.template.entity.Template;
import org.github.flowify.template.repository.TemplateRepository;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Position;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateSeeder implements CommandLineRunner {

    private static final String REMOVED_SERVICE_SLACK = "slack";
    private static final Set<String> FEATURED_TEMPLATE_NAMES = Set.of(
            "GitHub 새 PR 요약 후 Discord 알림",
            "GitHub 새 PR 링크를 Google Sheets에 저장",
            "신규 문서 요약 후 Gmail 전달",
            "문서 요약 결과를 Google Sheets에 저장",
            "Drive 폴더 전체 파일 요약 Gmail 발송",
            "Drive 폴더 전체 파일 요약 Notion 저장",
            "Drive 신규 파일 요약 Gmail 발송",
            "Drive 신규 파일 Discord 알림",
            "Drive 폴더 파일 메타데이터 Sheets 기록",
            "Drive 신규 파일 메타데이터 Sheets 기록",
            "SE Board 게시글 요약 후 Notion 저장",
            "SE Board 새 글 Discord 알림",
            "SE Board 새 글 Gmail 발송",
            "뉴스 검색 결과 요약 후 Notion 저장",
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
            "중요 메일 목록 요약 후 Notion 저장",
            "중요 메일 목록에서 할 일 추출 후 Notion 저장");

    private final TemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        int created = 0;
        int updated = 0;

        if (upsertTemplate(buildGithubDiscordTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildGithubSheetsTemplate(),
                "GitHub 새 PR 기록을 Google Sheets에 저장")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildFolderDocumentGmailTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildFolderDocumentSheetsTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildDriveFolderAllFilesGmailTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildDriveFolderAllFilesNotionTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildDriveNewFileGmailTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildDriveNewFileDiscordTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildDriveFolderMetadataSheetsTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildDriveNewFileMetadataSheetsTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildSeBoardNotionTemplate(),
                "SE Board 공지 요약 후 Notion 저장")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildSeBoardDiscordTemplate(),
                "SE Board 게시글 요약 후 Discord 알림")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildSeBoardGmailTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildNewsSearchNotionTemplate(),
                "뉴스 수집 요약 후 Notion 저장")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildNewsSearchDiscordTemplate(),
                "뉴스 검색 결과 요약 후 Discord 알림")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildNewsSearchGmailTemplate(),
                "뉴스 검색 결과 요약 후 Gmail 전달")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildNewsSearchDriveTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailSingleEmailDiscordTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailSingleEmailGmailTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailSingleEmailDriveTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailSenderEmailDiscordTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailSenderEmailNotionTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailSenderEmailGmailTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailAttachmentDriveBackupTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailAttachmentSummaryDriveTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailAttachmentMetadataSheetsTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailNewEmailDiscordTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailNewEmailGmailTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailLabelEmailFieldsSheetsTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildGmailNewEmailFieldsSheetsTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildImportantMailNotionTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildImportantMailTodosNotionTemplate())) {
            updated++;
        } else {
            created++;
        }

        int removed = removeNonFeaturedSystemTemplates();

        log.info("시스템 템플릿 시드 완료: 신규 {}개, 갱신 {}개, 제거 {}개", created, updated, removed);
    }

    private boolean upsertTemplate(Template seedTemplate, String... legacyNames) {
        Optional<Template> existing = findExistingSystemTemplate(seedTemplate.getName(), legacyNames);
        if (existing.isPresent()) {
            Template current = existing.get();
            seedTemplate.setId(current.getId());
            seedTemplate.setUseCount(current.getUseCount());
            seedTemplate.setCreatedAt(current.getCreatedAt());
            if (seedTemplate.getFolderKey() == null) {
                seedTemplate.setFolderKey(current.getFolderKey());
            }
            templateRepository.save(seedTemplate);
            return true;
        }

        templateRepository.save(seedTemplate);
        return false;
    }

    private Optional<Template> findExistingSystemTemplate(String name, String... legacyNames) {
        Optional<Template> existing = templateRepository.findByNameAndIsSystem(name, true);
        if (existing.isPresent()) {
            return existing;
        }

        if (legacyNames == null) {
            return Optional.empty();
        }

        for (String legacyName : legacyNames) {
            if (legacyName == null || legacyName.isBlank()) {
                continue;
            }
            existing = templateRepository.findByNameAndIsSystem(legacyName, true);
            if (existing.isPresent()) {
                return existing;
            }
        }

        return Optional.empty();
    }

    private int removeNonFeaturedSystemTemplates() {
        List<Template> removedTemplates = templateRepository.findByIsSystem(true).stream()
                .filter(template -> template.getRequiredServices().contains(REMOVED_SERVICE_SLACK)
                        || !FEATURED_TEMPLATE_NAMES.contains(template.getName()))
                .toList();
        if (removedTemplates.isEmpty()) {
            return 0;
        }

        templateRepository.deleteAll(removedTemplates);
        return removedTemplates.size();
    }

    // ── 기존 템플릿 ──

    private Template buildStudyNoteTemplate() {
        NodeDefinition input = NodeDefinition.builder()
                .id("node_1").category("storage").type("google_drive")
                .role("start").dataType("FILE_LIST").outputDataType("FILE_LIST")
                .position(new Position(80, 180))
                .build();
        NodeDefinition ai = NodeDefinition.builder()
                .id("node_2").category("ai").type("AI")
                .role("middle").dataType("SINGLE_FILE").outputDataType("TEXT")
                .position(new Position(300, 180))
                .build();
        NodeDefinition output = NodeDefinition.builder()
                .id("node_3").category("storage").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(520, 180))
                .build();

        return Template.builder()
                .name("학습 노트 자동 생성")
                .description("Google Drive 파일을 AI로 요약하여 Notion에 저장합니다.")
                .category("storage")
                .icon("book")
                .nodes(List.of(input, ai, output))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_1_2").source("node_1").target("node_2").build(),
                        EdgeDefinition.builder().id("edge_2_3").source("node_2").target("node_3").build()))
                .requiredServices(List.of("google_drive", "notion"))
                .isSystem(true)
                .build();
    }

    private Template buildNewsCrawlTemplate() {
        NodeDefinition input = NodeDefinition.builder()
                .id("node_1").category("web_crawl").type("naver_news")
                .role("start").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(80, 180))
                .build();
        NodeDefinition ai = NodeDefinition.builder()
                .id("node_2").category("ai").type("AI")
                .role("middle").dataType("TEXT").outputDataType("SPREADSHEET_DATA")
                .position(new Position(300, 180))
                .build();
        NodeDefinition output = NodeDefinition.builder()
                .id("node_3").category("spreadsheet").type("google_sheets")
                .role("end").dataType("SPREADSHEET_DATA")
                .position(new Position(520, 180))
                .build();

        return Template.builder()
                .name("뉴스 수집 및 정리")
                .description("네이버 뉴스를 수집하고 AI로 요약하여 Google Sheets에 기록합니다.")
                .category("web_crawl")
                .icon("newspaper")
                .nodes(List.of(input, ai, output))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_1_2").source("node_1").target("node_2").build(),
                        EdgeDefinition.builder().id("edge_2_3").source("node_2").target("node_3").build()))
                .requiredServices(List.of("google_drive"))
                .isSystem(true)
                .build();
    }

    private Template buildSheetReportTemplate() {
        NodeDefinition input = NodeDefinition.builder()
                .id("node_1").category("spreadsheet").type("google_sheets")
                .role("start").dataType("SPREADSHEET_DATA").outputDataType("SPREADSHEET_DATA")
                .position(new Position(80, 180))
                .build();
        NodeDefinition ai = NodeDefinition.builder()
                .id("node_2").category("ai").type("AI")
                .role("middle").dataType("SPREADSHEET_DATA").outputDataType("TEXT")
                .position(new Position(300, 180))
                .build();
        NodeDefinition output = NodeDefinition.builder()
                .id("node_3").category("storage").type("google_drive")
                .role("end").dataType("TEXT")
                .position(new Position(520, 180))
                .build();

        return Template.builder()
                .name("구글 시트 → 리포트 생성")
                .description("Google Sheets 데이터를 AI로 분석하여 리포트를 생성합니다.")
                .category("spreadsheet")
                .icon("chart")
                .nodes(List.of(input, ai, output))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_1_2").source("node_1").target("node_2").build(),
                        EdgeDefinition.builder().id("edge_2_3").source("node_2").target("node_3").build()))
                .requiredServices(List.of("google_drive"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailSingleEmailDiscordTemplate() {
        NodeDefinition gmail = buildGmailSingleEmailSourceNode("single_email", "manual");
        NodeDefinition llm = buildGmailSummaryNode();
        NodeDefinition discord = buildDiscordSinkNode();

        return Template.builder()
                .name("Gmail 단일 메일 요약 Discord 알림")
                .description("선택한 Gmail 메일 한 건을 요약해 Discord로 전달합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, llm, discord))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_llm").source("node_gmail_start").target("node_llm_gmail_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_discord").source("node_llm_gmail_summary").target("node_discord_end").build()))
                .requiredServices(List.of("gmail", "discord"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailSingleEmailGmailTemplate() {
        NodeDefinition gmail = buildGmailSingleEmailSourceNode("single_email", "manual");
        NodeDefinition llm = buildGmailSummaryNode();
        NodeDefinition gmailSink = buildGmailSinkNode("Gmail 메일 요약");

        return Template.builder()
                .name("Gmail 단일 메일 요약 Gmail 전달")
                .description("선택한 Gmail 메일 한 건을 요약해 Gmail로 전달합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, llm, gmailSink))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_llm").source("node_gmail_start").target("node_llm_gmail_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_gmail_summary").target("node_gmail_end").build()))
                .requiredServices(List.of("gmail"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailSingleEmailDriveTemplate() {
        NodeDefinition gmail = buildGmailSingleEmailSourceNode("single_email", "manual");
        NodeDefinition llm = buildGmailSummaryNode();
        NodeDefinition drive = buildDriveTextSinkNode("gmail_summary_{{date}}");

        return Template.builder()
                .name("Gmail 단일 메일 요약 Drive 저장")
                .description("선택한 Gmail 메일 한 건을 요약해 Google Drive에 텍스트 파일로 저장합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, llm, drive))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_llm").source("node_gmail_start").target("node_llm_gmail_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_drive").source("node_llm_gmail_summary").target("node_drive_end").build()))
                .requiredServices(List.of("gmail", "google_drive"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailSenderEmailDiscordTemplate() {
        NodeDefinition gmail = buildGmailSingleEmailSourceNode("sender_email", "event");
        NodeDefinition llm = buildGmailSummaryNode();
        NodeDefinition discord = buildDiscordSinkNode();

        return Template.builder()
                .name("특정 발신자 메일 요약 Discord 알림")
                .description("특정 발신자의 Gmail 새 메일을 요약해 Discord로 전달합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, llm, discord))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_llm").source("node_gmail_start").target("node_llm_gmail_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_discord").source("node_llm_gmail_summary").target("node_discord_end").build()))
                .requiredServices(List.of("gmail", "discord"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailSenderEmailNotionTemplate() {
        NodeDefinition gmail = buildGmailSingleEmailSourceNode("sender_email", "event");
        NodeDefinition llm = buildGmailSummaryNode();
        NodeDefinition notion = buildNotionSinkNode("발신자 메일 요약 - {{date}}");

        return Template.builder()
                .name("특정 발신자 메일 요약 Notion 저장")
                .description("특정 발신자의 Gmail 새 메일을 요약해 Notion에 저장합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, llm, notion))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_llm").source("node_gmail_start").target("node_llm_gmail_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_notion").source("node_llm_gmail_summary").target("node_notion_end").build()))
                .requiredServices(List.of("gmail", "notion"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailSenderEmailGmailTemplate() {
        NodeDefinition gmail = buildGmailSingleEmailSourceNode("sender_email", "event");
        NodeDefinition llm = buildGmailSummaryNode();
        NodeDefinition gmailSink = buildGmailSinkNode("특정 발신자 메일 요약");

        return Template.builder()
                .name("특정 발신자 메일 요약 Gmail 발송")
                .description("특정 발신자의 Gmail 새 메일을 요약해 Gmail로 발송합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, llm, gmailSink))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_llm").source("node_gmail_start").target("node_llm_gmail_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_gmail_summary").target("node_gmail_end").build()))
                .requiredServices(List.of("gmail"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailAttachmentDriveBackupTemplate() {
        NodeDefinition gmail = buildGmailAttachmentSourceNode();
        NodeDefinition drive = buildDriveFileListSinkNode();

        return Template.builder()
                .name("Gmail 첨부파일 Drive 백업")
                .description("첨부파일이 있는 Gmail 메일을 감지해 첨부파일을 Google Drive 폴더에 백업합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, drive))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_drive").source("node_gmail_start").target("node_drive_end").build()))
                .requiredServices(List.of("gmail", "google_drive"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailAttachmentSummaryDriveTemplate() {
        NodeDefinition gmail = buildGmailAttachmentSourceNode();
        NodeDefinition loop = buildDriveFileLoopNode();
        NodeDefinition llm = buildGmailAttachmentSummaryNode();
        NodeDefinition drive = buildDriveTextSinkNode("gmail_attachment_summary_{{index}}_{{date}}");

        return Template.builder()
                .name("Gmail 첨부파일별 요약 Drive 저장")
                .description("첨부파일이 있는 Gmail 메일을 감지해 첨부파일을 하나씩 요약하고 Google Drive에 저장합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, llm, drive))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_attachment_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_drive").source("node_llm_attachment_summary").target("node_drive_end").build()))
                .requiredServices(List.of("gmail", "google_drive"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailAttachmentMetadataSheetsTemplate() {
        NodeDefinition gmail = buildGmailAttachmentSourceNode();
        NodeDefinition loop = buildDriveFileLoopNode();
        NodeDefinition filter = buildGmailAttachmentMetadataTableNode();
        NodeDefinition sheets = buildSheetsSinkNode();

        return Template.builder()
                .name("Gmail 첨부파일 메타데이터 Sheets 기록")
                .description("첨부파일이 있는 Gmail 메일을 감지해 첨부파일 메타데이터를 Google Sheets에 기록합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, filter, sheets))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_filter").source("node_loop").target("node_filter_metadata").build(),
                        EdgeDefinition.builder().id("edge_filter_to_sheets").source("node_filter_metadata").target("node_sheets_end").build()))
                .requiredServices(List.of("gmail", "google_sheets"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailNewEmailDiscordTemplate() {
        NodeDefinition gmail = buildGmailEmailListSourceNode("new_email", "event");
        NodeDefinition loop = buildEmailListLoopNode();
        NodeDefinition llm = buildGmailEmailListSummaryNode();
        NodeDefinition discord = buildDiscordSinkNode();

        return Template.builder()
                .name("새 메일 목록 요약 Discord 알림")
                .description("새 Gmail 메일 목록을 하나씩 요약해 Discord로 전달합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, llm, discord))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_gmail_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_discord").source("node_llm_gmail_summary").target("node_discord_end").build()))
                .requiredServices(List.of("gmail", "discord"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailNewEmailGmailTemplate() {
        NodeDefinition gmail = buildGmailEmailListSourceNode("new_email", "event");
        NodeDefinition loop = buildEmailListLoopNode();
        NodeDefinition llm = buildGmailEmailListSummaryNode();
        NodeDefinition gmailSink = buildGmailSinkNode("새 메일 목록 요약");

        return Template.builder()
                .name("새 메일 목록 요약 Gmail 발송")
                .description("새 Gmail 메일 목록을 하나씩 요약해 Gmail로 발송합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, llm, gmailSink))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_gmail_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_gmail_summary").target("node_gmail_end").build()))
                .requiredServices(List.of("gmail"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailLabelEmailFieldsSheetsTemplate() {
        NodeDefinition gmail = buildGmailEmailListSourceNode("label_emails", "manual");
        NodeDefinition loop = buildEmailListLoopNode();
        NodeDefinition filter = buildGmailEmailFieldsTableNode();
        NodeDefinition sheets = buildSheetsSinkNode();

        return Template.builder()
                .name("라벨 메일 목록 필드 추출 Sheets 저장")
                .description("선택한 Gmail 라벨의 메일 목록에서 주요 필드를 추출해 Google Sheets에 기록합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, filter, sheets))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_filter").source("node_loop").target("node_filter_fields").build(),
                        EdgeDefinition.builder().id("edge_filter_to_sheets").source("node_filter_fields").target("node_sheets_end").build()))
                .requiredServices(List.of("gmail", "google_sheets"))
                .isSystem(true)
                .build();
    }

    private Template buildGmailNewEmailFieldsSheetsTemplate() {
        NodeDefinition gmail = buildGmailEmailListSourceNode("new_email", "event");
        NodeDefinition loop = buildEmailListLoopNode();
        NodeDefinition filter = buildGmailEmailFieldsTableNode();
        NodeDefinition sheets = buildSheetsSinkNode();

        return Template.builder()
                .name("새 메일 목록 필드 추출 Sheets 저장")
                .description("새 Gmail 메일 목록에서 주요 필드를 추출해 Google Sheets에 기록합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, filter, sheets))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_filter").source("node_loop").target("node_filter_fields").build(),
                        EdgeDefinition.builder().id("edge_filter_to_sheets").source("node_filter_fields").target("node_sheets_end").build()))
                .requiredServices(List.of("gmail", "google_sheets"))
                .isSystem(true)
                .build();
    }

    private NodeDefinition buildGmailSingleEmailSourceNode(String sourceMode, String triggerKind) {
        return NodeDefinition.builder()
                .id("node_gmail_start").category("service").type("gmail")
                .role("start").outputDataType("SINGLE_EMAIL")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "gmail",
                        "source_mode", sourceMode,
                        "target", "",
                        "target_label", "",
                        "trigger_kind", triggerKind,
                        "maxResults", 1))
                .build();
    }

    private NodeDefinition buildGmailAttachmentSourceNode() {
        return NodeDefinition.builder()
                .id("node_gmail_start").category("service").type("gmail")
                .role("start").outputDataType("FILE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "service", "gmail",
                        "source_mode", "attachment_email",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "event",
                        "maxResults", 1))
                .build();
    }

    private NodeDefinition buildGmailEmailListSourceNode(String sourceMode, String triggerKind) {
        boolean requiresLabel = "label_emails".equals(sourceMode);
        return NodeDefinition.builder()
                .id("node_gmail_start").category("service").type("gmail")
                .role("start").outputDataType("EMAIL_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", !requiresLabel,
                        "service", "gmail",
                        "source_mode", sourceMode,
                        "target", "",
                        "target_label", "",
                        "target_meta", requiresLabel ? Map.of("pickerType", "label") : Map.of(),
                        "trigger_kind", triggerKind,
                        "maxResults", 100))
                .build();
    }

    private NodeDefinition buildEmailListLoopNode() {
        return NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("EMAIL_LIST").outputDataType("SINGLE_EMAIL")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "targetField", "items",
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
    }

    private NodeDefinition buildGmailSummaryNode() {
        return NodeDefinition.builder()
                .id("node_llm_gmail_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_EMAIL").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 Gmail 메일을 요약해줘. 발신자, 제목, 핵심 요약 2~3문장, 주요 포인트, 필요한 후속 액션을 포함하고 알림이나 기록에 바로 사용할 수 있는 문장으로 정리해줘.",
                        "model", "gpt-4.1-mini",
                        "action", "summarize",
                        "requires_content", true,
                        "outputFormat", "text",
                        "temperature", 0.3,
                        "summaryFormat", "single_mail_digest_v1",
                        "resultMode", "single_aggregated"))
                .build();
    }

    private NodeDefinition buildGmailEmailListSummaryNode() {
        return NodeDefinition.builder()
                .id("node_llm_gmail_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_EMAIL").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 Gmail 메일을 요약해줘. 발신자, 제목, 핵심 요약 2~3문장, 주요 포인트, 필요한 후속 액션을 포함하고 알림이나 기록에 바로 사용할 수 있는 문장으로 정리해줘.",
                        "model", "gpt-4.1-mini",
                        "action", "summarize",
                        "requires_content", true,
                        "outputFormat", "text",
                        "temperature", 0.3,
                        "summaryFormat", "single_mail_digest_v1",
                        "resultMode", "single_aggregated"))
                .build();
    }

    private NodeDefinition buildGmailAttachmentSummaryNode() {
        return NodeDefinition.builder()
                .id("node_llm_attachment_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_FILE").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 Gmail 첨부파일 내용을 요약해줘. 파일명, 메일 첨부파일이라는 맥락, 핵심 요약 2~3문장, 주요 포인트, 원문 링크나 다운로드 링크가 있으면 함께 정리해줘. 파일 본문을 읽을 수 없으면 사용 가능한 메타데이터 중심으로 정리해줘.",
                        "model", "gpt-4.1-mini",
                        "action", "summarize",
                        "requires_content", true,
                        "outputFormat", "text",
                        "temperature", 0.3,
                        "summaryFormat", "gmail_attachment_digest_v1",
                        "resultMode", "single_aggregated"))
                .build();
    }

    private NodeDefinition buildGmailAttachmentMetadataTableNode() {
        return NodeDefinition.builder()
                .id("node_filter_metadata").category("logic").type("DATA_FILTER")
                .role("middle").dataType("SINGLE_FILE").outputDataType("SPREADSHEET_DATA")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "choiceActionId", "filter_metadata_table",
                        "choiceNodeType", "DATA_FILTER",
                        "choiceSelections", Map.of(
                                "follow_up", List.of("filename", "link", "file_size", "mime_type"))))
                .build();
    }

    private NodeDefinition buildGmailEmailFieldsTableNode() {
        return NodeDefinition.builder()
                .id("node_filter_fields").category("logic").type("DATA_FILTER")
                .role("middle").dataType("SINGLE_EMAIL").outputDataType("SPREADSHEET_DATA")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "choiceActionId", "filter_fields_table",
                        "choiceNodeType", "DATA_FILTER",
                        "choiceSelections", Map.of(
                                "follow_up", List.of("subject", "sender", "body_preview"))))
                .build();
    }

    private Template buildImportantMailNotionTemplate() {
        NodeDefinition gmail = NodeDefinition.builder()
                .id("node_gmail_start").category("service").type("gmail")
                .role("start").outputDataType("EMAIL_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "service", "gmail",
                        "source_mode", "label_emails",
                        "target", "IMPORTANT",
                        "target_label", "중요 메일",
                        "target_meta", Map.of("systemLabel", true),
                        "maxResults", 100))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("EMAIL_LIST").outputDataType("EMAIL_LIST")
                .position(new Position(300, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "targetField", "items",
                        "maxIterations", 100,
                        "timeout", 300))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_summary").category("ai").type("llm")
                .role("middle").dataType("EMAIL_LIST").outputDataType("TEXT")
                .position(new Position(520, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 중요 메일 목록의 모든 메일을 빠짐없이 포함해 Notion 기록용 요약을 작성해줘. 각 메일은 번호를 붙이고, 발신자/제목/핵심 내용/액션 필요 여부 형식으로 정리해줘.",
                        "model", "gpt-4.1-mini",
                        "outputFormat", "text",
                        "temperature", 0.3,
                        "summaryFormat", "mail_digest_v1",
                        "resultMode", "single_aggregated"))
                .build();
        NodeDefinition notion = NodeDefinition.builder()
                .id("node_notion_end").category("service").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(740, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "target_type", "page",
                        "target_id", "",
                        "title_template", "메일 요약 - {{date}}"))
                .build();

        return Template.builder()
                .name("중요 메일 목록 요약 후 Notion 저장")
                .description("중요 메일 목록을 정해진 형식으로 요약해 Notion 페이지에 저장합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, llm, notion))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_notion").source("node_llm_summary").target("node_notion_end").build()))
                .requiredServices(List.of("gmail", "notion"))
                .isSystem(true)
                .build();
    }

    private Template buildImportantMailTodosNotionTemplate() {
        NodeDefinition gmail = NodeDefinition.builder()
                .id("node_gmail_start").category("service").type("gmail")
                .role("start").outputDataType("EMAIL_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "service", "gmail",
                        "source_mode", "label_emails",
                        "target", "IMPORTANT",
                        "target_label", "중요 메일",
                        "target_meta", Map.of("systemLabel", true),
                        "maxResults", 100))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("EMAIL_LIST").outputDataType("EMAIL_LIST")
                .position(new Position(300, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "targetField", "items",
                        "maxIterations", 100,
                        "timeout", 300))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_todos").category("ai").type("llm")
                .role("middle").dataType("EMAIL_LIST").outputDataType("TEXT")
                .position(new Position(520, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 중요 메일 목록의 모든 메일을 빠짐없이 검토해서 해야 할 일만 추출해 Notion 기록용으로 정리해줘. 각 항목은 메일 번호, 발신자, 제목, 해야 할 일, 마감/확인 필요 여부 형식으로 작성해줘.",
                        "model", "gpt-4.1-mini",
                        "outputFormat", "text",
                        "temperature", 0.2,
                        "summaryFormat", "mail_action_items_v1",
                        "resultMode", "single_aggregated"))
                .build();
        NodeDefinition notion = NodeDefinition.builder()
                .id("node_notion_end").category("service").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(740, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "target_type", "page",
                        "target_id", "",
                        "title_template", "할 일 추출 - {{date}}"))
                .build();

        return Template.builder()
                .name("중요 메일 목록에서 할 일 추출 후 Notion 저장")
                .description("중요 메일 목록에서 해야 할 일을 추출해 Notion 페이지에 저장합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, llm, notion))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_todos").build(),
                        EdgeDefinition.builder().id("edge_llm_to_notion").source("node_llm_todos").target("node_notion_end").build()))
                .requiredServices(List.of("gmail", "notion"))
                .isSystem(true)
                .build();
    }

    private Template buildFolderDocumentGmailTemplate() {
        NodeDefinition drive = buildFolderDocumentSourceNode();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_FILE").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 문서 내용을 바탕으로 이메일 전달용 요약을 작성해줘. 문서명, 핵심 요약 2~3문장, 주요 포인트 3개 이내를 포함하고, 메일 본문으로 바로 붙여넣을 수 있게 자연스럽게 정리해줘.",
                        "model", "gpt-4.1-mini",
                        "action", "summarize",
                        "requires_content", true,
                        "outputFormat", "text",
                        "temperature", 0.3,
                        "summaryFormat", "document_digest_email_v1",
                        "resultMode", "single_aggregated"))
                .build();
        NodeDefinition gmail = NodeDefinition.builder()
                .id("node_gmail_end").category("service").type("gmail")
                .role("end").dataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "gmail",
                        "to", "",
                        "subject", "문서 요약",
                        "action", "send"))
                .build();

        return Template.builder()
                .name("신규 문서 요약 후 Gmail 전달")
                .description("지정한 Google Drive 폴더의 문서를 읽어 핵심 내용을 요약하고 이메일로 전달합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, llm, gmail))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_llm").source("node_drive_start").target("node_llm_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_summary").target("node_gmail_end").build()))
                .requiredServices(List.of("google_drive", "gmail"))
                .isSystem(true)
                .build();
    }

    private Template buildFolderDocumentSheetsTemplate() {
        NodeDefinition drive = buildFolderDocumentSourceNode();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_FILE").outputDataType("SPREADSHEET_DATA")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 문서 내용을 분석해서 Google Sheets에 바로 기록할 JSON만 반환해줘. 반드시 {\"headers\": [...], \"rows\": [[...]]} 형식을 지키고, headers는 [\"document_name\", \"summary\", \"highlights\", \"source_url\"]로 고정해줘. summary는 1~2문장, highlights는 하나의 문자열로 정리해줘.",
                        "model", "gpt-4.1-mini",
                        "action", "ai_analyze",
                        "requires_content", true,
                        "outputFormat", "json",
                        "temperature", 0.2,
                        "summaryFormat", "document_sheet_row_v1",
                        "resultMode", "single_aggregated"))
                .build();
        NodeDefinition sheets = NodeDefinition.builder()
                .id("node_sheets_end").category("service").type("google_sheets")
                .role("end").dataType("SPREADSHEET_DATA")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_sheets",
                        "spreadsheet_id", "",
                        "write_mode", "append",
                        "sheet_name", "Sheet1"))
                .build();

        return Template.builder()
                .name("문서 요약 결과를 Google Sheets에 저장")
                .description("지정한 Google Drive 폴더의 문서를 읽어 요약한 뒤 Google Sheets에 기록합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, llm, sheets))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_llm").source("node_drive_start").target("node_llm_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_sheets").source("node_llm_summary").target("node_sheets_end").build()))
                .requiredServices(List.of("google_drive", "google_sheets"))
                .isSystem(true)
                .build();
    }

    private NodeDefinition buildFolderDocumentSourceNode() {
        return NodeDefinition.builder()
                .id("node_drive_start").category("service").type("google_drive")
                .role("start").outputDataType("SINGLE_FILE")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_drive",
                        "source_mode", "folder_new_file",
                        "target", "",
                        "target_label", "",
                        "target_meta", Map.of("pickerType", "folder")))
                .build();
    }

    private Template buildDriveFolderAllFilesGmailTemplate() {
        NodeDefinition drive = buildDriveFileListSourceNode("folder_all_files", "manual");
        NodeDefinition loop = buildDriveFileLoopNode();
        NodeDefinition llm = buildDriveFileSummaryNode();
        NodeDefinition gmail = buildGmailSinkNode("Drive 폴더 파일 요약");

        return Template.builder()
                .name("Drive 폴더 전체 파일 요약 Gmail 발송")
                .description("지정한 Google Drive 폴더의 전체 파일을 하나씩 요약해 Gmail로 발송합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, loop, llm, gmail))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_loop").source("node_drive_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_drive_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_drive_summary").target("node_gmail_end").build()))
                .requiredServices(List.of("google_drive", "gmail"))
                .isSystem(true)
                .build();
    }

    private Template buildDriveFolderAllFilesNotionTemplate() {
        NodeDefinition drive = buildDriveFileListSourceNode("folder_all_files", "manual");
        NodeDefinition loop = buildDriveFileLoopNode();
        NodeDefinition llm = buildDriveFileSummaryNode();
        NodeDefinition notion = buildNotionSinkNode("Drive 폴더 파일 요약 - {{date}}");

        return Template.builder()
                .name("Drive 폴더 전체 파일 요약 Notion 저장")
                .description("지정한 Google Drive 폴더의 전체 파일을 하나씩 요약해 Notion에 저장합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, loop, llm, notion))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_loop").source("node_drive_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_drive_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_notion").source("node_llm_drive_summary").target("node_notion_end").build()))
                .requiredServices(List.of("google_drive", "notion"))
                .isSystem(true)
                .build();
    }

    private Template buildDriveNewFileGmailTemplate() {
        NodeDefinition drive = buildDriveFileListSourceNode("folder_new_file", "event");
        NodeDefinition loop = buildDriveFileLoopNode();
        NodeDefinition llm = buildDriveFileSummaryNode();
        NodeDefinition gmail = buildGmailSinkNode("Drive 신규 파일 요약");

        return Template.builder()
                .name("Drive 신규 파일 요약 Gmail 발송")
                .description("지정한 Google Drive 폴더에 새 파일이 들어오면 하나씩 요약해 Gmail로 발송합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, loop, llm, gmail))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_loop").source("node_drive_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_drive_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_drive_summary").target("node_gmail_end").build()))
                .requiredServices(List.of("google_drive", "gmail"))
                .isSystem(true)
                .build();
    }

    private Template buildDriveNewFileDiscordTemplate() {
        NodeDefinition drive = buildDriveFileListSourceNode("folder_new_file", "event");
        NodeDefinition loop = buildDriveFileLoopNode();
        NodeDefinition llm = buildDriveFileSummaryNode();
        NodeDefinition discord = buildDiscordSinkNode();

        return Template.builder()
                .name("Drive 신규 파일 Discord 알림")
                .description("지정한 Google Drive 폴더에 새 파일이 들어오면 하나씩 요약해 Discord로 전달합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, loop, llm, discord))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_loop").source("node_drive_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_drive_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_discord").source("node_llm_drive_summary").target("node_discord_end").build()))
                .requiredServices(List.of("google_drive", "discord"))
                .isSystem(true)
                .build();
    }

    private Template buildDriveFolderMetadataSheetsTemplate() {
        NodeDefinition drive = buildDriveFileListSourceNode("folder_all_files", "manual");
        NodeDefinition loop = buildDriveFileLoopNode();
        NodeDefinition filter = buildDriveMetadataTableNode();
        NodeDefinition sheets = buildSheetsSinkNode();

        return Template.builder()
                .name("Drive 폴더 파일 메타데이터 Sheets 기록")
                .description("지정한 Google Drive 폴더의 파일 정보를 하나씩 표 형태로 정리해 Google Sheets에 기록합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, loop, filter, sheets))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_loop").source("node_drive_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_filter").source("node_loop").target("node_filter_metadata").build(),
                        EdgeDefinition.builder().id("edge_filter_to_sheets").source("node_filter_metadata").target("node_sheets_end").build()))
                .requiredServices(List.of("google_drive", "google_sheets"))
                .isSystem(true)
                .build();
    }

    private Template buildDriveNewFileMetadataSheetsTemplate() {
        NodeDefinition drive = buildDriveFileListSourceNode("folder_new_file", "event");
        NodeDefinition loop = buildDriveFileLoopNode();
        NodeDefinition filter = buildDriveMetadataTableNode();
        NodeDefinition sheets = buildSheetsSinkNode();

        return Template.builder()
                .name("Drive 신규 파일 메타데이터 Sheets 기록")
                .description("지정한 Google Drive 폴더의 새 파일 정보를 하나씩 표 형태로 정리해 Google Sheets에 기록합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, loop, filter, sheets))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_loop").source("node_drive_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_filter").source("node_loop").target("node_filter_metadata").build(),
                        EdgeDefinition.builder().id("edge_filter_to_sheets").source("node_filter_metadata").target("node_sheets_end").build()))
                .requiredServices(List.of("google_drive", "google_sheets"))
                .isSystem(true)
                .build();
    }

    private NodeDefinition buildDriveFileListSourceNode(String sourceMode, String triggerKind) {
        return NodeDefinition.builder()
                .id("node_drive_start").category("service").type("google_drive")
                .role("start").outputDataType("FILE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_drive",
                        "source_mode", sourceMode,
                        "target", "",
                        "target_label", "",
                        "target_meta", Map.of("pickerType", "folder"),
                        "trigger_kind", triggerKind,
                        "maxResults", 100))
                .build();
    }

    private NodeDefinition buildDriveFileLoopNode() {
        return NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("FILE_LIST").outputDataType("SINGLE_FILE")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "targetField", "items",
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
    }

    private NodeDefinition buildDriveFileSummaryNode() {
        return NodeDefinition.builder()
                .id("node_llm_drive_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_FILE").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 Google Drive 파일 내용을 요약해줘. 파일명, 핵심 요약 2~3문장, 주요 포인트 3개 이내, 원문 링크를 포함해 후속 알림이나 기록에 바로 사용할 수 있게 정리해줘. 파일 본문을 읽을 수 없으면 사용 가능한 메타데이터 중심으로 정리해줘.",
                        "model", "gpt-4.1-mini",
                        "action", "summarize",
                        "requires_content", true,
                        "outputFormat", "text",
                        "temperature", 0.3,
                        "summaryFormat", "drive_file_digest_v1",
                        "resultMode", "single_aggregated"))
                .build();
    }

    private NodeDefinition buildDriveMetadataTableNode() {
        return NodeDefinition.builder()
                .id("node_filter_metadata").category("logic").type("DATA_FILTER")
                .role("middle").dataType("SINGLE_FILE").outputDataType("SPREADSHEET_DATA")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "choiceActionId", "filter_metadata_table",
                        "choiceNodeType", "DATA_FILTER",
                        "choiceSelections", Map.of(
                                "follow_up", List.of("filename", "link", "upload_time", "file_size"))))
                .build();
    }

    private NodeDefinition buildGmailSinkNode(String subject) {
        return NodeDefinition.builder()
                .id("node_gmail_end").category("service").type("gmail")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "gmail",
                        "to", "",
                        "subject", subject,
                        "action", "send"))
                .build();
    }

    private NodeDefinition buildNotionSinkNode(String titleTemplate) {
        return NodeDefinition.builder()
                .id("node_notion_end").category("service").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "target_type", "page",
                        "target_id", "",
                        "title_template", titleTemplate))
                .build();
    }

    private NodeDefinition buildDiscordSinkNode() {
        return NodeDefinition.builder()
                .id("node_discord_end").category("service").type("discord")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "discord",
                        "webhook_url", "",
                        "message_template", "",
                        "username", "Flowify"))
                .build();
    }

    private NodeDefinition buildSheetsSinkNode() {
        return NodeDefinition.builder()
                .id("node_sheets_end").category("service").type("google_sheets")
                .role("end").dataType("SPREADSHEET_DATA")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_sheets",
                        "spreadsheet_id", "",
                        "write_mode", "append_rows",
                        "sheet_name", "Sheet1"))
                .build();
    }

    private NodeDefinition buildDriveTextSinkNode(String filenameTemplate) {
        return NodeDefinition.builder()
                .id("node_drive_end").category("service").type("google_drive")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_drive",
                        "folder_id", "",
                        "filename_template", filenameTemplate))
                .build();
    }

    private NodeDefinition buildDriveFileListSinkNode() {
        return NodeDefinition.builder()
                .id("node_drive_end").category("service").type("google_drive")
                .role("end").dataType("FILE_LIST")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_drive",
                        "folder_id", "",
                        "filename_template", "{{filename_stem}}"))
                .build();
    }

    // ── 파일 업로드 자동 공유 템플릿 ──

    private Template buildDriveUploadGmailTemplate() {
        NodeDefinition googleDrive = NodeDefinition.builder()
                .id("node_drive_start").category("service").type("google_drive")
                .role("start").outputDataType("SINGLE_FILE")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_drive",
                        "source_mode", "folder_new_file",
                        "target", "",
                        "target_label", "",
                        "target_meta", Map.of("pickerType", "folder")))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_email").category("ai").type("llm")
                .role("middle").dataType("SINGLE_FILE").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 파일 내용을 바탕으로 이메일 알림 본문을 작성해줘. 파일명을 반드시 포함하고, 핵심 요약과 확인할 포인트를 짧게 정리해줘. 원문 링크가 있으면 함께 언급하고, 본문이 비어 있거나 비텍스트 파일이면 그 사실을 명시해줘. 메일 본문으로 바로 사용할 수 있는 형태만 출력해줘.",
                        "model", "gpt-4.1-mini",
                        "action", "summarize",
                        "requires_content", true,
                        "outputFormat", "text",
                        "temperature", 0.2))
                .build();
        NodeDefinition gmail = NodeDefinition.builder()
                .id("node_gmail_end").category("service").type("gmail")
                .role("end").dataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "gmail",
                        "to", "",
                        "subject", "새 파일 업로드 알림",
                        "action", "send"))
                .build();

        return Template.builder()
                .name("새 파일 업로드 알림 메일 발송")
                .description("지정한 Google Drive 폴더의 새 파일 정보를 정리해 이메일 알림을 발송합니다.")
                .category("file_upload_auto_share")
                .icon("google_drive")
                .nodes(List.of(googleDrive, llm, gmail))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_llm").source("node_drive_start").target("node_llm_email").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_email").target("node_gmail_end").build()))
                .requiredServices(List.of("google_drive", "gmail"))
                .isSystem(true)
                .build();
    }

    private Template buildDriveUploadNotionTemplate() {
        NodeDefinition googleDrive = NodeDefinition.builder()
                .id("node_drive_start").category("service").type("google_drive")
                .role("start").outputDataType("SINGLE_FILE")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_drive",
                        "source_mode", "folder_new_file",
                        "target", "",
                        "target_label", "",
                        "target_meta", Map.of("pickerType", "folder")))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_notion").category("ai").type("llm")
                .role("middle").dataType("SINGLE_FILE").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 파일 내용을 바탕으로 Notion 기록용 요약을 작성해줘. 파일명을 반드시 포함하고, 핵심 내용과 주요 포인트를 구분해서 정리해줘. 원문 링크가 있으면 함께 적어주고, 본문이 비어 있거나 비텍스트 파일이면 메타데이터 중심 기록이라는 점을 명시해줘. 불필요한 서론 없이 바로 기록용 본문만 출력해줘.",
                        "model", "gpt-4.1-mini",
                        "action", "summarize",
                        "requires_content", true,
                        "outputFormat", "text",
                        "temperature", 0.2))
                .build();
        NodeDefinition notion = NodeDefinition.builder()
                .id("node_notion_end").category("service").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "target_type", "page",
                        "target_id", "",
                        "title_template", "업로드 파일 기록 - {{filename}}"))
                .build();

        return Template.builder()
                .name("새 파일 업로드 후 Notion 기록")
                .description("지정한 Google Drive 폴더의 새 파일 정보를 정리해 Notion 페이지에 기록합니다.")
                .category("file_upload_auto_share")
                .icon("google_drive")
                .nodes(List.of(googleDrive, llm, notion))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_llm").source("node_drive_start").target("node_llm_notion").build(),
                        EdgeDefinition.builder().id("edge_llm_to_notion").source("node_llm_notion").target("node_notion_end").build()))
                .requiredServices(List.of("google_drive", "notion"))
                .isSystem(true)
                .build();
    }

    private Template buildGithubDiscordTemplate() {
        NodeDefinition github = NodeDefinition.builder()
                .id("node_github_start").category("service").type("github")
                .role("start").outputDataType("API_RESPONSE")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "github",
                        "source_mode", "new_pr",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "event",
                        "include_drafts", true,
                        "backfill_count", 5))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_summary").category("ai").type("llm")
                .role("middle").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "",
                        "outputFormat", "text",
                        "temperature", 0.7,
                        "choiceActionId", "ai_summarize",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "brief")))
                .build();
        NodeDefinition discord = NodeDefinition.builder()
                .id("node_discord_end").category("service").type("discord")
                .role("end").dataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "discord",
                        "webhook_url", "",
                        "message_template", "",
                        "username", "Flowify"))
                .build();

        return Template.builder()
                .name("GitHub 새 PR 요약 후 Discord 알림")
                .description("새로 생성된 GitHub PR을 감지해 핵심 변경 내용을 요약하고 Discord로 전달합니다.")
                .category("communication")
                .icon("github")
                .nodes(List.of(github, llm, discord))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_github_to_llm").source("node_github_start").target("node_llm_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_discord").source("node_llm_summary").target("node_discord_end").build()))
                .requiredServices(List.of("github", "discord"))
                .isSystem(true)
                .build();
    }

    private Template buildGithubSheetsTemplate() {
        NodeDefinition github = NodeDefinition.builder()
                .id("node_github_start").category("service").type("github")
                .role("start").outputDataType("API_RESPONSE")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "github",
                        "source_mode", "new_pr",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "event",
                        "include_drafts", true,
                        "backfill_count", 5))
                .build();
        NodeDefinition filter = NodeDefinition.builder()
                .id("node_filter_fields").category("control").type("filter")
                .role("middle").dataType("API_RESPONSE").outputDataType("SPREADSHEET_DATA")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "removeDuplicates", false,
                        "choiceActionId", "filter_fields",
                        "choiceNodeType", "DATA_FILTER",
                        "choiceSelections", Map.of("follow_up", "url")))
                .build();
        NodeDefinition sheets = NodeDefinition.builder()
                .id("node_sheets_end").category("service").type("google_sheets")
                .role("end").dataType("SPREADSHEET_DATA")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_sheets",
                        "write_mode", "append_rows",
                        "spreadsheet_id", "",
                        "sheet_name", "Sheet1"))
                .build();

        return Template.builder()
                .name("GitHub 새 PR 링크를 Google Sheets에 저장")
                .description("새로 생성된 GitHub PR 링크를 Google Sheets에 한 줄씩 기록합니다.")
                .category("spreadsheet")
                .icon("github")
                .nodes(List.of(github, filter, sheets))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_github_to_filter").source("node_github_start").target("node_filter_fields").build(),
                        EdgeDefinition.builder().id("edge_filter_to_sheets").source("node_filter_fields").target("node_sheets_end").build()))
                .requiredServices(List.of("github", "google_sheets"))
                .isSystem(true)
                .build();
    }

    private Template buildNewsSearchNotionTemplate() {
        NodeDefinition news = NodeDefinition.builder()
                .id("node_news_start").category("service").type("naver_news")
                .role("start").outputDataType("ARTICLE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "naver_news",
                        "source_mode", "article_search",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "manual",
                        "maxResults", 5))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("ARTICLE_LIST").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_news").category("ai").type("llm")
                .role("middle").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "",
                        "outputFormat", "text",
                        "temperature", 0.7,
                        "choiceActionId", "ai_refine",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "newsletter")))
                .build();
        NodeDefinition notion = NodeDefinition.builder()
                .id("node_notion_end").category("service").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "target_type", "page",
                        "target_id", "",
                        "title_template", "뉴스 브리핑 - {{date}}"))
                .build();

        return Template.builder()
                .name("뉴스 검색 결과 요약 후 Notion 저장")
                .description("검색한 뉴스 목록을 요약해 Notion 페이지에 브리핑 형식으로 저장합니다.")
                .category("web_crawl")
                .icon("naver_news")
                .nodes(List.of(news, loop, llm, notion))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_news_to_loop").source("node_news_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_news").build(),
                        EdgeDefinition.builder().id("edge_llm_to_notion").source("node_llm_news").target("node_notion_end").build()))
                .requiredServices(List.of("naver_news", "notion"))
                .isSystem(true)
                .build();
    }

    private Template buildNewsSearchGmailTemplate() {
        NodeDefinition news = NodeDefinition.builder()
                .id("node_news_start").category("service").type("naver_news")
                .role("start").outputDataType("ARTICLE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "naver_news",
                        "source_mode", "article_search",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "manual",
                        "maxResults", 5))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("ARTICLE_LIST").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_news").category("ai").type("llm")
                .role("middle").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "",
                        "outputFormat", "text",
                        "temperature", 0.7,
                        "choiceActionId", "ai_refine",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "newsletter")))
                .build();
        NodeDefinition gmail = NodeDefinition.builder()
                .id("node_gmail_end").category("service").type("gmail")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "gmail",
                        "to", "",
                        "subject", "뉴스 브리핑",
                        "action", "send"))
                .build();

        return Template.builder()
                .name("네이버 뉴스 요약 Gmail 발송")
                .description("네이버 뉴스 검색 결과를 하나씩 요약해 Gmail로 발송합니다.")
                .category("web_crawl")
                .icon("naver_news")
                .nodes(List.of(news, loop, llm, gmail))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_news_to_loop").source("node_news_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_news").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_news").target("node_gmail_end").build()))
                .requiredServices(List.of("naver_news", "gmail"))
                .isSystem(true)
                .build();
    }

    private Template buildNewsSearchDiscordTemplate() {
        NodeDefinition news = NodeDefinition.builder()
                .id("node_news_start").category("service").type("naver_news")
                .role("start").outputDataType("ARTICLE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "naver_news",
                        "source_mode", "article_search",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "manual",
                        "maxResults", 5))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("ARTICLE_LIST").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_news").category("ai").type("llm")
                .role("middle").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "",
                        "outputFormat", "text",
                        "temperature", 0.7,
                        "choiceActionId", "ai_refine",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "newsletter")))
                .build();
        NodeDefinition discord = NodeDefinition.builder()
                .id("node_discord_end").category("service").type("discord")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "discord",
                        "webhook_url", "",
                        "message_template", "",
                        "username", "Flowify"))
                .build();

        return Template.builder()
                .name("네이버 뉴스 요약 Discord 알림")
                .description("네이버 뉴스 검색 결과를 하나씩 요약해 Discord로 전달합니다.")
                .category("web_crawl")
                .icon("naver_news")
                .nodes(List.of(news, loop, llm, discord))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_news_to_loop").source("node_news_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_news").build(),
                        EdgeDefinition.builder().id("edge_llm_to_discord").source("node_llm_news").target("node_discord_end").build()))
                .requiredServices(List.of("naver_news", "discord"))
                .isSystem(true)
                .build();
    }

    private Template buildNewsSearchDriveTemplate() {
        NodeDefinition news = NodeDefinition.builder()
                .id("node_news_start").category("service").type("naver_news")
                .role("start").outputDataType("ARTICLE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "naver_news",
                        "source_mode", "article_search",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "manual",
                        "maxResults", 5))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("ARTICLE_LIST").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_news").category("ai").type("llm")
                .role("middle").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "",
                        "outputFormat", "text",
                        "temperature", 0.7,
                        "choiceActionId", "ai_refine",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "newsletter")))
                .build();
        NodeDefinition drive = NodeDefinition.builder()
                .id("node_drive_end").category("service").type("google_drive")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "google_drive",
                        "folder_id", "",
                        "filename_template", "naver_news_summary_{{date}}"))
                .build();

        return Template.builder()
                .name("네이버 뉴스 요약 Google Drive 저장")
                .description("네이버 뉴스 검색 결과를 하나씩 요약해 Google Drive에 텍스트 파일로 저장합니다.")
                .category("web_crawl")
                .icon("naver_news")
                .nodes(List.of(news, loop, llm, drive))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_news_to_loop").source("node_news_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_news").build(),
                        EdgeDefinition.builder().id("edge_llm_to_drive").source("node_llm_news").target("node_drive_end").build()))
                .requiredServices(List.of("naver_news", "google_drive"))
                .isSystem(true)
                .build();
    }

    private Template buildSeBoardNotionTemplate() {
        NodeDefinition seboard = NodeDefinition.builder()
                .id("node_seboard_start").category("service").type("web_news")
                .role("start").outputDataType("ARTICLE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "web_news",
                        "source_mode", "seboard_posts",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "manual",
                        "maxResults", 5))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("ARTICLE_LIST").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_seboard").category("ai").type("llm")
                .role("middle").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "",
                        "outputFormat", "text",
                        "temperature", 0.7,
                        "choiceActionId", "ai_refine",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "newsletter")))
                .build();
        NodeDefinition notion = NodeDefinition.builder()
                .id("node_notion_end").category("service").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "target_type", "page",
                        "target_id", "",
                        "title_template", "SE Board 게시글 요약 - {{date}}"))
                .build();

        return Template.builder()
                .name("SE Board 게시글 요약 후 Notion 저장")
                .description("SE Board 게시글을 모아 핵심 내용을 정리하고 Notion 페이지에 저장합니다.")
                .category("web_scraping")
                .icon("seboard")
                .nodes(List.of(seboard, loop, llm, notion))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_seboard_to_loop").source("node_seboard_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_seboard").build(),
                        EdgeDefinition.builder().id("edge_llm_to_notion").source("node_llm_seboard").target("node_notion_end").build()))
                .requiredServices(List.of("web_news", "notion"))
                .isSystem(true)
                .build();
    }

    private Template buildSeBoardDiscordTemplate() {
        NodeDefinition seboard = NodeDefinition.builder()
                .id("node_seboard_start").category("service").type("web_news")
                .role("start").outputDataType("ARTICLE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "web_news",
                        "source_mode", "seboard_new_posts",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "event",
                        "maxResults", 5))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("ARTICLE_LIST").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_seboard").category("ai").type("llm")
                .role("middle").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "",
                        "outputFormat", "text",
                        "temperature", 0.7,
                        "choiceActionId", "ai_refine",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "newsletter")))
                .build();
        NodeDefinition discord = NodeDefinition.builder()
                .id("node_discord_end").category("service").type("discord")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "discord",
                        "webhook_url", "",
                        "message_template", "",
                        "username", "Flowify"))
                .build();

        return Template.builder()
                .name("SE Board 새 글 Discord 알림")
                .description("SE Board에 새 글이 올라오면 하나씩 요약해 Discord로 전달합니다.")
                .category("web_scraping")
                .icon("seboard")
                .nodes(List.of(seboard, loop, llm, discord))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_seboard_to_loop").source("node_seboard_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_seboard").build(),
                        EdgeDefinition.builder().id("edge_llm_to_discord").source("node_llm_seboard").target("node_discord_end").build()))
                .requiredServices(List.of("web_news", "discord"))
                .isSystem(true)
                .build();
    }

    private Template buildSeBoardGmailTemplate() {
        NodeDefinition seboard = NodeDefinition.builder()
                .id("node_seboard_start").category("service").type("web_news")
                .role("start").outputDataType("ARTICLE_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "web_news",
                        "source_mode", "seboard_new_posts",
                        "target", "",
                        "target_label", "",
                        "trigger_kind", "event",
                        "maxResults", 5))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("ARTICLE_LIST").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "maxIterations", 100,
                        "timeout", 300,
                        "choiceNodeType", "LOOP"))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_seboard").category("ai").type("llm")
                .role("middle").dataType("TEXT").outputDataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "",
                        "outputFormat", "text",
                        "temperature", 0.7,
                        "choiceActionId", "ai_refine",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "newsletter")))
                .build();
        NodeDefinition gmail = NodeDefinition.builder()
                .id("node_gmail_end").category("service").type("gmail")
                .role("end").dataType("TEXT")
                .position(new Position(800, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "gmail",
                        "to", "",
                        "subject", "SE Board 새 글 알림",
                        "action", "send"))
                .build();

        return Template.builder()
                .name("SE Board 새 글 Gmail 발송")
                .description("SE Board에 새 글이 올라오면 하나씩 요약해 Gmail로 발송합니다.")
                .category("web_scraping")
                .icon("seboard")
                .nodes(List.of(seboard, loop, llm, gmail))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_seboard_to_loop").source("node_seboard_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_seboard").build(),
                        EdgeDefinition.builder().id("edge_llm_to_gmail").source("node_llm_seboard").target("node_gmail_end").build()))
                .requiredServices(List.of("web_news", "gmail"))
                .isSystem(true)
                .build();
    }
}
