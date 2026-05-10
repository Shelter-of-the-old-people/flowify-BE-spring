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
        assertThat(websiteFeed.getTargetSchema())
                .containsEntry("type", "text_input")
                .containsEntry("label", "사이트 주소")
                .containsEntry("validation", "url");
        assertThat(catalogService.isSourceTargetRequired("web_news", "website_feed"))
                .isTrue();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsOf(Map<String, Object> schema) {
        return (List<Map<String, Object>>) schema.get("fields");
    }
}
