package org.github.flowify.catalog.service.picker;

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
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(webClient);

        TargetOptionResponse response = provider.getOptions("seboard_posts", null, null, "info", null);

        assertThat(response.getNextCursor()).isNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo("11");
        assertThat(response.getItems().get(0).getLabel()).isEqualTo("Information");
        assertThat(response.getItems().get(0).getDescription()).isEqualTo("Free Board");
        assertThat(response.getItems().get(0).getType()).isEqualTo("category");
        assertThat(response.getItems().get(0).getMetadata())
                .containsEntry("provider", "seboard")
                .containsEntry("boardId", "10")
                .containsEntry("boardName", "Free Board")
                .containsEntry("urlId", "information");
    }

    @Test
    void getOptions_unsupportedSourceModeThrowsInvalidRequest() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new AssertionError("request should not be sent")))
                .build();
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(webClient);

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
        WebNewsTargetOptionProvider provider = new WebNewsTargetOptionProvider(webClient);

        assertThatThrownBy(() -> provider.getOptions("seboard_posts", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_RATE_LIMITED);
    }
}
