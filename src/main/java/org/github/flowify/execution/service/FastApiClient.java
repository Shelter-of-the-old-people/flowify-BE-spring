package org.github.flowify.execution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastApiClient {

    private final WebClient fastapiWebClient;

    @SuppressWarnings("unchecked")
    public String execute(String workflowId, String userId,
                          Object workflowDefinition, Map<String, String> serviceTokens) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "workflow", workflowDefinition,
                    "service_tokens", serviceTokens
            );

            Map<String, Object> response = fastapiWebClient.post()
                    .uri("/api/v1/workflows/{id}/execute", workflowId)
                    .header("X-User-ID", userId)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("execution_id")) {
                return (String) response.get("execution_id");
            }
            throw new BusinessException(ErrorCode.EXECUTION_FAILED, "FastAPI 실행 응답이 유효하지 않습니다.");
        } catch (WebClientResponseException e) {
            log.error("FastAPI 실행 요청 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE, "AI 서비스 요청에 실패했습니다.");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("FastAPI 통신 오류: ", e);
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateWorkflow(String userId, String prompt) {
        try {
            Map<String, Object> requestBody = Map.of("prompt", prompt);

            return fastapiWebClient.post()
                    .uri("/api/v1/workflows/generate")
                    .header("X-User-ID", userId)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI 워크플로우 생성 요청 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE, "AI 서비스 요청에 실패했습니다.");
        } catch (Exception e) {
            log.error("FastAPI 통신 오류: ", e);
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE);
        }
    }

    public void stopExecution(String executionId, String userId) {
        try {
            fastapiWebClient.post()
                    .uri("/api/v1/executions/{id}/stop", executionId)
                    .header("X-User-ID", userId)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI 중지 요청 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EXECUTION_FAILED, "워크플로우 중지 요청에 실패했습니다.");
        } catch (Exception e) {
            log.error("FastAPI 통신 오류: ", e);
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    public void rollback(String executionId, String nodeId, String userId) {
        try {
            WebClient.RequestBodySpec spec = fastapiWebClient.post()
                    .uri("/api/v1/executions/{id}/rollback", executionId)
                    .header("X-User-ID", userId);

            WebClient.RequestHeadersSpec<?> requestSpec = (nodeId != null)
                    ? spec.bodyValue(Map.of("node_id", nodeId))
                    : spec;

            requestSpec.retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI 롤백 요청 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EXECUTION_FAILED, "롤백 요청에 실패했습니다.");
        } catch (Exception e) {
            log.error("FastAPI 통신 오류: ", e);
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE);
        }
    }
}
