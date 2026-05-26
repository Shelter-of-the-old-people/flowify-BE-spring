package org.github.flowify.template.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.template.dto.CreateTemplateRequest;
import org.github.flowify.template.entity.Template;
import org.github.flowify.template.service.TemplateService;
import org.github.flowify.user.entity.User;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "템플릿", description = "워크플로우 템플릿 관리")
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @Operation(summary = "템플릿 목록 조회", description = "전체 또는 카테고리별 템플릿 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<Template>> getTemplates(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String folderKey) {
        return ApiResponse.ok(templateService.getTemplates(category, folderKey));
    }

    @Operation(summary = "템플릿 상세 조회")
    @GetMapping("/{id}")
    public ApiResponse<Template> getTemplateById(@PathVariable String id) {
        return ApiResponse.ok(templateService.getTemplateById(id));
    }

    @Operation(summary = "템플릿으로 워크플로우 생성", description = "선택한 템플릿을 기반으로 새 워크플로우를 생성합니다.")
    @PostMapping("/{id}/instantiate")
    public ApiResponse<WorkflowResponse> instantiateTemplate(Authentication authentication,
                                                              @PathVariable String id) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(templateService.instantiateTemplate(user.getId(), id));
    }

    @Operation(summary = "사용자 템플릿 생성", description = "내 워크플로우를 템플릿으로 저장합니다.")
    @PostMapping
    public ApiResponse<Template> createTemplate(Authentication authentication,
                                                @Valid @RequestBody CreateTemplateRequest request) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(templateService.createUserTemplate(user.getId(), request));
    }
}
