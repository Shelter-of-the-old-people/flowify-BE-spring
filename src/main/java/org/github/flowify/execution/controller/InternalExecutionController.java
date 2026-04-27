package org.github.flowify.execution.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.dto.ExecutionCompleteRequest;
import org.github.flowify.execution.service.ExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "내부 실행 콜백", description = "FastAPI가 실행 완료를 Spring에 알리는 내부 엔드포인트 (X-Internal-Token 필요)")
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalExecutionController {

    private final ExecutionService executionService;

    @Value("${app.fastapi.internal-token}")
    private String internalToken;

    @Operation(summary = "실행 완료 콜백", description = "FastAPI가 워크플로우 실행 완료 후 상태를 업데이트합니다.")
    @PostMapping("/executions/{execId}/complete")
    public ApiResponse<Void> completeExecution(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable String execId,
            @RequestBody ExecutionCompleteRequest request) {

        if (!internalToken.equals(token)) {
            log.warn("Internal token mismatch on callback for execId={}. expected length={}, received length={}",
                    execId, internalToken.length(), token.length());
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        executionService.completeExecution(execId, request.getStatus(), request.getError());
        return ApiResponse.ok();
    }
}
