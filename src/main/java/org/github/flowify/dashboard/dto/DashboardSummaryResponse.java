package org.github.flowify.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class DashboardSummaryResponse {

    private DashboardMetricsResponse metrics;
    private List<DashboardIssueResponse> issues;
    private List<DashboardServiceResponse> services;
}
