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

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateSeeder implements CommandLineRunner {

    private final TemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        int created = 0;
        int updated = 0;

        if (upsertTemplate(buildStudyNoteTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildMeetingMinutesTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildNewsCrawlTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildSheetReportTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildUnreadMailSlackTemplate(),
                "읽지 않은 메일 요약 후 Slack 공유")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildImportantMailNotionTemplate(),
                "중요 메일 요약 후 Notion 저장")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(
                buildImportantMailTodosNotionTemplate(),
                "중요 메일 할 일 추출 후 Notion 저장")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildFolderDocumentSlackTemplate())) {
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

        log.info("시스템 템플릿 시드 완료: 신규 {}개, 갱신 {}개", created, updated);
    }

    private boolean upsertTemplate(Template seedTemplate, String... legacyNames) {
        Optional<Template> existing = findExistingSystemTemplate(seedTemplate.getName(), legacyNames);
        if (existing.isPresent()) {
            Template current = existing.get();
            seedTemplate.setId(current.getId());
            seedTemplate.setUseCount(current.getUseCount());
            seedTemplate.setCreatedAt(current.getCreatedAt());
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

    private Template buildMeetingMinutesTemplate() {
        NodeDefinition input = NodeDefinition.builder()
                .id("node_1").category("storage").type("google_drive")
                .role("start").dataType("SINGLE_FILE").outputDataType("SINGLE_FILE")
                .position(new Position(80, 180))
                .build();
        NodeDefinition ai = NodeDefinition.builder()
                .id("node_2").category("ai").type("AI")
                .role("middle").dataType("SINGLE_FILE").outputDataType("TEXT")
                .position(new Position(300, 180))
                .build();
        NodeDefinition output = NodeDefinition.builder()
                .id("node_3").category("service").type("slack")
                .role("end").dataType("TEXT")
                .position(new Position(520, 180))
                .build();

        return Template.builder()
                .name("회의록 요약 및 공유")
                .description("회의 녹취를 AI로 정리하여 Slack으로 전송합니다.")
                .category("communication")
                .icon("message")
                .nodes(List.of(input, ai, output))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_1_2").source("node_1").target("node_2").build(),
                        EdgeDefinition.builder().id("edge_2_3").source("node_2").target("node_3").build()))
                .requiredServices(List.of("google_drive", "slack"))
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

    private Template buildUnreadMailSlackTemplate() {
        NodeDefinition gmail = NodeDefinition.builder()
                .id("node_gmail_start").category("service").type("gmail")
                .role("start").outputDataType("EMAIL_LIST")
                .position(new Position(80, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "service", "gmail",
                        "source_mode", "label_emails",
                        "target", "UNREAD",
                        "target_label", "읽지 않은 메일",
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
                        "prompt", "입력된 읽지 않은 메일 목록의 모든 메일을 빠짐없이 포함해 Slack 공유용 요약을 작성해줘. 각 메일은 번호를 붙이고, 발신자/제목/핵심 내용/액션 필요 여부 형식으로 정리해줘.",
                        "model", "gpt-4.1-mini",
                        "outputFormat", "text",
                        "temperature", 0.3,
                        "summaryFormat", "mail_digest_v1",
                        "resultMode", "single_aggregated"))
                .build();
        NodeDefinition slack = NodeDefinition.builder()
                .id("node_slack_end").category("service").type("slack")
                .role("end").dataType("TEXT")
                .position(new Position(740, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "slack",
                        "channel", "",
                        "message_format", "markdown",
                        "header", "메일 요약"))
                .build();

        return Template.builder()
                .name("읽지 않은 메일 목록 요약 후 Slack 공유")
                .description("읽지 않은 메일 목록을 정해진 형식으로 요약해 Slack 채널에 공유합니다.")
                .category("mail_summary_forward")
                .icon("gmail")
                .nodes(List.of(gmail, loop, llm, slack))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_gmail_to_loop").source("node_gmail_start").target("node_loop").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop").target("node_llm_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_slack").source("node_llm_summary").target("node_slack_end").build()))
                .requiredServices(List.of("gmail", "slack"))
                .isSystem(true)
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

    private Template buildFolderDocumentSlackTemplate() {
        NodeDefinition drive = buildFolderDocumentSourceNode();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_FILE").outputDataType("TEXT")
                .position(new Position(320, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "입력된 문서 내용을 바탕으로 Slack 공유용 요약을 작성해줘. 문서명, 핵심 요약 2~3문장, 주요 포인트 3개 이내를 간결하게 정리해줘. 불필요한 서론 없이 바로 결과만 작성해줘.",
                        "model", "gpt-4.1-mini",
                        "outputFormat", "text",
                        "temperature", 0.3,
                        "summaryFormat", "document_digest_v1",
                        "resultMode", "single_aggregated"))
                .build();
        NodeDefinition slack = NodeDefinition.builder()
                .id("node_slack_end").category("service").type("slack")
                .role("end").dataType("TEXT")
                .position(new Position(560, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "slack",
                        "channel", "",
                        "message_format", "markdown",
                        "header", "문서 요약"))
                .build();

        return Template.builder()
                .name("신규 문서 요약 후 Slack 공유")
                .description("지정한 Google Drive 폴더의 문서를 읽어 핵심 내용을 요약하고 Slack 채널에 공유합니다.")
                .category("folder_document_summary")
                .icon("google_drive")
                .nodes(List.of(drive, llm, slack))
                .edges(List.of(
                        EdgeDefinition.builder().id("edge_drive_to_llm").source("node_drive_start").target("node_llm_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_slack").source("node_llm_summary").target("node_slack_end").build()))
                .requiredServices(List.of("google_drive", "slack"))
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
}

