package org.github.flowify.execution;

import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.service.FastApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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
    void previewNode_mapsDocumentContentUnsupported() {
        FastApiClient client = clientReturning(HttpStatus.UNPROCESSABLE_ENTITY, """
                {
                  "error_code": "DOCUMENT_CONTENT_UNSUPPORTED",
                  "message": "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다."
                }
                """);

        assertThatThrownBy(() -> client.previewNode(
                "wf1", "user1", "node1", Map.of(), Map.of(), 5, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException e = (BusinessException) error;
                    org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.DOCUMENT_CONTENT_UNSUPPORTED);
                    org.assertj.core.api.Assertions.assertThat(e.getMessage())
                            .isEqualTo("이 파일 형식은 아직 본문 읽기를 지원하지 않습니다.");
                });
    }

    @Test
    void execute_mapsDocumentContentTooLarge() {
        FastApiClient client = clientReturning(HttpStatus.PAYLOAD_TOO_LARGE, """
                {
                  "error_code": "DOCUMENT_CONTENT_TOO_LARGE",
                  "message": "파일이 너무 커서 본문을 읽을 수 없습니다."
                }
                """);

        assertThatThrownBy(() -> client.execute("wf1", "user1", Map.of(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_CONTENT_TOO_LARGE);
    }

    @Test
    void execute_preservesDocumentContentNotRequestedCode() {
        FastApiClient client = clientReturning(HttpStatus.UNPROCESSABLE_ENTITY, """
                {
                  "error_code": "DOCUMENT_CONTENT_NOT_REQUESTED",
                  "message": "본문이 필요한 작업이지만 본문 추출이 수행되지 않았습니다."
                }
                """);

        assertThatThrownBy(() -> client.execute("wf1", "user1", Map.of(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException e = (BusinessException) error;
                    org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.DOCUMENT_CONTENT_NOT_REQUESTED);
                    org.assertj.core.api.Assertions.assertThat(e.getMessage())
                            .isEqualTo("본문이 필요한 작업이지만 본문 추출이 수행되지 않았습니다.");
                });
    }

    @Test
    void execute_doesNotSendOcrVisionProviderSettingsInRuntimePayload() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    MockClientHttpRequest mockRequest = new MockClientHttpRequest(
                            HttpMethod.POST,
                            URI.create("http://localhost" + request.url().getPath()));
                    request.body().insert(mockRequest, new BodyInserter.Context() {
                        @Override
                        public List<HttpMessageWriter<?>> messageWriters() {
                            return strategies.messageWriters();
                        }

                        @Override
                        public Optional<ServerHttpRequest> serverRequest() {
                            return Optional.empty();
                        }

                        @Override
                        public Map<String, Object> hints() {
                            return Map.of();
                        }
                    }).block();
                    requestBody.set(mockRequest.getBodyAsString().block());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"execution_id\":\"exec-1\"}")
                            .build());
                })
                .build();
        FastApiClient client = new FastApiClient(webClient);

        client.execute(
                "wf1",
                "user1",
                Map.of("id", "wf1"),
                Map.of("gmail", "gmail-token"),
                Map.of("user_profile", Map.of("user_id", "user1"))
        );

        assertThat(requestBody.get())
                .contains("\"workflow\"")
                .contains("\"service_tokens\"")
                .contains("\"runtime_context\"")
                .doesNotContain("OCR_PROVIDER")
                .doesNotContain("VISION_PROVIDER")
                .doesNotContain("LLM_API_KEY")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("api_key")
                .doesNotContain("provider_key");
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
