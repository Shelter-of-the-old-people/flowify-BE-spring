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

        assertThat(websiteFeed.getCanonicalInputType()).isEqualTo("ARTICLE_LIST");
        assertThat(websiteFeed.getLabel()).isEqualTo("RSS 지원 사이트");
        assertThat(websiteFeed.getTargetSchema())
                .containsEntry("type", "text_input")
                .containsEntry("label", "사이트 주소")
                .containsEntry("validation", "url");
        assertThat(websiteFeed.getTargetSchema().get("helper_text"))
                .asString()
                .contains("RSS")
                .contains("네이버 뉴스 검색");
        assertThat(catalogService.isSourceTargetRequired("web_news", "website_feed"))
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsOf(Map<String, Object> schema) {
        return (List<Map<String, Object>>) schema.get("fields");
    }
}
