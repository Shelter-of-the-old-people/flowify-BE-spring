package org.github.flowify.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.common.dto.PageResponse;
import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.user.entity.User;
import org.github.flowify.workflow.dto.NodeAddRequest;
import org.github.flowify.workflow.dto.NodeChoiceSelectRequest;
import org.github.flowify.workflow.dto.NodeUpdateRequest;
import org.github.flowify.workflow.dto.ShareRequest;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.dto.WorkflowGenerateRequest;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.dto.WorkflowUpdateRequest;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.choice.dto.ChoiceResponse;
import org.github.flowify.workflow.service.choice.dto.NodeSelectionResult;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "워크플로우", description = "워크플로우 CRUD 및 노드 관리")
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final FastApiClient fastApiClient;

    @Operation(summary = "워크플로우 생성", description = "새 워크플로우를 생성합니다.")
    @PostMapping
    public ApiResponse<WorkflowResponse> createWorkflow(Authentication authentication,
                                                        @Valid @RequestBody WorkflowCreateRequest request) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.createWorkflow(user.getId(), request));
    }

    @Operation(summary = "워크플로우 목록 조회", description = "내 워크플로우 및 공유된 워크플로우 목록을 페이지네이션으로 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<WorkflowResponse>> getWorkflows(Authentication authentication,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.getWorkflowsByUserId(user.getId(), page, size));
    }

    @Operation(summary = "워크플로우 상세 조회")
    @GetMapping("/{id}")
    public ApiResponse<WorkflowResponse> getWorkflow(Authentication authentication,
                                                     @PathVariable String id) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.getWorkflowById(user.getId(), id));
    }

    @Operation(summary = "워크플로우 수정", description = "워크플로우의 이름, 설명, 노드, 엣지를 수정합니다.")
    @PutMapping("/{id}")
    public ApiResponse<WorkflowResponse> updateWorkflow(Authentication authentication,
                                                        @PathVariable String id,
                                                        @RequestBody WorkflowUpdateRequest request) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.updateWorkflow(user.getId(), id, request));
    }

    @Operation(summary = "워크플로우 삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteWorkflow(Authentication authentication,
                                            @PathVariable String id) {
        User user = (User) authentication.getPrincipal();
        workflowService.deleteWorkflow(user.getId(), id);
        return ApiResponse.ok();
    }

    @Operation(summary = "워크플로우 공유", description = "워크플로우를 다른 사용자에게 공유합니다.")
    @PostMapping("/{id}/share")
    public ApiResponse<Void> shareWorkflow(Authentication authentication,
                                           @PathVariable String id,
                                           @Valid @RequestBody ShareRequest request) {
        User user = (User) authentication.getPrincipal();
        workflowService.shareWorkflow(user.getId(), id, request.getUserIds());
        return ApiResponse.ok();
    }

    @Operation(summary = "AI 워크플로우 생성", description = "자연어 프롬프트를 기반으로 AI가 워크플로우를 자동 생성합니다.")
    @SuppressWarnings("unchecked")
    @PostMapping("/generate")
    public ApiResponse<WorkflowResponse> generateWorkflow(Authentication authentication,
                                                          @Valid @RequestBody WorkflowGenerateRequest request) {
        User user = (User) authentication.getPrincipal();
        Map<String, Object> generated = fastApiClient.generateWorkflow(user.getId(), request.getPrompt());
        WorkflowCreateRequest createRequest = convertGeneratedToCreateRequest(generated);
        return ApiResponse.ok(workflowService.createWorkflow(user.getId(), createRequest));
    }

    // ── 노드 단위 API ──

    @Operation(summary = "노드 선택지 조회", description = "이전 노드의 출력 타입을 기반으로 다음 노드 선택지를 조회합니다.")
    @GetMapping("/{id}/choices/{prevNodeId}")
    public ApiResponse<ChoiceResponse> getNodeChoices(Authentication authentication,
                                                       @PathVariable String id,
                                                       @PathVariable String prevNodeId) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.getNodeChoices(user.getId(), id, prevNodeId, null));
    }

    @Operation(summary = "노드 선택지 확정", description = "사용자의 선택을 처리하고 노드 타입을 결정합니다.")
    @PostMapping("/{id}/choices/{prevNodeId}/select")
    public ApiResponse<NodeSelectionResult> selectNodeChoice(Authentication authentication,
                                                              @PathVariable String id,
                                                              @PathVariable String prevNodeId,
                                                              @Valid @RequestBody NodeChoiceSelectRequest request) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.selectNodeChoice(
                user.getId(), id, prevNodeId,
                request.getSelectedOptionId(), request.getContext()));
    }

    @Operation(summary = "노드 추가", description = "워크플로우에 새 노드를 추가합니다.")
    @PostMapping("/{id}/nodes")
    public ApiResponse<WorkflowResponse> addNode(Authentication authentication,
                                                  @PathVariable String id,
                                                  @Valid @RequestBody NodeAddRequest request) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.addMiddleNode(user.getId(), id, request));
    }

    @Operation(summary = "노드 수정", description = "기존 노드의 설정을 수정합니다.")
    @PutMapping("/{id}/nodes/{nodeId}")
    public ApiResponse<WorkflowResponse> updateNode(Authentication authentication,
                                                     @PathVariable String id,
                                                     @PathVariable String nodeId,
                                                     @RequestBody NodeUpdateRequest request) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.updateNode(user.getId(), id, nodeId, request));
    }

    @Operation(summary = "노드 삭제", description = "노드를 삭제하고 후속 노드도 캐스케이드 삭제합니다.")
    @DeleteMapping("/{id}/nodes/{nodeId}")
    public ApiResponse<WorkflowResponse> deleteNode(Authentication authentication,
                                                     @PathVariable String id,
                                                     @PathVariable String nodeId) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(workflowService.deleteNodeCascade(user.getId(), id, nodeId));
    }

    private WorkflowCreateRequest convertGeneratedToCreateRequest(Map<String, Object> generated) {
        // FastAPI가 반환한 JSON을 WorkflowCreateRequest로 변환
        // FastAPI 응답 형식에 맞게 매핑 (ObjectMapper 활용)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.convertValue(generated, WorkflowCreateRequest.class);
    }
}
