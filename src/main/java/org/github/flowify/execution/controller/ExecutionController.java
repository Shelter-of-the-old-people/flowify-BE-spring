package org.github.flowify.execution.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.execution.dto.ExecutionDetailResponse;
import org.github.flowify.execution.dto.ExecutionSummaryResponse;
import org.github.flowify.execution.dto.NodeDataResponse;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.service.ExecutionService;
import org.github.flowify.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "실행", description = "워크플로우 실행 및 이력 관리")
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @Operation(summary = "워크플로우 실행", description = "워크플로우를 FastAPI에 위임하여 실행합니다.")
    @PostMapping("/{id}/execute")
    public ApiResponse<String> executeWorkflow(Authentication authentication,
                                               @PathVariable String id) {
        User user = (User) authentication.getPrincipal();
        String executionId = executionService.executeWorkflow(user.getId(), id);
        return ApiResponse.ok(executionId);
    }

    @Operation(summary = "실행 이력 목록 조회", description = "워크플로우 소유자만 조회 가능. nodeLogs 제외한 summary 반환.")
    @GetMapping("/{id}/executions")
    public ApiResponse<List<ExecutionSummaryResponse>> getExecutions(Authentication authentication,
                                                                      @PathVariable String id) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(executionService.getExecutionsByWorkflowId(user.getId(), id));
    }

    @Operation(summary = "최신 실행 조회", description = "가장 최근 실행의 summary를 반환합니다. 실행 이력 없으면 data: null.")
    @GetMapping("/{id}/executions/latest")
    public ApiResponse<ExecutionSummaryResponse> getLatestExecution(Authentication authentication,
                                                                     @PathVariable String id) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(executionService.getLatestExecution(user.getId(), id));
    }

    @Operation(summary = "실행 상세 조회", description = "특정 실행의 노드별 로그를 포함한 상세 정보를 조회합니다. 워크플로우 소유자만 조회 가능.")
    @GetMapping("/{id}/executions/{execId}")
    public ApiResponse<ExecutionDetailResponse> getExecutionDetail(Authentication authentication,
                                                                     @PathVariable String id,
                                                                     @PathVariable String execId) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(executionService.getExecutionDetail(user.getId(), id, execId));
    }

    @Operation(summary = "최신 실행 노드 데이터 조회", description = "최신 실행 기준으로 특정 노드의 입출력 데이터를 조회합니다.")
    @GetMapping("/{id}/executions/latest/nodes/{nodeId}/data")
    public ApiResponse<NodeDataResponse> getLatestNodeData(Authentication authentication,
                                                            @PathVariable String id,
                                                            @PathVariable String nodeId) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(executionService.getLatestNodeData(user.getId(), id, nodeId));
    }

    @Operation(summary = "특정 실행 노드 데이터 조회", description = "특정 실행의 특정 노드 입출력 데이터를 조회합니다.")
    @GetMapping("/{id}/executions/{execId}/nodes/{nodeId}/data")
    public ApiResponse<NodeDataResponse> getNodeData(Authentication authentication,
                                                      @PathVariable String id,
                                                      @PathVariable String execId,
                                                      @PathVariable String nodeId) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(executionService.getNodeData(user.getId(), id, execId, nodeId));
    }

    @Operation(summary = "실행 중지", description = "실행 중인 워크플로우를 중지합니다.")
    @PostMapping("/{id}/executions/{execId}/stop")
    public ApiResponse<Void> stopExecution(Authentication authentication,
                                           @PathVariable String id,
                                           @PathVariable String execId) {
        User user = (User) authentication.getPrincipal();
        executionService.stopExecution(user.getId(), execId);
        return ApiResponse.ok();
    }

    @Operation(summary = "실행 롤백", description = "실패한 실행을 마지막 성공 스냅샷으로 롤백합니다.")
    @PostMapping("/{id}/executions/{execId}/rollback")
    public ApiResponse<Void> rollbackExecution(Authentication authentication,
                                               @PathVariable String id,
                                               @PathVariable String execId,
                                               @RequestParam(required = false) String nodeId) {
        User user = (User) authentication.getPrincipal();
        executionService.rollbackExecution(user.getId(), execId, nodeId);
        return ApiResponse.ok();
    }
}
