package org.github.flowify.catalog.service.picker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebNewsTargetOptionProviderTest {

    private final WebFeedSourceRegistry webFeedSourceRegistry =
            new WebFeedSourceRegistry(new ObjectMapper());

    @Test
    void getOptions_seBoardPostsReturnsCategoryOptions() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/menu");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("""
                                    [
                                      {
                                        "menuId": 1,
                                        "name": "Notice",
                                        "type": "BOARD",
                                        "subMenu": [
                                          {
                                            "menuId": 2,
                                            "name": "General",
                                            "urlId": "general",
                                            "type": "CATEGORY",
                                            "accessible": true,
                                            "subMenu": []
                                          },
                                          {
                                            "menuId": 3,
                                            "name": "Hidden",
                                            "urlId": "hidden",
                                            "type": "CATEGORY",
                                            "accessible": false,
                                            "subMenu": []
                                          }
                                        ]
                                      },
                                      {
                                        "menuId": 10,
                                        "name": "Free Board",
                                        "type": "BOARD",
                                        "subMenu": [
                                          {
                                            "menuId": 11,
                                            "name": "Information",
                                            "urlId": "information",
                                            "type": "CATEGORY",
                                            "accessible": true,
                                            "subMenu": []
                                          }
                                        ]
                                      }
                                    ]
                                    """)
                            .build());
                })
                .build();
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(
                webClient,
                webFeedSourceRegistry
        );

        TargetOptionResponse response = provider.getOptions("seboard_posts", null, null, "info", null);

        assertThat(response.getNextCursor()).isNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo("11");
        assertThat(response.getItems().get(0).getLabel()).isEqualTo("Free Board > Information");
        assertThat(response.getItems().get(0).getDescription()).isEqualTo("Free Board");
        assertThat(response.getItems().get(0).getType()).isEqualTo("category");
        assertThat(response.getItems().get(0).getMetadata())
                .containsEntry("provider", "seboard")
                .containsEntry("boardId", "10")
                .containsEntry("boardName", "Free Board")
                .containsEntry("categoryId", "11")
                .containsEntry("categoryName", "Information")
                .containsEntry("displayPath", "Free Board > Information")
                .containsEntry("urlId", "information");
    }

    @Test
    void getOptions_seBoardNewPostsReturnsCategoryOptions() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                [
                                  {
                                    "menuId": 1,
                                    "name": "Notice",
                                    "type": "BOARD",
                                    "subMenu": [
                                      {
                                        "menuId": 2,
                                        "name": "General",
                                        "urlId": "general",
                                        "type": "CATEGORY",
                                        "accessible": true,
                                        "subMenu": []
                                      }
                                    ]
                                  }
                                ]
                                """)
                        .build()))
                .build();
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(
                webClient,
                webFeedSourceRegistry
        );

        TargetOptionResponse response = provider.getOptions(
                "seboard_new_posts", null, null, null, null);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo("2");
        assertThat(response.getItems().get(0).getType()).isEqualTo("category");
    }

    @Test
    void getOptions_unsupportedSourceModeThrowsInvalidRequest() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new AssertionError("request should not be sent")))
                .build();
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(
                webClient,
                webFeedSourceRegistry
        );

        assertThatThrownBy(() -> provider.getOptions("rss_feed", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void getOptions_rateLimitedMapsToExternalRateLimited() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"message\":\"Too many requests\"}")
                        .build()))
                .build();
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(
                webClient,
                webFeedSourceRegistry
        );

        assertThatThrownBy(() -> provider.getOptions("seboard_posts", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_RATE_LIMITED);
    }

    @Test
    void getOptions_websiteFeedReturnsFeedSourceOptions() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new AssertionError("request should not be sent")))
                .build();
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(
                webClient,
                webFeedSourceRegistry
        );

        TargetOptionResponse response = provider.getOptions("website_feed", null, null, null, null);

        assertThat(response.getItems()).isNotEmpty();
        assertThat(response.getItems())
                .anySatisfy(item -> {
                    assertThat(item.getId()).isEqualTo("https://feeds.bbci.co.uk/news/rss.xml");
                    assertThat(item.getLabel()).isEqualTo("BBC News");
                    assertThat(item.getType()).isEqualTo("feed_source");
                    assertThat(item.getMetadata())
                            .containsEntry("presetId", "bbc_news")
                            .containsEntry("category", "뉴스")
                            .containsEntry("language", "en")
                            .containsEntry("sourceType", "언론사");
                });
    }

    @Test
    void getOptions_websiteFeedFiltersByTags() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new AssertionError("request should not be sent")))
                .build();
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(
                webClient,
                webFeedSourceRegistry
        );

        TargetOptionResponse response = provider.getOptions("website_feed", null, null, "요리", null);

        assertThat(response.getItems()).extracting("label").contains("BBC Good Food");
    }
}
