package org.github.flowify.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.catalog.dto.SinkCatalog;
import org.github.flowify.catalog.dto.SourceCatalog;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.picker.TargetOptionService;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.user.entity.User;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "에디터 카탈로그", description = "Source/Sink 서비스 카탈로그 조회")
@RestController
@RequestMapping("/api/editor-catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final ChoiceMappingService choiceMappingService;
    private final TargetOptionService targetOptionService;

    @Operation(summary = "Source 서비스 카탈로그 조회",
            description = "사용 가능한 source 서비스와 source mode 목록을 반환합니다.")
    @GetMapping("/sources")
    public ApiResponse<SourceCatalog> getSourceCatalog() {
        return ApiResponse.ok(catalogService.getSourceCatalog());
    }

    @Operation(summary = "Sink 서비스 카탈로그 조회",
            description = "사용 가능한 sink 서비스와 수용 가능한 input type 목록을 반환합니다.")
    @GetMapping("/sinks")
    public ApiResponse<SinkCatalog> getSinkCatalog() {
        return ApiResponse.ok(catalogService.getSinkCatalog());
    }

    @Operation(summary = "Sink 상세 설정 스키마 조회",
            description = "특정 sink 서비스의 input type별 설정 스키마를 반환합니다.")
    @GetMapping("/sinks/{serviceKey}/schema")
    public ApiResponse<Map<String, Object>> getSinkSchema(
            @PathVariable String serviceKey,
            @RequestParam String inputType) {
        return ApiResponse.ok(catalogService.getSinkSchema(serviceKey, inputType));
    }

    @Operation(summary = "Source target 선택지 조회",
            description = "source mode에 맞는 외부 서비스 target option 목록을 반환합니다.")
    @GetMapping("/sources/{serviceKey}/target-options")
    public ApiResponse<TargetOptionResponse> getTargetOptions(
            Authentication authentication,
            @PathVariable String serviceKey,
            @RequestParam("mode") String sourceMode,
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String cursor) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(targetOptionService.getOptions(
                user.getId(), serviceKey, sourceMode, parentId, query, cursor));
    }

    @Operation(summary = "Mapping Rules 조회",
            description = "데이터 타입별 처리 규칙과 노드 타입 정의를 반환합니다.")
    @GetMapping("/mapping-rules")
    public ApiResponse<MappingRules> getMappingRules() {
        return ApiResponse.ok(choiceMappingService.getMappingRules());
    }
}
