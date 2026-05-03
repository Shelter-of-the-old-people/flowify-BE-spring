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

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateSeeder implements CommandLineRunner {

    private final TemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        List<Template> templates = List.of(
                buildStudyNoteTemplate(),
                buildMeetingMinutesTemplate(),
                buildNewsCrawlTemplate(),
                buildSheetReportTemplate(),
                buildUnreadMailSlackTemplate(),
                buildImportantMailNotionTemplate(),
                buildImportantMailTodosNotionTemplate()
        );

        int created = 0;
        int updated = 0;
        for (Template seedTemplate : templates) {
            var existing = templateRepository.findByNameAndIsSystem(seedTemplate.getName(), true);
            if (existing.isPresent()) {
                Template ex = existing.get();
                seedTemplate.setId(ex.getId());
                seedTemplate.setUseCount(ex.getUseCount());
                seedTemplate.setCreatedAt(ex.getCreatedAt());
                templateRepository.save(seedTemplate);
                updated++;
            } else {
                templateRepository.save(seedTemplate);
                created++;
            }
        }

        log.info("시스템 템플릿 시드 완료: 신규 {}개, 갱신 {}개", created, updated);
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

    // ── 메일 요약/전달 템플릿 3종 ──

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
                        "target_meta", Map.of("systemLabel", true)))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("EMAIL_LIST").outputDataType("SINGLE_EMAIL")
                .position(new Position(300, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "targetField", "items",
                        "maxIterations", 100,
                        "timeout", 300))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_EMAIL").outputDataType("TEXT")
                .position(new Position(520, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "아래 메일의 핵심 내용을 3줄로 요약해줘. 발신자, 제목, 주요 내용을 포함해줘.",
                        "model", "gpt-4.1-mini",
                        "outputFormat", "text",
                        "temperature", 0.3))
                .build();
        NodeDefinition slack = NodeDefinition.builder()
                .id("node_slack_end").category("service").type("slack")
                .role("end").dataType("TEXT")
                .position(new Position(740, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "slack",
                        "message_format", "markdown",
                        "header", "메일 요약"))
                .build();

        return Template.builder()
                .name("읽지 않은 메일 요약 후 Slack 공유")
                .description("읽지 않은 메일을 하나씩 요약해 Slack 채널로 공유합니다.")
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
                        "target_meta", Map.of("systemLabel", true)))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("EMAIL_LIST").outputDataType("SINGLE_EMAIL")
                .position(new Position(300, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "targetField", "items",
                        "maxIterations", 100,
                        "timeout", 300))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_summary").category("ai").type("llm")
                .role("middle").dataType("SINGLE_EMAIL").outputDataType("TEXT")
                .position(new Position(520, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "아래 메일의 핵심 내용을 3줄로 요약해줘. 발신자, 제목, 주요 내용을 포함해줘.",
                        "model", "gpt-4.1-mini",
                        "outputFormat", "text",
                        "temperature", 0.3))
                .build();
        NodeDefinition notion = NodeDefinition.builder()
                .id("node_notion_end").category("service").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(740, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "title_template", "메일 요약 - {{date}}"))
                .build();

        return Template.builder()
                .name("중요 메일 요약 후 Notion 저장")
                .description("중요 메일을 하나씩 요약해 Notion 페이지 또는 데이터베이스에 저장합니다.")
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
                        "target_meta", Map.of("systemLabel", true)))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop").category("control").type("loop")
                .role("middle").dataType("EMAIL_LIST").outputDataType("SINGLE_EMAIL")
                .position(new Position(300, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "targetField", "items",
                        "maxIterations", 100,
                        "timeout", 300))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_todos").category("ai").type("llm")
                .role("middle").dataType("SINGLE_EMAIL").outputDataType("TEXT")
                .position(new Position(520, 180))
                .config(Map.of(
                        "isConfigured", true,
                        "prompt", "아래 메일에서 사용자가 해야 할 일을 추출해줘. 각 항목은 할 일, 마감일, 관련 발신자를 포함해줘.",
                        "model", "gpt-4.1-mini",
                        "outputFormat", "text",
                        "temperature", 0.2))
                .build();
        NodeDefinition notion = NodeDefinition.builder()
                .id("node_notion_end").category("service").type("notion")
                .role("end").dataType("TEXT")
                .position(new Position(740, 180))
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "title_template", "할 일 추출 - {{date}}"))
                .build();

        return Template.builder()
                .name("중요 메일 할 일 추출 후 Notion 저장")
                .description("중요 메일에서 해야 할 일을 추출해 Notion에 정리합니다.")
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
}
