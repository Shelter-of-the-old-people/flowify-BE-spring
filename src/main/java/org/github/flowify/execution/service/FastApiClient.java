package org.github.flowify.execution.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.NodePreviewResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastApiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Qualifier("fastapiWebClient")
    private final WebClient fastapiWebClient;

    @SuppressWarnings("unchecked")
    public String execute(String workflowId, String userId,
                          Object workflowDefinition, Map<String, String> serviceTokens) {
        return execute(workflowId, userId, workflowDefinition, serviceTokens, Map.of());
    }

    @SuppressWarnings("unchecked")
    public String execute(String workflowId, String userId,
                          Object workflowDefinition, Map<String, String> serviceTokens,
                          Map<String, Object> runtimeContext) {
        try {
            Map<String, Object> requestBody = createWorkflowRequestBody(
                    workflowDefinition,
                    serviceTokens,
                    runtimeContext
            );

            Map<String, Object> response = fastapiWebClient.post()
                    .uri("/api/v1/workflows/{id}/execute", workflowId)
                    .header("X-User-ID", userId)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && response.containsKey("execution_id")) {
                return (String) response.get("execution_id");
            }
            throw new BusinessException(ErrorCode.EXECUTION_FAILED, "FastAPI 실행 응답이 유효하지 않습니다.");
        } catch (WebClientResponseException e) {
            log.error("FastAPI 실행 요청 실패: {}", e.getMessage());
            throw toBusinessException(e, "AI 서비스 요청에 실패했습니다.");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("FastAPI 통신 오류: ", e);
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    public NodePreviewResponse previewNode(String workflowId, String userId, String nodeId,
                                           Object workflowDefinition,
                                           Map<String, String> serviceTokens,
                                           int limit,
                                           boolean includeContent) {
        return previewNode(
                workflowId,
                userId,
                nodeId,
                workflowDefinition,
                serviceTokens,
                limit,
                includeContent,
                Map.of()
        );
    }

    @SuppressWarnings("unchecked")
    public NodePreviewResponse previewNode(String workflowId, String userId, String nodeId,
                                           Object workflowDefinition,
                                           Map<String, String> serviceTokens,
                                           int limit,
                                           boolean includeContent,
                                           Map<String, Object> runtimeContext) {
        try {
            Map<String, Object> requestBody = createWorkflowRequestBody(
                    workflowDefinition,
                    serviceTokens,
                    runtimeContext
            );
            requestBody.put("limit", limit);
            requestBody.put("include_content", includeContent);

            Map<String, Object> response = fastapiWebClient.post()
                    .uri("/api/v1/workflows/{workflowId}/nodes/{nodeId}/preview", workflowId, nodeId)
                    .header("X-User-ID", userId)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response == null) {
                throw new BusinessException(ErrorCode.EXECUTION_FAILED, "FastAPI preview response is empty.");
            }

            return toNodePreviewResponse(response);
        } catch (WebClientResponseException e) {
            log.error("FastAPI preview request failed: {}", e.getMessage());
            throw toBusinessException(e, "노드 미리보기 요청에 실패했습니다.");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("FastAPI preview communication error: ", e);
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateWorkflow(String userId, String prompt) {
        return generateWorkflow(userId, prompt, Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateWorkflow(String userId, String prompt, Map<String, Object> context) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("prompt", prompt);
            if (context != null && !context.isEmpty()) {
                requestBody.put("context", context);
            }

            return fastapiWebClient.post()
                    .uri("/api/v1/workflows/generate")
                    .header("X-User-ID", userId)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI 워크플로우 생성 요청 실패: {}", e.getMessage());
            throw toBusinessException(e, "AI 서비스 요청에 실패했습니다.");
        } catch (Exception e) {
            log.error("FastAPI 통신 오류: ", e);
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> refineWorkflow(String userId, String prompt,
                                              Map<String, Object> currentWorkflow,
                                              Map<String, Object> context) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("prompt", prompt);
            requestBody.put("current_workflow", currentWorkflow != null ? currentWorkflow : Map.of());
            if (context != null && !context.isEmpty()) {
                requestBody.put("context", context);
            }

            return fastapiWebClient.post()
                    .uri("/api/v1/workflows/refine")
                    .header("X-User-ID", userId)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI 워크플로우 수정 생성 요청 실패: {}", e.getMessage());
            throw toBusinessException(e, "AI 서비스 요청에 실패했습니다.");
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
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI 중지 요청 실패: {}", e.getMessage());
            throw toBusinessException(e, "워크플로우 중지 요청에 실패했습니다.");
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
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI 롤백 요청 실패: {}", e.getMessage());
            throw toBusinessException(e, "롤백 요청에 실패했습니다.");
        } catch (Exception e) {
            log.error("FastAPI 통신 오류: ", e);
            throw new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    private NodePreviewResponse toNodePreviewResponse(Map<String, Object> response) {
        return NodePreviewResponse.builder()
                .workflowId((String) response.get("workflow_id"))
                .nodeId((String) response.get("node_id"))
                .status((String) response.get("status"))
                .available(Boolean.TRUE.equals(response.get("available")))
                .reason((String) response.get("reason"))
                .inputData(response.get("input_data"))
                .outputData(response.get("output_data"))
                .previewData(response.get("preview_data"))
                .missingFields((List<String>) response.get("missing_fields"))
                .metadata((Map<String, Object>) response.get("metadata"))
                .build();
    }

    private BusinessException toBusinessException(WebClientResponseException e, String fallbackMessage) {
        Map<String, Object> body = parseErrorBody(e.getResponseBodyAsString());
        String fastApiErrorCode = firstString(body, "error_code", "errorCode", "code");
        String message = firstString(body, "message", "detail", "error");

        if (fastApiErrorCode == null || fastApiErrorCode.isBlank()) {
            return new BusinessException(ErrorCode.FASTAPI_UNAVAILABLE, fallbackMessage);
        }

        ErrorCode mapped = mapFastApiErrorCode(fastApiErrorCode);
        String resolvedMessage = message != null && !message.isBlank()
                ? message
                : mapped.getMessage();
        return new BusinessException(mapped, resolvedMessage);
    }

    private Map<String, Object> parseErrorBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("FastAPI error body parsing failed: {}", responseBody);
            return Map.of();
        }
    }

    private String firstString(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private ErrorCode mapFastApiErrorCode(String fastApiErrorCode) {
        return switch (fastApiErrorCode) {
            case "OAUTH_SCOPE_INSUFFICIENT" -> ErrorCode.OAUTH_SCOPE_INSUFFICIENT;
            case "OAUTH_TOKEN_MISSING" -> ErrorCode.OAUTH_NOT_CONNECTED;
            case "OAUTH_TOKEN_INVALID" -> ErrorCode.OAUTH_TOKEN_EXPIRED;
            case "EXTERNAL_RATE_LIMITED" -> ErrorCode.EXTERNAL_RATE_LIMITED;
            case "EXTERNAL_API_ERROR" -> ErrorCode.EXTERNAL_API_ERROR;
            case "LLM_API_ERROR" -> ErrorCode.LLM_API_ERROR;
            case "LLM_GENERATION_FAILED" -> ErrorCode.LLM_GENERATION_FAILED;
            case "DOCUMENT_CONTENT_UNSUPPORTED" -> ErrorCode.DOCUMENT_CONTENT_UNSUPPORTED;
            case "DOCUMENT_CONTENT_TOO_LARGE" -> ErrorCode.DOCUMENT_CONTENT_TOO_LARGE;
            case "DOCUMENT_CONTENT_EMPTY" -> ErrorCode.DOCUMENT_CONTENT_EMPTY;
            case "DOCUMENT_CONTENT_EXTRACTION_FAILED" -> ErrorCode.DOCUMENT_CONTENT_EXTRACTION_FAILED;
            case "DOCUMENT_CONTENT_NOT_REQUESTED" -> ErrorCode.DOCUMENT_CONTENT_NOT_REQUESTED;
            case "UNSUPPORTED_RUNTIME_SOURCE", "UNSUPPORTED_RUNTIME_SINK" -> ErrorCode.PREFLIGHT_VALIDATION_FAILED;
            case "UNAUTHORIZED" -> ErrorCode.FASTAPI_UNAVAILABLE;
            default -> ErrorCode.FASTAPI_UNAVAILABLE;
        };
    }

    private Map<String, Object> createWorkflowRequestBody(
            Object workflowDefinition,
            Map<String, String> serviceTokens,
            Map<String, Object> runtimeContext
    ) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("workflow", workflowDefinition);
        requestBody.put("service_tokens", serviceTokens);
        if (runtimeContext != null && !runtimeContext.isEmpty()) {
            requestBody.put("runtime_context", runtimeContext);
        }
        return requestBody;
    }
}
