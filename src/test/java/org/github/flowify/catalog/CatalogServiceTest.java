package org.github.flowify.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogServiceTest {

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(new ObjectMapper());
        ReflectionTestUtils.invokeMethod(catalogService, "loadCatalogs");
    }

    @Test
    @DisplayName("Discord sink catalog는 Webhook 계약을 로딩한다")
    void discordSinkCatalog_loadsWebhookContract() {
        SinkService discord = catalogService.findSinkService("discord");

        assertThat(discord.getLabel()).isEqualTo("Discord");
        assertThat(discord.isAuthRequired()).isFalse();
        assertThat(discord.getAcceptedInputTypes()).containsExactly(
                "TEXT",
                "SINGLE_ANNOUNCEMENT",
                "SINGLE_EMAIL"
        );
        assertThat(catalogService.getSinkRequiredFields("discord"))
                .containsExactly("webhook_url");

        Map<String, Object> schema = catalogService.getSinkSchema("discord", "TEXT");
        List<Map<String, Object>> fields = fieldsOf(schema);

        assertThat(fields)
                .anySatisfy(field -> assertThat(field)
                        .containsEntry("key", "webhook_url")
                        .containsEntry("type", "secret_text")
                        .containsEntry("required", true))
                .anySatisfy(field -> assertThat(field)
                        .containsEntry("key", "message_template")
                        .containsEntry("type", "textarea")
                        .containsEntry("required", false));
    }

    @Test
    @DisplayName("Discord sink는 TEXT 외 입력 타입을 허용하지 않는다")
    void discordSinkCatalog_rejectsUnsupportedInputType() {
        assertThatThrownBy(() -> catalogService.getSinkSchema("discord", "FILE_LIST"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATALOG_INVALID_INPUT_TYPE);
    }

    @Test
    @DisplayName("Gmail sink catalog는 텍스트 전달 방식 설정을 선택 항목으로 제공한다")
    void gmailSinkCatalog_loadsTextDeliveryModeContract() {
        SinkService gmail = catalogService.findSinkService("gmail");

        assertThat(gmail.getAcceptedInputTypes())
                .containsExactly(
                        "TEXT",
                        "SINGLE_FILE",
                        "FILE_LIST",
                        "SINGLE_ANNOUNCEMENT",
                        "SINGLE_EMAIL"
                );
        assertThat(catalogService.getSinkRequiredFields("gmail"))
                .containsExactly("to", "subject", "action");

        Map<String, Object> schema = catalogService.getSinkSchema("gmail", "TEXT");
        List<Map<String, Object>> fields = fieldsOf(schema);

        assertThat(fields)
                .anySatisfy(field -> assertThat(field)
                        .containsEntry("key", "text_delivery_mode")
                        .containsEntry("type", "select")
                        .containsEntry("required", false)
                        .containsEntry("options", List.of("body", "attachment")));
    }

    @Test
    @DisplayName("Google Drive sink catalog는 파일명 템플릿과 파일 형식 계약을 제공한다")
    void googleDriveSinkCatalog_loadsFilenameTemplateContract() {
        SinkService googleDrive = catalogService.findSinkService("google_drive");

        assertThat(googleDrive.getAcceptedInputTypes())
                .containsExactly("TEXT", "SINGLE_FILE", "FILE_LIST", "SPREADSHEET_DATA");
        assertThat(catalogService.getSinkRequiredFields("google_drive"))
                .containsExactly("folder_id");

        Map<String, Object> schema = catalogService.getSinkSchema("google_drive", "TEXT");
        List<Map<String, Object>> fields = fieldsOf(schema);

        assertThat(fields)
                .anySatisfy(field -> assertThat(field)
                        .containsEntry("key", "drive_action")
                        .containsEntry("type", "select")
                        .containsEntry("required", false)
                        .containsEntry("options", List.of("copy")))
                .anySatisfy(field -> assertThat(field)
                        .containsEntry("key", "filename_template")
                        .containsEntry("type", "text")
                        .containsEntry("required", false))
                .anySatisfy(field -> assertThat(field)
                        .containsEntry("key", "file_format")
                        .containsEntry("type", "select")
                        .containsEntry("required", false)
                        .containsEntry("options", List.of("pdf", "docx", "txt", "original")));
    }

    @Test
    @DisplayName("announcement payload is accepted by text delivery sinks only")
    void announcementPayloadAcceptedByTextDeliverySinks() {
        assertThat(catalogService.findSinkService("discord").getAcceptedInputTypes())
                .contains("SINGLE_ANNOUNCEMENT");
        assertThat(catalogService.findSinkService("gmail").getAcceptedInputTypes())
                .contains("SINGLE_ANNOUNCEMENT");
        assertThat(catalogService.findSinkService("notion").getAcceptedInputTypes())
                .contains("SINGLE_ANNOUNCEMENT");
        assertThat(catalogService.findSinkService("google_drive").getAcceptedInputTypes())
                .doesNotContain("SINGLE_ANNOUNCEMENT");
        assertThat(catalogService.findSinkService("google_sheets").getAcceptedInputTypes())
                .doesNotContain("SINGLE_ANNOUNCEMENT");
        assertThat(catalogService.findSinkService("google_calendar").getAcceptedInputTypes())
                .doesNotContain("SINGLE_ANNOUNCEMENT");
    }

    @Test
    @DisplayName("email payload is accepted by text delivery sinks only")
    void emailPayloadAcceptedByTextDeliverySinks() {
        assertThat(catalogService.findSinkService("discord").getAcceptedInputTypes())
                .contains("SINGLE_EMAIL");
        assertThat(catalogService.findSinkService("gmail").getAcceptedInputTypes())
                .contains("SINGLE_EMAIL");
        assertThat(catalogService.findSinkService("notion").getAcceptedInputTypes())
                .contains("SINGLE_EMAIL");
        assertThat(catalogService.findSinkService("google_drive").getAcceptedInputTypes())
                .doesNotContain("SINGLE_EMAIL");
        assertThat(catalogService.findSinkService("google_sheets").getAcceptedInputTypes())
                .doesNotContain("SINGLE_EMAIL");
        assertThat(catalogService.findSinkService("google_calendar").getAcceptedInputTypes())
                .doesNotContain("SINGLE_EMAIL");
    }

    @Test
    @DisplayName("Google Drive 새 파일 source catalog는 목록 타입을 제공한다")
    void googleDriveSourceCatalog_loadsNewFileListContract() {
        SourceService googleDrive = catalogService.findSourceService("google_drive");

        SourceMode newFile = googleDrive.getSourceModes().stream()
                .filter(mode -> "new_file".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();
        SourceMode folderNewFile = googleDrive.getSourceModes().stream()
                .filter(mode -> "folder_new_file".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(newFile.getCanonicalInputType()).isEqualTo("FILE_LIST");
        assertThat(newFile.getTriggerKind()).isEqualTo("event");
        assertThat(folderNewFile.getCanonicalInputType()).isEqualTo("FILE_LIST");
        assertThat(folderNewFile.getTriggerKind()).isEqualTo("event");
    }

    @Test
    @DisplayName("Gmail sender_email source catalog는 발송인 이메일 target 계약을 제공한다")
    void gmailSourceCatalog_loadsSenderEmailContract() {
        SourceService gmail = catalogService.findSourceService("gmail");

        SourceMode newEmail = gmail.getSourceModes().stream()
                .filter(mode -> "new_email".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();
        SourceMode senderEmail = gmail.getSourceModes().stream()
                .filter(mode -> "sender_email".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(newEmail.getCanonicalInputType()).isEqualTo("EMAIL_LIST");
        assertThat(newEmail.getTriggerKind()).isEqualTo("event");
        assertThat(senderEmail.getCanonicalInputType()).isEqualTo("SINGLE_EMAIL");
        assertThat(senderEmail.getTriggerKind()).isEqualTo("event");
        assertThat(senderEmail.getTargetSchema())
                .containsEntry("type", "text_input")
                .containsEntry("label", "보낸 사람 이메일")
                .containsEntry("placeholder", "sender@example.com")
                .containsEntry("required", true)
                .containsEntry("validation", "email")
                .doesNotContainKey("keyword_supported");
        assertThat(senderEmail.getTargetSchema().get("helper_text"))
                .asString()
                .contains("Gmail 검색 문법 없이");
        assertThat(catalogService.isSourceTargetRequired("gmail", "sender_email"))
                .isTrue();
        assertThat(gmail.getSourceModes())
                .allSatisfy(mode -> assertThat(mode.getTargetSchema())
                        .doesNotContainKey("keyword_supported"));
    }

    @Test
    @DisplayName("인터넷 글 소스 catalog에 웹사이트 feed mode를 로딩한다")
    void webNewsSourceCatalog_loadsWebsiteFeedMode() {
        SourceService webNews = catalogService.findSourceService("web_news");

        SourceMode websiteFeed = webNews.getSourceModes().stream()
                .filter(mode -> "website_feed".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(webNews.getLabel()).isEqualTo("인터넷");
        assertThat(websiteFeed.getCanonicalInputType()).isEqualTo("ARTICLE_LIST");
        assertThat(websiteFeed.getLabel()).isEqualTo("여러 출처에서 새 글 가져오기");
        assertThat(websiteFeed.getTriggerKind()).isEqualTo("event");
        assertThat(websiteFeed.getTargetSchema())
                .containsEntry("type", "feed_source_picker")
                .containsEntry("label", "뉴스/글 출처")
                .containsEntry("multiple", true)
                .containsEntry("picker_supported", true)
                .containsEntry("allow_custom", true)
                .containsEntry("max_items", 10)
                .containsEntry("keyword_supported", true)
                .containsEntry("keyword_label", "관심 키워드")
                .containsEntry("validation", "url");
        assertThat(websiteFeed.getTargetSchema().get("helper_text"))
                .asString()
                .contains("출처")
                .contains("직접 입력");
        assertThat(catalogService.isSourceTargetRequired("web_news", "website_feed"))
                .isTrue();
    }

    @Test
    @DisplayName("인터넷 글 catalog는 신규 SE Board 공지 mode를 로딩한다")
    void webNewsSourceCatalog_loadsSeBoardNewPostsMode() {
        SourceService webNews = catalogService.findSourceService("web_news");

        SourceMode newPosts = webNews.getSourceModes().stream()
                .filter(mode -> "seboard_new_posts".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(newPosts.getLabel()).isEqualTo("SE Board 새 글 가져오기");
        assertThat(newPosts.getCanonicalInputType()).isEqualTo("ARTICLE_LIST");
        assertThat(newPosts.getTriggerKind()).isEqualTo("event");
        assertThat(newPosts.getTargetSchema())
                .containsEntry("type", "category_picker")
                .containsEntry("picker_supported", true)
                .containsEntry("keyword_supported", true)
                .containsEntry("keyword_label", "포함할 단어");
        assertThat(newPosts.getTargetSchema().get("helper_text"))
                .asString()
                .contains("새 글")
                .contains("포함할 단어");
        assertThat(catalogService.isSourceTargetRequired("web_news", "seboard_new_posts"))
                .isTrue();
    }

    @Test
    @DisplayName("네이버 뉴스 catalog는 기존 API 응답 계약을 유지한다")
    void naverNewsSourceCatalog_keepsKeywordSearchApiResponseContract() {
        SourceService naverNews = catalogService.findSourceService("naver_news");

        SourceMode keywordSearch = naverNews.getSourceModes().stream()
                .filter(mode -> "keyword_search".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(keywordSearch.getCanonicalInputType()).isEqualTo("API_RESPONSE");
        assertThat(keywordSearch.getTargetSchema())
                .containsEntry("type", "text_input")
                .containsEntry("placeholder", "검색 키워드");
        assertThat(catalogService.isSourceTargetRequired("naver_news", "keyword_search"))
                .isTrue();
    }

    @Test
    @DisplayName("네이버 뉴스 catalog에 기사 목록 검색 mode를 로딩한다")
    void naverNewsSourceCatalog_loadsArticleSearchMode() {
        SourceService naverNews = catalogService.findSourceService("naver_news");

        SourceMode articleSearch = naverNews.getSourceModes().stream()
                .filter(mode -> "article_search".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(articleSearch.getLabel()).isEqualTo("네이버 뉴스 검색");
        assertThat(articleSearch.getCanonicalInputType()).isEqualTo("ARTICLE_LIST");
        assertThat(articleSearch.getTargetSchema())
                .containsEntry("type", "text_input")
                .containsEntry("label", "검색어");
        assertThat(articleSearch.getTargetSchema().get("helper_text"))
                .asString()
                .contains("최신 네이버 뉴스");
        assertThat(catalogService.isSourceTargetRequired("naver_news", "article_search"))
                .isTrue();
    }

    @Test
    @DisplayName("네이버 뉴스 catalog는 신규 기사 mode를 로딩한다")
    void naverNewsSourceCatalog_loadsNewArticlesMode() {
        SourceService naverNews = catalogService.findSourceService("naver_news");

        SourceMode newArticles = naverNews.getSourceModes().stream()
                .filter(mode -> "new_articles".equals(mode.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(newArticles.getCanonicalInputType()).isEqualTo("ARTICLE_LIST");
        assertThat(newArticles.getTriggerKind()).isEqualTo("event");
        assertThat(newArticles.getTargetSchema())
                .containsEntry("type", "text_input")
                .containsEntry("label", "검색어");
        assertThat(catalogService.isSourceTargetRequired("naver_news", "new_articles"))
                .isTrue();
    }

    @Test
    @DisplayName("Google Calendar sink catalog는 applicable_when 문맥 규칙을 노출한다")
    void googleCalendarSinkCatalog_exposesApplicableWhenRule() {
        SinkService googleCalendar = catalogService.findSinkService("google_calendar");

        assertThat(googleCalendar.getAcceptedInputTypes())
                .containsExactly("TEXT", "SCHEDULE_DATA");
        assertThat(googleCalendar.getApplicableWhen())
                .containsEntry("service", List.of("google_calendar"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsOf(Map<String, Object> schema) {
        return (List<Map<String, Object>>) schema.get("fields");
    }
}
