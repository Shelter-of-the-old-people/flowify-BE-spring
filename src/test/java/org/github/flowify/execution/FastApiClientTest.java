package org.github.flowify.execution;

import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.service.FastApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FastApiClientTest {

    @Test
    void execute_mapsOauthScopeInsufficient() {
        FastApiClient client = clientReturning(HttpStatus.FORBIDDEN, """
                {
                  "error_code": "OAUTH_SCOPE_INSUFFICIENT",
                  "message": "Gmail read scope is missing"
                }
                """);

        assertThatThrownBy(() -> client.execute("wf1", "user1", Map.of(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException e = (BusinessException) error;
                    org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.OAUTH_SCOPE_INSUFFICIENT);
                    org.assertj.core.api.Assertions.assertThat(e.getMessage())
                            .isEqualTo("Gmail read scope is missing");
                });
    }

    @Test
    void execute_mapsTokenMissingToOauthNotConnected() {
        FastApiClient client = clientReturning(HttpStatus.UNAUTHORIZED, """
                {
                  "error_code": "OAUTH_TOKEN_MISSING",
                  "message": "Gmail token is missing"
                }
                """);

        assertThatThrownBy(() -> client.execute("wf1", "user1", Map.of(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_NOT_CONNECTED);
    }

    @Test
    void previewNode_mapsUnsupportedRuntimeSourceToPreflightValidationFailed() {
        FastApiClient client = clientReturning(HttpStatus.BAD_REQUEST, """
                {
                  "error_code": "UNSUPPORTED_RUNTIME_SOURCE",
                  "message": "Gmail mode is unsupported"
                }
                """);

        assertThatThrownBy(() -> client.previewNode(
                "wf1", "user1", "node1", Map.of(), Map.of(), 5, false))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.PREFLIGHT_VALIDATION_FAILED);
    }

    @Test
    void execute_mapsExternalRateLimited() {
        FastApiClient client = clientReturning(HttpStatus.TOO_MANY_REQUESTS, """
                {
                  "error_code": "EXTERNAL_RATE_LIMITED",
                  "message": "Gmail rate limit exceeded"
                }
                """);

        assertThatThrownBy(() -> client.execute("wf1", "user1", Map.of(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_RATE_LIMITED);
    }

    @Test
    void execute_fallsBackWhenErrorBodyCannotBeParsed() {
        FastApiClient client = clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "not-json");

        assertThatThrownBy(() -> client.execute("wf1", "user1", Map.of(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.FASTAPI_UNAVAILABLE);
    }

    private FastApiClient clientReturning(HttpStatus status, String body) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()))
                .build();
        return new FastApiClient(webClient);
    }
}
