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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import reactor.core.publisher.Mono;

class GmailTargetOptionProviderTest {

    @Test
    void getOptions_labelEmailsReturnsLabelOptions() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/labels");
                    assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                            .isEqualTo("Bearer gmail-token");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("""
                                    {
                                      "labels": [
                                        {
                                          "id": "Label_123",
                                          "name": "Newsletters",
                                          "type": "user",
                                          "messagesTotal": 42,
                                          "messagesUnread": 3
                                        },
                                        {
                                          "id": "INBOX",
                                          "name": "INBOX",
                                          "type": "system"
                                        }
                                      ]
                                    }
                                    """)
                            .build());
                })
                .build();
        GmailTargetOptionProvider provider = new GmailTargetOptionProvider(webClient);

        TargetOptionResponse response = provider.getOptions(
                "label_emails", "gmail-token", null, "news", null);

        assertThat(response.getNextCursor()).isNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo("Label_123");
        assertThat(response.getItems().get(0).getLabel()).isEqualTo("Newsletters");
        assertThat(response.getItems().get(0).getType()).isEqualTo("label");
        assertThat(response.getItems().get(0).getMetadata())
                .containsEntry("messageCount", 42)
                .containsEntry("unreadCount", 3);
    }

    @Test
    void getOptions_rateLimitedMapsToExternalRateLimited() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                {
                                  "error": {
                                    "code": 429,
                                    "message": "Quota exceeded"
                                  }
                                }
                                """)
                        .build()))
                .build();
        GmailTargetOptionProvider provider = new GmailTargetOptionProvider(webClient);

        assertThatThrownBy(() -> provider.getOptions("label_emails", "gmail-token", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_RATE_LIMITED);
    }
}
