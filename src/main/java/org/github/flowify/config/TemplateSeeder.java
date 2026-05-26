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
            "SE Board 게시글 요약 후 Notion 저장",
            "SE Board 게시글 요약 후 Discord 알림",
            "뉴스 검색 결과 요약 후 Notion 저장",
            "뉴스 검색 결과 요약 후 Gmail 전달",
            "뉴스 검색 결과 요약 후 Discord 알림",
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
        if (upsertTemplate(
                buildSeBoardNotionTemplate(),
                "SE Board 공지 요약 후 Notion 저장")) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildSeBoardDiscordTemplate())) {
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
        if (upsertTemplate(buildNewsSearchGmailTemplate())) {
            updated++;
        } else {
            created++;
        }
        if (upsertTemplate(buildNewsSearchDiscordTemplate())) {
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
                .name("뉴스 검색 결과 요약 후 Gmail 전달")
                .description("검색한 뉴스 목록을 요약해 읽기 쉬운 브리핑 형식으로 이메일로 전달합니다.")
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
                .name("뉴스 검색 결과 요약 후 Discord 알림")
                .description("검색한 뉴스 목록을 요약해 읽기 쉬운 브리핑 형식으로 Discord로 전달합니다.")
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
                .name("SE Board 게시글 요약 후 Discord 알림")
                .description("SE Board 게시글을 모아 핵심 내용을 요약하고 Discord로 전달합니다.")
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
}
