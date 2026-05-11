package org.github.flowify.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.github.flowify.common.dto.ApiResponse;
import org.github.flowify.dashboard.dto.DashboardSummaryResponse;
import org.github.flowify.dashboard.service.DashboardService;
import org.github.flowify.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드", description = "대시보드 요약 데이터 조회")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "대시보드 요약 조회", description = "현재 사용자의 대시보드 metric, issue, 서비스 연결 요약을 조회합니다.")
    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> getSummary(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ApiResponse.ok(dashboardService.getSummary(user.getId()));
    }
}
