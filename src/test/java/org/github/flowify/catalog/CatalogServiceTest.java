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
        assertThat(discord.getAcceptedInputTypes()).containsExactly("TEXT");
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
        assertThat(websiteFeed.getTargetSchema())
                .containsEntry("type", "feed_source_picker")
                .containsEntry("label", "뉴스/글 출처")
                .containsEntry("multiple", true)
                .containsEntry("picker_supported", true)
                .containsEntry("allow_custom", true)
                .containsEntry("max_items", 10)
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
                .containsEntry("picker_supported", true);
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsOf(Map<String, Object> schema) {
        return (List<Map<String, Object>>) schema.get("fields");
    }
}
